package com.github.vbmacher.intellij.cucumber.scala.tables

import com.github.vbmacher.intellij.cucumber.scala.{GherkinTables, ScCucumberSpecBase, ScalaCucumberSettings}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class GherkinTableSpec extends ScCucumberSpecBase {
  @Test
  def testTransposePreservesEscapesAndSurroundingSource(): Unit = {
    val prefix = "Feature: Table\n  Scenario: Table\n    Given data\n"
    val suffix = "\n    Then unchanged\n"
    myFixture.configureByText("table.feature", prefix + "      | na<caret>me | amount |\n      | A\\|B | 10 |" + suffix)
    val selection = GherkinTables.selection(myFixture.getEditor).getOrElse(fail("No table selection"))
    selection.cells shouldBe Vector(Vector("name", "amount"), Vector("A\\|B", "10"))
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = GherkinTables.replace(myFixture.getEditor, selection, selection.cells.transpose)
    })
    myFixture.getFile.getText shouldBe prefix + "      | name   | A\\|B |\n      | amount | 10   |" + suffix
  }

  @Test
  def testQuotedTsvRoundTripsEmptyCellsAndEscapedContent(): Unit = {
    val cells = Vector(Vector("A\\|B", "line\\nnext", ""), Vector("tab\tvalue", "\\\\n", "a\"b"))
    GherkinTables.fromTsv(GherkinTables.toTsv(cells)) shouldBe Right(cells)
    GherkinTables.fromTsv("a\tb\nx").isLeft shouldBe true
    GherkinTables.fromTsv("\"unclosed").isLeft shouldBe true
  }

  @Test
  def testTabMovesWithinCellsAndExtendsLastRow(): Unit = {
    val settings = getProject.getService(classOf[ScalaCucumberSettings])
    settings.tableNavigation = true
    try {
      myFixture.configureByText("table.feature", "Feature: Table\n  Scenario: Table\n    Given data\n      | name | value |\n      | Alice | 1<caret> |\n")
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
      val selection = GherkinTables.selection(myFixture.getEditor).getOrElse(fail("No new cell"))
      selection.cells.size shouldBe 3
      selection.row shouldBe 2
      selection.column shouldBe 0
      selection.cells(2) shouldBe Vector("", "")
    } finally settings.tableNavigation = false
  }

  @Test
  def testExamplesAreDistinguishedFromDataTables(): Unit = {
    myFixture.configureByText("outline.feature", "Feature: Table\n  Scenario Outline: Table\n    Given <name>\n    Examples:\n      | name |\n      | Ali<caret>ce |\n")
    GherkinTables.selection(myFixture.getEditor).exists(_.isExamples) shouldBe true
  }
}
