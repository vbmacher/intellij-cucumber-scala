package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages

import java.awt.datatransfer.{DataFlavor, StringSelection}

abstract class GherkinTableAction(operation: String) extends AnAction {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(Option(event.getData(CommonDataKeys.EDITOR)).flatMap(GherkinTables.selection).nonEmpty)

  override def actionPerformed(event: AnActionEvent): Unit = {
    for (editor <- Option(event.getData(CommonDataKeys.EDITOR)); selection <- GherkinTables.selection(editor)) {
      if (!selection.rectangular) Messages.showErrorDialog(event.getProject, "Correct the ragged table before editing its structure.", "Gherkin Table")
      else {
        val cells = selection.cells
        val changed: Option[Seq[Seq[String]]] = operation match {
          case "clear" => Some(clear(selection))
          case "deleteRow" => if (selection.row == 0) None else Some(cells.patch(selection.row, Nil, selection.lastRow - selection.row + 1))
          case "deleteColumn" => if (cells.head.size <= selection.lastColumn - selection.column + 1) None
            else Some(cells.map(_.patch(selection.column, Nil, selection.lastColumn - selection.column + 1)))
          case _ => None
              }
            }
          case _ => None
        }
        changed.foreach { value =>
          val preview = GherkinTables.render(value).mkString("\n")
          val needsPreview = Set("transpose", "paste", "deleteRow", "deleteColumn").contains(operation)
          if (!needsPreview || EditPreview.confirm(event.getProject, "Gherkin Table · " + operation, preview))
            WriteCommandAction.runWriteCommandAction(event.getProject, "Edit Gherkin table", null, () => GherkinTables.replace(editor, selection, value))
        }
      }
    }
  }

  private def clear(selection: GherkinTables.Selection): Seq[Seq[String]] = selection.cells.zipWithIndex.map { case (row, r) =>
    row.zipWithIndex.map { case (value, c) => if (r >= selection.row && r <= selection.lastRow && c >= selection.column && c <= selection.lastColumn) "" else value }
  }
}
class ClearTableCellsAction extends GherkinTableAction("clear")
class DeleteTableRowsAction extends GherkinTableAction("deleteRow")
class DeleteTableColumnsAction extends GherkinTableAction("deleteColumn")
