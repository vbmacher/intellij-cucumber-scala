package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.util.GherkinFileAction
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class SuggestEdgeCasesAction extends GherkinFileAction("Suggest edge cases…") {
  override def update(event: AnActionEvent): Unit = {
    super.update(event)
    event.getPresentation.setEnabledAndVisible(event.getPresentation.isEnabledAndVisible &&
      Option(event.getProject).exists(ScalaCucumberSettings.getInstance(_).scenarioDrafts) &&
      event.getData(CommonDataKeys.EDITOR) != null)
  }

  override def actionPerformed(event: AnActionEvent): Unit = for {
    project <- Option(event.getProject) if ScalaCucumberSettings.getInstance(project).scenarioDrafts
    file <- Option(event.getData(CommonDataKeys.PSI_FILE)).collect { case f: GherkinFile => f }
    editor <- Option(event.getData(CommonDataKeys.EDITOR))
  } {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    val selection = selectedRange(editor).map(range => range.getStartOffset -> range.getEndOffset)
    inReadAction(DraftContext.collect(file, editor.getDocument, editor.getCaretModel.getOffset, selection)) match {
      case Left(message) => Messages.showInfoMessage(project, message, "Suggest edge cases")
      case Right(context) => new ScenarioDraftDialog(project, context).show()
    }
  }
}
