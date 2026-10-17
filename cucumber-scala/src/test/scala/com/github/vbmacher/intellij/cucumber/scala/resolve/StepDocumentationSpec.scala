package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.editor.ScStepDocumentation
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.steps.StepUsage
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepDocumentationSpec extends StepResolveSpecBase {
  @Test
  def testAmbiguousDocumentationKeepsEveryCandidateAndCaptures(): Unit = {
    myFixture.addFileToProject("Accounts.scala", "class Accounts { Given(\"account {int} exists\") { (id: Int) => () } }")
    myFixture.addFileToProject("Generic.scala", "class Generic { Given(\"account {word} exists\") { (name: String) => () } }")
    myFixture.configureByText("accounts.feature", "Feature: Accounts\n  Scenario: Existing\n    Given account 42 exists\n")
    inReadAction {
      val step = myFixture.findElementByText("Given account 42 exists", classOf[GherkinStep])
      val html = new ScStepDocumentation().generateDoc(step, step)
      html should include("Ambiguous step: 2 matching definitions")
      html should include("Accounts.scala:1")
      html should include("Generic.scala:1")
      html should include("id: Int = 42")
      html should include("name: String = 42")
      val definition = findStepDefinitions("Given account 42 exists").head.getElement
      StepUsage.scenarios(definition).map(_.getScenarioName) shouldBe Seq("Existing")
    }
  }

  @Test
  def testBackgroundCountsScenariosAndOutlineOnce(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"setup\") { () => () } }")
    myFixture.configureByText("counts.feature",
      """Feature: Counts
        |  Background: Shared
        |    Given setup
        |  Scenario: First
        |    Then missing
        |  Scenario Outline: Second
        |    Then <value>
        |    Examples:
        |      | value |
        |      | one |
        |      | two |
        |""".stripMargin)
    inReadAction {
      val definition = findStepDefinitions("Given setup").head.getElement
      StepUsage.scenarios(definition).map(_.getScenarioName).toSet shouldBe Set("First", "Second")
    }
  }
}
