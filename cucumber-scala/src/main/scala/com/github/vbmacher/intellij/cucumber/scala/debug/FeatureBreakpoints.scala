package com.github.vbmacher.intellij.cucumber.scala.debug

import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, JavaBreakpointHandler, JavaBreakpointHandlerFactory}
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiClass, PsiElement, PsiManager}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.breakpoints.{XBreakpoint, XLineBreakpoint, XLineBreakpointType}
import com.sun.jdi.{ObjectReference, ReferenceType, Value}
import com.sun.jdi.event.LocatableEvent
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.cucumber.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr

import scala.jdk.CollectionConverters._
import scala.util.Try

object FeatureBreakpoints {
  val Category: Key[FeatureJavaBreakpoint] = Key.create("ScalaCucumberFeatureBreakpoint")
  final case class Target(source: PsiElement, uri: String, implementation: SourcePosition, stepLine: Int, exampleLine: Option[Int])
  def targets(file: GherkinFile, line: Int): Either[String, Seq[Target]] = {
    val document = com.intellij.psi.PsiDocumentManager.getInstance(file.getProject).getDocument(file)
    if (document == null || line >= document.getLineCount || line < 0) return Left("Invalid feature location")
    val leaf = file.findElementAt(math.min(document.getLineEndOffset(line), document.getLineStartOffset(line) + document.getText.substring(document.getLineStartOffset(line), document.getLineEndOffset(line)).takeWhile(_.isWhitespace).length))
    val step = Option(PsiTreeUtil.getParentOfType(leaf, classOf[GherkinStep], false))
    val row = Option(PsiTreeUtil.getParentOfType(leaf, classOf[GherkinTableRow], false)).filter { r =>
      PsiTreeUtil.getParentOfType(r, classOf[GherkinExamplesBlock]) != null && r.getParent.asInstanceOf[GherkinTable].getHeaderRow != r
    }
    val steps = step.toSeq ++ row.toSeq.flatMap(r => Option(PsiTreeUtil.getParentOfType(r, classOf[GherkinScenarioOutline]))).flatMap(_.getSteps)
    if (steps.isEmpty) return Left("Choose a Gherkin step or an Examples data row")
    val mappings = steps.map { source =>
      matchingSteps(source) match {
        case Seq(definition: ScStepDefinition) =>
          asCall(definition.getElement.getParent).flatMap(call => Option(PsiTreeUtil.findChildOfType(call.args, classOf[ScFunctionExpr])))
            .flatMap(_.result).map(body => Target(source, file.getVirtualFile.getUrl, SourcePosition.createFromElement(body), source.getContainingFile.getText.take(source.getTextOffset).count(_ == '\n') + 1, row.map(_ => line + 1)))
        case _ => None
      }
    }
    if (mappings.exists(_.isEmpty)) Left("All selected steps need one exact Scala implementation; resolve undefined/ambiguous steps first")
    else Right(mappings.flatten)
  }
  def matches(expectedUri: String, stepLine: Int, exampleLine: Option[Int], runtimeUri: String, runtimeStep: Int, runtimeExample: Int): Boolean =
    Try(RunResults.uri(expectedUri) == RunResults.uri(runtimeUri)).getOrElse(false) && stepLine == runtimeStep && exampleLine.forall(_ == runtimeExample)
}

class FeatureBreakpointType extends XLineBreakpointType[JavaLineBreakpointProperties]("scala-cucumber-step", "Scala Cucumber Step / Example") {
  override def createProperties(): JavaLineBreakpointProperties = new JavaLineBreakpointProperties
  override def createBreakpointProperties(file: VirtualFile, line: Int): JavaLineBreakpointProperties = new JavaLineBreakpointProperties
  override def canPutAt(file: VirtualFile, line: Int, project: Project): Boolean = file.getExtension == "feature" && inReadAction {
    Option(PsiManager.getInstance(project).findFile(file)).collect { case feature: GherkinFile => FeatureBreakpoints.targets(feature, line).isRight }.getOrElse(false)
  }
}

class FeatureBreakpointHandlerFactory extends JavaBreakpointHandlerFactory {
  override def createHandler(process: DebugProcessImpl): JavaBreakpointHandler = new JavaBreakpointHandler(classOf[FeatureBreakpointType], process) {
    override protected def createJavaBreakpoint(breakpoint: XBreakpoint[_ <: com.intellij.xdebugger.breakpoints.XBreakpointProperties[_]]): Breakpoint[_ <: org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties[_]] =
      new FeatureJavaBreakpoint(process.getProject, breakpoint.asInstanceOf[XLineBreakpoint[JavaLineBreakpointProperties]])
  }
}

/** Own JDI requests leave ordinary Scala breakpoints and their conditions untouched. Requests belong to this debug process. */
class FeatureJavaBreakpoint(project: Project, breakpoint: XLineBreakpoint[JavaLineBreakpointProperties])
  extends Breakpoint[JavaLineBreakpointProperties](project, breakpoint) {
  private var resolved = Seq.empty[FeatureBreakpoints.Target]
  private var problem = ""
  private val highlights = scala.collection.mutable.Map.empty[com.intellij.xdebugger.XDebugSession, FeatureDebugHighlight]
  override def reload(): Unit = inReadAction {
    val result = Option(breakpoint.getSourcePosition).flatMap(p => Option(PsiManager.getInstance(project).findFile(p.getFile)))
      .collect { case file: GherkinFile => FeatureBreakpoints.targets(file, breakpoint.getLine) }.getOrElse(Left("Feature source unavailable"))
    resolved = result.toOption.getOrElse(Seq.empty)
    problem = result.left.toOption.getOrElse("")
  }
  reload()
  override def getPsiClass: PsiClass = inReadAction {
    resolved.headOption.flatMap(t => Option(PsiTreeUtil.getParentOfType(t.implementation.getElementAt, classOf[PsiClass]))).orNull
  }
  override def getEvaluationElement: PsiElement = inReadAction { resolved.headOption.map(_.implementation.getElementAt).orNull }
  override def getDisplayName: String = "Scala Cucumber " + breakpoint.getFileUrl + ":" + (breakpoint.getLine + 1)
  override def getIcon: javax.swing.Icon = AllIcons.Debugger.Db_set_breakpoint
  override def isValid: Boolean = inReadAction { resolved.nonEmpty && resolved.forall(_.source.isValid) }
  override def getCategory: Key[FeatureJavaBreakpoint] = FeatureBreakpoints.Category
  override def getEventMessage(event: LocatableEvent): String = getDisplayName
  override def createRequest(process: DebugProcessImpl): Unit = {
    reload()
    if (!isValid) process.getRequestsManager.setInvalid(this, problem)
    else if (shouldCreateRequest(process)) resolved.map(_.implementation).distinct.foreach(position => createOrWaitPrepare(process, position))
  }
  override def processClassPrepare(process: DebugProcess, referenceType: ReferenceType): Unit = {
    val debug = process.asInstanceOf[DebugProcessImpl]
    // Class-prepare requests already exist here; they must not suppress executable line requests.
    if (shouldCreateRequest(debug, true)) resolved.flatMap(target => debug.getPositionManager.locationsOfLine(referenceType, target.implementation).asScala).distinct.foreach { location =>
      val request = debug.getRequestsManager.createBreakpointRequest(this, location)
      debug.getRequestsManager.enableRequest(request)
    }
  }
  override def evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean = {
    def invoke(value: ObjectReference, method: String): Option[Value] = value.referenceType().methodsByName(method).asScala.find(_.argumentTypeNames().isEmpty).map { m =>
      context.getDebugProcess.invokeMethod(context, value, m, java.util.Collections.emptyList[Value]())
    }
    val hit = CucumberDebugContext.read(event.thread(), invoke).flatMap { observed =>
      resolved.find(target => FeatureBreakpoints.matches(target.uri, target.stepLine, target.exampleLine, observed.uri, observed.step, observed.example))
    }
    val accepted = hit.nonEmpty && super.evaluateCondition(context, event)
    if (accepted) hit.foreach { target =>
      Option(context.getDebugProcess.getXdebugProcess).foreach { process =>
        val session = process.getSession
        val highlight = highlights.synchronized {
          highlights.getOrElseUpdate(session, {
            session.addSessionListener(new com.intellij.xdebugger.XDebugSessionListener {
              override def sessionStopped(): Unit = highlights.synchronized { highlights.remove(session); () }
            })
            new FeatureDebugHighlight(project, session)
          })
        }
        highlight.show(target.uri, target.stepLine, target.exampleLine)
      }
    }
    accepted
  }
}
