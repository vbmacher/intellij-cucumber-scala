package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.{ScStepDocumentation, StepUsage}
import com.github.vbmacher.intellij.cucumber.scala.ScCucumber.inReadAction
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepDocumentationSpec extends StepResolveSpecBase {
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
