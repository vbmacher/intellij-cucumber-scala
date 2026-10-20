package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.util.GherkinFileAction
import com.intellij.codeInspection.{InspectionManager, ProblemHighlightType}
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.inspections.{CucumberCreateStepFixBase, CucumberStepDefinitionCreationContext}
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep}

import scala.jdk.CollectionConverters._

object BatchStepCreation {
  def missingSteps(feature: GherkinFile, selection: Option[TextRange]): Seq[GherkinStep] =
    PsiTreeUtil.findChildrenOfType(feature, classOf[GherkinStep]).asScala.toSeq
      .filter(s => selection.forall(_.intersects(s.getTextRange)) && matchingSteps(s).isEmpty)
      .map(step => step -> ScStepDefinitionCreator.snippetsFor(step)).filter(_._2.nonEmpty)
      // The keyword does not participate in Cucumber matching.
      .distinctBy(_._2.map(_.dropWhile(_ != '(')).sorted)
      .map(_._1)
}

private class BatchStepCreationFix(steps: Seq[GherkinStep]) extends CucumberCreateStepFixBase {
  override def getName: String = "Create Missing Scala Steps…"

  override protected def createStepOrSteps(step: GherkinStep, context: CucumberStepDefinitionCreationContext): Unit = {
    // Reuse the quick fix's destination chooser, new-file dialog and Scala creator.
    // The context retains the chosen (or newly created) file for the remaining steps.
    steps.iterator.takeWhile(s => createFileOrStepDefinition(s, context)).foreach(_ => ())
  }

  override protected def shouldRunTemplateOnStepDefinition(): Boolean = steps.size == 1
}

class BatchStepCreationAction extends GherkinFileAction("Create Missing Scala Steps…") {
  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    val editor = event.getData(CommonDataKeys.EDITOR)
    event.getData(CommonDataKeys.PSI_FILE) match {
      case file: GherkinFile if project != null && editor != null =>
        val steps = BatchStepCreation.missingSteps(file, selectedRange(editor))
        steps.headOption match {
          case None => Messages.showInfoMessage(project, "No missing steps in the selected scope.", "Create Scala Steps")
          case Some(step) =>
            val fix = new BatchStepCreationFix(steps)
            val descriptor = InspectionManager.getInstance(project).createProblemDescriptor(
              step, "Missing Scala steps", fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true)
            fix.applyFix(project, descriptor)
        }
      case _ =>
    }
  }
}
