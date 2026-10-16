package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.{Caret, Editor}
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinTable

import scala.jdk.CollectionConverters._

abstract class GherkinTableNavigation(original: EditorActionHandler, direction: String) extends EditorActionHandler {
  override def doExecute(editor: Editor, caret: Caret, context: DataContext): Unit = {
    if (!GherkinTableNavigation.navigate(editor, direction)) original.execute(editor, caret, context)
  }
}
object GherkinTableNavigation {
  def navigate(editor: Editor, direction: String): Boolean = {
    val project = editor.getProject
    val selected = if (project != null && project.getService(classOf[ScalaCucumberSettings]).tableNavigation && editor.getCaretModel.getCaretCount == 1)
      GherkinTables.selection(editor).filter(_.rectangular) else None
    selected match {
      case None => false
      case Some(selection) =>
        val width = selection.cells.head.size
        val current = selection.row * width + selection.column
        val next = direction match {
          case "previous" => math.max(0, current - 1)
          case "enter" => (selection.row + 1) * width + selection.column
          case _ => current + 1
        }
        val tableStart = selection.table.getTextOffset
        if (next >= selection.cells.size * width)
          com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, "Extend Gherkin table", null,
            () => GherkinTables.replace(editor, selection, selection.cells :+ Vector.fill(width)("")))
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
        Option(PsiTreeUtil.getParentOfType(file.findElementAt(tableStart), classOf[GherkinTable], false)).foreach { table =>
          val rows = Option(table.getHeaderRow).toSeq ++ table.getDataRows.asScala
          rows.lift(next / width).flatMap(r => r.getPsiCells.asScala.lift(next % width)).foreach { cell =>
            editor.getSelectionModel.removeSelection()
            editor.getCaretModel.moveToOffset(cell.getTextOffset)
            editor.getSelectionModel.setSelection(cell.getTextRange.getStartOffset, cell.getTextRange.getEndOffset)
          }
        }
        true
    }
  }
}
class GherkinTableTab(original: EditorActionHandler) extends GherkinTableNavigation(original, "next")
class GherkinTableBackTab(original: EditorActionHandler) extends GherkinTableNavigation(original, "previous")
class GherkinTableEnter(original: EditorActionHandler) extends GherkinTableNavigation(original, "enter")

class GherkinTablePipe extends com.intellij.codeInsight.editorActions.TypedHandlerDelegate {
  override def beforeCharTyped(c: Char, project: com.intellij.openapi.project.Project, editor: Editor,
                               file: com.intellij.psi.PsiFile, fileType: com.intellij.openapi.fileTypes.FileType): com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result = {
    import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
    val offset = editor.getCaretModel.getOffset
    val backslashes = editor.getDocument.getCharsSequence.subSequence(0, offset).toString.reverse.takeWhile(_ == '\\').length
    if (c == '|' && backslashes % 2 == 0 && GherkinTableNavigation.navigate(editor, "next")) Result.STOP else Result.CONTINUE
  }
}
class GherkinTableDelete(original: EditorActionHandler) extends EditorActionHandler {
  override def doExecute(editor: Editor, caret: Caret, context: DataContext): Unit = {
    val project = editor.getProject
    val selection = if (project != null && project.getService(classOf[ScalaCucumberSettings]).tableNavigation && editor.getSelectionModel.hasSelection)
      GherkinTables.selection(editor).filter(_.rectangular) else None
    selection match {
      case None => original.execute(editor, caret, context)
      case Some(value) =>
        val cleared = value.cells.zipWithIndex.map { case (row, r) => row.zipWithIndex.map { case (cell, column) =>
          if (r >= value.row && r <= value.lastRow && column >= value.column && column <= value.lastColumn) "" else cell
        } }
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, "Clear Gherkin cells", null,
          () => GherkinTables.replace(editor, value, cleared))
        editor.getSelectionModel.removeSelection()
    }
  }
}
