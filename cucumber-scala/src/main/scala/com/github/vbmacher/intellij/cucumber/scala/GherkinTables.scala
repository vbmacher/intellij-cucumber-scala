package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.{GherkinExamplesBlock, GherkinTable, GherkinTableCell, GherkinTableRow}

import scala.jdk.CollectionConverters._

object GherkinTables {
  final case class Selection(table: GherkinTable, rows: Seq[GherkinTableRow], cells: Vector[Vector[String]], row: Int, column: Int,
                             lastRow: Int, lastColumn: Int) {
    def rectangular: Boolean = cells.nonEmpty && cells.map(_.size).distinct.size == 1
    def isExamples: Boolean = table.getParent.isInstanceOf[GherkinExamplesBlock]
    def selected: Vector[Vector[String]] = cells.slice(row, lastRow + 1).map(_.slice(column, lastColumn + 1))
  }

  def selection(editor: Editor): Option[Selection] = {
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    def cellAt(offset: Int): Option[GherkinTableCell] = Option(file).flatMap(f => Option(f.findElementAt(offset)))
      .flatMap { e =>
        Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinTableCell], false)).orElse {
          Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinTableRow], false)).flatMap(_.getPsiCells.asScala
            .find(c => c.getTextRange.containsOffset(offset)))
        }
      }
    val selected = editor.getSelectionModel
    val start = if (selected.hasSelection) selected.getSelectionStart else editor.getCaretModel.getOffset
    val end = if (selected.hasSelection) math.max(start, selected.getSelectionEnd - 1) else start
    for {
      first <- cellAt(start)
      last <- cellAt(end)
      table <- Option(PsiTreeUtil.getParentOfType(first, classOf[GherkinTable]))
      if PsiTreeUtil.isAncestor(table, last, true)
    } yield {
      val rows = (Option(table.getHeaderRow).toSeq ++ table.getDataRows.asScala).distinct
      def position(cell: GherkinTableCell): (Int, Int) = {
        val r = rows.indexOf(cell.getParent)
        r -> rows(r).getPsiCells.indexOf(cell)
      }
      val a = position(first)
      val b = position(last)
      Selection(table, rows, rows.map(_.getPsiCells.asScala.map(_.getText.trim).toVector).toVector,
        math.min(a._1, b._1), math.min(a._2, b._2), math.max(a._1, b._1), math.max(a._2, b._2))
    }
  }

  def render(cells: Seq[Seq[String]]): Seq[String] = {
    val columns = cells.map(_.size).maxOption.getOrElse(0)
    val widths = (0 until columns).map(i => cells.map(_.lift(i).getOrElse("").length).maxOption.getOrElse(0))
    cells.map(row => (0 until columns).map(i => row.lift(i).getOrElse("").padTo(widths(i), ' ')).mkString("| ", " | ", " |"))
  }

  // Replace row ranges independently so inter-row comments and surrounding source survive.
  def replace(editor: Editor, selection: Selection, cells: Seq[Seq[String]]): Unit = {
    val document = editor.getDocument
    val rendered = render(cells)
    val last = selection.rows.last.getTextRange.getEndOffset
    val first = selection.rows.head.getTextOffset
    val line = document.getLineStartOffset(document.getLineNumber(first))
    val indent = document.getText.substring(line, first)
    if (rendered.size > selection.rows.size)
      document.insertString(last, rendered.drop(selection.rows.size).map("\n" + indent + _).mkString)
    selection.rows.zipWithIndex.reverse.foreach { case (row, i) =>
      if (i < rendered.size) document.replaceString(row.getTextRange.getStartOffset, row.getTextRange.getEndOffset, rendered(i))
      else {
        val begin = document.getLineStartOffset(document.getLineNumber(row.getTextOffset))
        val finish = math.min(document.getTextLength, document.getLineEndOffset(document.getLineNumber(row.getTextOffset)) + 1)
        document.deleteString(begin, finish)
      }
    }
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(document)
  }

  def decode(value: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < value.length) {
      if (value(i) == '\\' && i + 1 < value.length) {
        value(i + 1) match {
          case 'n' => out += '\n'; i += 1
          case '|' => out += '|'; i += 1
          case '\\' => out += '\\'; i += 1
          case _ => out += value(i)
        }
      } else out += value(i)
      i += 1
    }
    out.result()
  }
  def encode(value: String): String = value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")

}
