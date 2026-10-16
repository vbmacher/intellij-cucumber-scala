package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea, RangeHighlighter}
import com.intellij.openapi.fileEditor.{FileEditorManager, TextEditor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.{XDebugSession, XDebugSessionListener}
import com.intellij.xdebugger.ui.DebuggerColors

/** A session owns its own highlights; resuming or stopping never clears another session's state. */
class FeatureDebugHighlight(project: Project, session: XDebugSession) {
  private var highlighters = Seq.empty[RangeHighlighter]
  private var generation = 0L
  session.addSessionListener(new XDebugSessionListener {
    override def beforeSessionResume(): Unit = clear()
    override def sessionStopped(): Unit = clear()
  })
  def show(uri: String, step: Int, example: Option[Int]): Unit = {
    val current = synchronized { generation += 1; generation }
    ApplicationManager.getApplication.invokeLater(() => {
      if (!project.isDisposed && synchronized { current == generation }) {
        highlighters.foreach(_.dispose()); highlighters = Seq.empty
        Option(VirtualFileManager.getInstance().findFileByUrl(uri)).foreach { file =>
          FileEditorManager.getInstance(project).getEditors(file).collect { case text: TextEditor => text.getEditor }.distinct.foreach { editor =>
            val document = editor.getDocument
            val attributes = EditorColorsManager.getInstance().getGlobalScheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES)
            highlighters ++= (Seq(step) ++ example).distinct.filter(line => line > 0 && line <= document.getLineCount).map { line =>
              editor.getMarkupModel.addRangeHighlighter(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1),
                HighlighterLayer.SELECTION - 1, attributes, HighlighterTargetArea.LINES_IN_RANGE)
            }
          }
        }
      }
    }, ModalityState.any())
  }
  private def clear(): Unit = {
    synchronized { generation += 1 }
    ApplicationManager.getApplication.invokeLater(() => { highlighters.foreach(_.dispose()); highlighters = Seq.empty }, ModalityState.any())
  }
}
