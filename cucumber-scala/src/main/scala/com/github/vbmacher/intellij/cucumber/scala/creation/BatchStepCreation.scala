package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.util.{Dialogs, GherkinFileAction}
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.{FileChooser, FileChooserDescriptorFactory}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.jdk.CollectionConverters._

object BatchStepCreation {
  def proposals(feature: GherkinFile, selection: Option[TextRange]): Seq[Seq[String]] =
    PsiTreeUtil.findChildrenOfType(feature, classOf[GherkinStep]).asScala.toSeq
      .filter(s => selection.forall(_.intersects(s.getTextRange)) && matchingSteps(s).isEmpty)
      .map(ScStepDefinitionCreator.snippetsFor).filter(_.nonEmpty)
      // The keyword does not participate in Cucumber matching.
      .distinctBy(_.map(_.dropWhile(_ != '(')).sorted)

  def apply(destination: ScalaFile, sources: Seq[String]): Boolean = {
    val body = destination.typeDefinitions.headOption.flatMap(_.extendsBlock.templateBody)
    if (!destination.isValid || !destination.isWritable || body.isEmpty || sources.isEmpty) false
    else {
      val target = body.get
      // Parse all expressions before mutating the destination.
      val expressions = sources.map(ScalaPsiElementFactory.createExpressionFromText(_, target)(ProjectContext.fromProject(destination.getProject)))
      expressions.foreach(e => target.addBefore(e, target.getLastChild))
      PsiDocumentManager.getInstance(destination.getProject).commitAllDocuments()
      true
    }
  }
}

class BatchStepCreationAction extends GherkinFileAction("Create Missing Scala Steps…") {
  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    val editor = event.getData(CommonDataKeys.EDITOR)
    event.getData(CommonDataKeys.PSI_FILE) match {
      case file: GherkinFile if project != null && editor != null =>
        val proposals = BatchStepCreation.proposals(file, selectedRange(editor))
        if (proposals.isEmpty) Messages.showInfoMessage(project, "No missing steps in the selected scope.", "Create Scala Steps")
        else {
          val chosen = proposals.map { choices =>
            if (choices.size == 1) choices.headOption
            else choices.lift(Messages.showChooseDialog(project, "Choose a parameter mapping", "Create Scala Steps", null, choices.toArray, choices.head))
          }
          if (chosen.forall(_.nonEmpty)) {
            val virtualFile = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor("scala"), project, null)
            Option(virtualFile).flatMap(v => Option(PsiManager.getInstance(project).findFile(v))).foreach {
              case destination: ScalaFile if destination.isWritable && destination.typeDefinitions.headOption.flatMap(_.extendsBlock.templateBody).nonEmpty =>
                val sources = chosen.flatten
                if (Dialogs.confirm(project, "Create Scala Steps in " + destination.getName, sources.mkString("\n\n")))
                  WriteCommandAction.runWriteCommandAction(project, "Create Scala steps", null, () => { BatchStepCreation.apply(destination, sources); () }, destination)
              case _ => Messages.showErrorDialog(project, "Choose a writable Scala step class with a body.", "Create Scala Steps")
            }
          }
        }
      case _ =>
    }
  }
}
