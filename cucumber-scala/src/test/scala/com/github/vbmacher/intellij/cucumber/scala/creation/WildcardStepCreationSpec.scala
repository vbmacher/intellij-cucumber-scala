package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class WildcardStepCreationSpec extends StepCreationSpecBase {
  private val quotes = "\"\"\""

  private def checkWildcard(featureText: String, keyword: String, pattern: String = "review", arguments: String = ""): Unit = {
    val destination = loadTestCase("creation/StepDefinitions.scala")(0)
    val feature = myFixture.configureByText("wildcard.feature", featureText)
    val originalFeature = feature.getText
    checkStepCreation(feature, destination,
      s"""import io.cucumber.scala.ScalaDsl
        |
        |class StepDefinitions extends ScalaDsl {
        |
        |  $keyword($quotes$pattern$quotes) { ($arguments) =>
        |    // Write code here that turns the phrase above into concrete actions
        |    throw new io.cucumber.scala.PendingException()
        |  }
        |}""".stripMargin
    )
    myFixture.doHighlighting()
    inReadAction {
      feature.getText shouldBe originalFeature
      val step = myFixture.findElementByText("* review", classOf[GherkinStep])
      val reference = step.getReferences.collectFirst { case r: CucumberStepReference => r }
        .getOrElse(fail("Expected a Cucumber step reference"))
      reference.getRangeInElement.substring(step.getText) shouldBe step.getName
      val targets = reference.multiResolve(false).map(_.getElement)
      targets.length shouldBe 1
      targets(0).getContainingFile shouldBe destination
      targets(0).getText shouldBe s"$keyword($quotes$pattern$quotes)"
    }
  }

  @Test
  def testFollowsGiven(): Unit = {
    checkWildcard("Feature: Review\n  Scenario: Review\n    Given setup\n    * re<caret>view\n", "Given")
  }

  @Test
  def testFollowsWhenThroughConjunctionsAndWildcards(): Unit = {
    checkWildcard(
      "Feature: Review\n  Scenario: Review\n    Given setup\n    When acting\n    And continuing\n    * more work\n    But still acting\n    # Keep the semantic keyword\n    * re<caret>view\n",
      "When")
  }

  @Test
  def testFollowsNearestThen(): Unit = {
    checkWildcard("Feature: Review\n  Scenario: Review\n    When acting\n    Then checking\n    And continuing\n    * re<caret>view\n", "Then")
  }

  @Test
  def testLeadingWildcardDoesNotInheritFromAnotherScenarioOrBackground(): Unit = {
    checkWildcard(
      "Feature: Review\n  Background: Setup\n    When setup\n  Scenario: Earlier\n    Then checking\n  Scenario: Review\n    * re<caret>view\n    Then later\n",
      "Given")
  }

  @Test
  def testFollowsKeywordWithinBackground(): Unit = {
    checkWildcard("Feature: Review\n  Background: Setup\n    When acting\n    * re<caret>view\n  Scenario: Review\n    Then checking\n", "When")
  }

  @Test
  def testPreservesExpressionParametersAndDocString(): Unit = {
    checkWildcard(
      s"Feature: Review\n  Scenario: Review\n    When acting\n    * re<caret>view 42\n      ${quotes}json\n      {\"message\": \"hello\"}\n      $quotes\n",
      "When", "review {int}", "int1: Int, docString: String")
  }

  @Test
  def testPreservesExpressionParametersAndDataTable(): Unit = {
    checkWildcard(
      "Feature: Review\n  Scenario: Review\n    Then checking\n    * re<caret>view 42\n      | name |\n      | Alice |\n",
      "Then", "review {int}", "int1: Int, dataTable: io.cucumber.datatable.DataTable")
  }
}
