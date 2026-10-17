package com.github.vbmacher.intellij.cucumber.scala.util

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.cucumber.psi.GherkinFile

/** Editor action available only in Gherkin feature files. */
abstract class GherkinFileAction(text: String) extends AnAction(text) {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(event.getData(CommonDataKeys.PSI_FILE).isInstanceOf[GherkinFile])

  protected def selectedRange(editor: Editor): Option[TextRange] =
    Option(editor).map(_.getSelectionModel).filter(_.hasSelection).map(s => new TextRange(s.getSelectionStart, s.getSelectionEnd))
}
