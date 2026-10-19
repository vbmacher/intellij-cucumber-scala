package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, ActionUpdateThread, AnActionEvent, CommonDataKeys, DataContext, DefaultActionGroup}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class FindSimilarActionSpec extends ScCucumberSpecBase {
  private def event: AnActionEvent = {
    val context: DataContext = key =>
      if (CommonDataKeys.PROJECT.is(key)) getProject
      else if (CommonDataKeys.PSI_FILE.is(key)) myFixture.getFile
      else if (CommonDataKeys.EDITOR.is(key)) myFixture.getEditor
      else null
    AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, context)
  }

  @Test
  def testFeatureScenarioOutlineAndStepQueries(): Unit = {
    val action = new FindSimilarAction
    action.getActionUpdateThread shouldBe ActionUpdateThread.BGT
    Seq(
      "<caret>Feature: Payments\n  Scenario: Account\n    Given balance\n" -> "Payments",
      "Feature: Payments\n  Scenario: Acc<caret>ount\n    Given balance\n" -> "Account",
      "Feature: Payments\n  Scenario Outline: Acc<caret>ount <id>\n    Given balance\n" -> "Account <id>",
      "Feature: Payments\n  Scenario: Account\n    Given bal<caret>ance\n" -> "balance"
    ).foreach { case (source, expected) =>
      myFixture.configureByText("payments.feature", source)
      action.searchText(event) shouldBe Some(expected)
      val update = event
      action.update(update)
      update.getPresentation.isEnabledAndVisible shouldBe true
    }
    val manager = ActionManager.getInstance()
    manager.getAction("EditorPopupMenu").asInstanceOf[DefaultActionGroup].getChildren(null: AnActionEvent)
      .contains(manager.getAction("ScalaCucumber.FindSimilar")) shouldBe true
  }

  @Test
  def testSelectedTextAndUnsupportedContexts(): Unit = {
    val action = new FindSimilarAction
    myFixture.configureByText("payments.feature", "Feature: Payments\n  Scenario: Account\n    Given account balance\n")
    val start = myFixture.getFile.getText.indexOf("account balance")
    myFixture.getEditor.getSelectionModel.setSelection(start, start + 7)
    action.searchText(event) shouldBe Some("account")
    myFixture.getEditor.getSelectionModel.removeSelection()
    myFixture.configureByText("payments.feature", "Feature: Payments\n  # <caret>comment\n  Scenario: Account\n    Given balance\n")
    action.searchText(event) shouldBe None
    myFixture.configureByText("plain.txt", "<caret>Payments")
    val update = event
    action.update(update)
    update.getPresentation.isEnabledAndVisible shouldBe false
  }
}
