package com.github.vbmacher.intellij.cucumber.scala.tables

import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages

import java.awt.datatransfer.{DataFlavor, StringSelection}

abstract class GherkinTableAction(operation: String) extends AnAction {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(Option(event.getData(CommonDataKeys.EDITOR)).flatMap(GherkinTables.selection).nonEmpty)

  override def actionPerformed(event: AnActionEvent): Unit = {
    for (editor <- Option(event.getData(CommonDataKeys.EDITOR)); selection <- GherkinTables.committedSelection(editor)) {
      if (!selection.rectangular) Messages.showErrorDialog(event.getProject, "Correct the ragged table before editing its structure.", "Gherkin Table")
      else {
        val cells = selection.cells
        val changed: Option[Seq[Seq[String]]] = operation match {
          case "copy" | "cut" =>
            CopyPasteManager.getInstance().setContents(new StringSelection(GherkinTables.toTsv(selection.selected)))
            if (operation == "cut") Some(clear(selection)) else None
          case "clear" => Some(clear(selection))
          case "transpose" if selection.isExamples =>
            Messages.showErrorDialog(event.getProject, "Examples headers define outline parameters. Transpose is available only for DataTables.", "Transpose Table"); None
          case "transpose" => Some(cells.transpose)
          case "rowUp" | "rowDown" =>
            val to = selection.row + (if (operation == "rowUp") -1 else 1)
            if (selection.row == 0 || to <= 0 || to >= cells.size) None
            else Some(cells.updated(selection.row, cells(to)).updated(to, cells(selection.row)))
          case "columnLeft" | "columnRight" =>
            val to = selection.column + (if (operation == "columnLeft") -1 else 1)
            if (to < 0 || to >= cells.head.size) None
            else Some(cells.map(row => row.updated(selection.column, row(to)).updated(to, row(selection.column))))
          case "deleteRow" => if (selection.row == 0) None else Some(cells.patch(selection.row, Nil, selection.lastRow - selection.row + 1))
          case "deleteColumn" => if (cells.head.size <= selection.lastColumn - selection.column + 1) None
            else Some(cells.map(_.patch(selection.column, Nil, selection.lastColumn - selection.column + 1)))
          case "paste" =>
            Option(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)).flatMap { text =>
              GherkinTables.fromTsv(text) match {
                case Left(message) => Messages.showErrorDialog(event.getProject, message, "Paste TSV"); None
                case Right(pasted) if pasted.nonEmpty =>
                  val width = math.max(cells.head.size, selection.column + pasted.head.size)
                  val height = math.max(cells.size, selection.row + pasted.size)
                  Some(Vector.tabulate(height, width) { (r, c) =>
                    pasted.lift(r - selection.row).flatMap(_.lift(c - selection.column))
                      .getOrElse(cells.lift(r).flatMap(_.lift(c)).getOrElse(""))
                  })
                case _ => None
              }
            }
          case _ => None
        }
        changed.foreach { value =>
          val preview = GherkinTables.render(value).mkString("\n")
          val needsPreview = Set("transpose", "paste", "deleteRow", "deleteColumn").contains(operation)
          if (!needsPreview || Dialogs.confirm(event.getProject, "Gherkin Table · " + operation, preview))
            WriteCommandAction.runWriteCommandAction(event.getProject, "Edit Gherkin table", null, () => GherkinTables.replace(editor, selection, value))
        }
      }
    }
  }

  private def clear(selection: GherkinTables.Selection): Seq[Seq[String]] = selection.cells.zipWithIndex.map { case (row, r) =>
    row.zipWithIndex.map { case (value, c) => if (r >= selection.row && r <= selection.lastRow && c >= selection.column && c <= selection.lastColumn) "" else value }
  }
}
class CopyTableTsvAction extends GherkinTableAction("copy")
class CutTableTsvAction extends GherkinTableAction("cut")
class PasteTableTsvAction extends GherkinTableAction("paste")
class ClearTableCellsAction extends GherkinTableAction("clear")
class TransposeTableAction extends GherkinTableAction("transpose")
class MoveTableRowUpAction extends GherkinTableAction("rowUp")
class MoveTableRowDownAction extends GherkinTableAction("rowDown")
class MoveTableColumnLeftAction extends GherkinTableAction("columnLeft")
class MoveTableColumnRightAction extends GherkinTableAction("columnRight")
class DeleteTableRowsAction extends GherkinTableAction("deleteRow")
class DeleteTableColumnsAction extends GherkinTableAction("deleteColumn")
