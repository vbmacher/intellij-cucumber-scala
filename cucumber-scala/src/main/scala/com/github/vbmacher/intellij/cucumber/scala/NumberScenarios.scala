package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiManager}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.{GherkinScenario, GherkinStepsHolder}

import javax.swing._
import scala.jdk.CollectionConverters._

object NumberScenarios {
  final case class Edit(holder: GherkinStepsHolder, range: TextRange, before: String, after: String)
  def plan(files: Seq[PsiFile], selection: Option[TextRange], start: Int, width: Int, prefix: String): Seq[Edit] = {
    val existing = ("^" + java.util.regex.Pattern.quote(prefix) + "[0-9]+ - ").r
    val scenarios = files.sortBy(_.getVirtualFile.getPath).flatMap(file => PsiTreeUtil.findChildrenOfType(file, classOf[GherkinStepsHolder]).asScala)
      .filterNot { case s: GherkinScenario => s.isBackground; case _ => false }
      .filter(s => selection.forall(_.intersects(s.getTextRange)))
    scenarios.zipWithIndex.flatMap { case (scenario, index) =>
      val before = scenario.getScenarioName
      val clean = existing.replaceFirstIn(before, "")
      val number = (start.toLong + index).toString.reverse.padTo(width, '0').reverse
      val after = s"$prefix$number - $clean"
      val colon = scenario.getText.indexOf(':', ScenarioSelection.sourceOffset(scenario) - scenario.getTextOffset)
      val nameAt = scenario.getText.indexOf(before, colon + 1)
      if (before == after || nameAt < 0) None
      else Some(Edit(scenario, TextRange.from(scenario.getTextOffset + nameAt, before.length), before, after))
    }
  }

  def apply(edits: Seq[Edit]): Unit = {
    edits.groupBy(_.holder.getContainingFile).foreach { case (file, changes) =>
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      changes.sortBy(_.range.getStartOffset).reverse.foreach(e => document.replaceString(e.range.getStartOffset, e.range.getEndOffset, e.after))
      PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
    }
  }
}

class NumberScenariosAction extends AnAction("Number Scenario Titles…") {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(event.getData(CommonDataKeys.PSI_FILE).isInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile])

  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    val file = event.getData(CommonDataKeys.PSI_FILE)
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (project != null && file != null) {
      val start = new JSpinner(new SpinnerNumberModel(1, 0, Int.MaxValue, 1))
      val width = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1))
      val prefix = new JTextField("")
      val scope = new JComboBox[String](Array("Current file / editor selection", "Selected feature files"))
      val dialog = new DialogWrapper(project) {
        setTitle("Number Scenario Titles")
        init()
        override def createCenterPanel(): JComponent = {
          val panel = new JPanel(new java.awt.GridLayout(0, 2))
          panel.add(new JLabel("Start")); panel.add(start)
          panel.add(new JLabel("Minimum width")); panel.add(width)
          panel.add(new JLabel("Prefix")); panel.add(prefix)
          panel.add(new JLabel("Scope")); panel.add(scope)
          panel
        }
      }
      if (dialog.showAndGet()) {
        val files = if (scope.getSelectedIndex == 0) Seq(file)
          else Option(event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).toSeq.flatten.filter(_.getExtension == "feature")
            .flatMap(f => Option(PsiManager.getInstance(project).findFile(f)))
        val selection = if (scope.getSelectedIndex == 0 && editor != null && editor.getSelectionModel.hasSelection)
          Some(new TextRange(editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)) else None
        if (prefix.getText.exists(c => c == '\n' || c == '\r')) Messages.showErrorDialog(project, "The prefix must fit on one line.", "Number Scenarios")
        else {
          val edits = NumberScenarios.plan(files, selection, start.getValue.asInstanceOf[Int], width.getValue.asInstanceOf[Int], prefix.getText)
          if (files.exists(!_.isWritable)) Messages.showErrorDialog(project, "The selected scope contains read-only files.", "Number Scenarios")
          else if (EditPreview.confirm(project, s"${edits.size} Scenario Title Changes", edits.map(e => e.holder.getContainingFile.getName + ": " + e.before + " → " + e.after).mkString("\n")))
            WriteCommandAction.runWriteCommandAction(project, "Number scenario titles", null, () => NumberScenarios.apply(edits), files: _*)
        }
      }
    }
  }
}
