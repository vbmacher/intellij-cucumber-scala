package com.github.vbmacher.intellij.cucumber.scala.scenarios

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.publish.FeatureHtml
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScCucumber, StepUsage}
import com.github.vbmacher.intellij.cucumber.scala.suite.{SuiteAnalysis, SuiteView}
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.search.GlobalSearchScope
import io.cucumber.tagexpressions.TagExpressionParser
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep}
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class ScenarioInteractionsSpec extends ScCucumberSpecBase {
  @Test
  def testBackgroundBindingsAgreeAcrossNavigationUsagesCompletionAndSuite(): Unit = {
    myFixture.addFileToProject("Steps.scala",
      "class Steps { Given(\"account exists\") { () => () }; Given(\"account local\") { () => () } }")
    myFixture.configureByText("backgrounds.feature", """Feature: Accounts
      | Background:
      |  Given account exists
      | Rule: First
      |  Background:
      |   Given account local
      |  Scenario Outline: First account
      |   Then missing <id>
      |   Examples:
      |    | id |
      |    | 1  |
      |    | 2  |
      | Rule: Second
      |  Scenario: Second account
      |   Then missing 3
      |""".stripMargin)
    ScCucumber.inReadAction {
      val report = SuiteAnalysis.scan(getProject, Seq(myFixture.getModule), GlobalSearchScope.projectScope(getProject))
      report.scenarios shouldBe 2
      report.examples shouldBe 2
      Seq("account exists" -> Set("First account", "Second account"), "account local" -> Set("First account")).foreach {
        case (wording, expected) =>
          val step = myFixture.findElementByText("Given " + wording, classOf[GherkinStep])
          val definition = ScCucumber.matchingSteps(step).head.getElement
          val reference = step.getReferences.collectFirst { case value: CucumberStepReference => value }.get
          reference.getRangeInElement.substring(step.getText) shouldBe wording
          reference.multiResolve(false).map(_.getElement).toSeq shouldBe Seq(definition)
          StepUsage.scenarios(definition).map(_.getScenarioName).toSet shouldBe expected
          val finding = report.findings.find(f => f.view == SuiteView.Definitions && f.source.target == definition).get
          finding.related.map(_.label).toSet shouldBe expected
      }
      val declarations = Scenarios.declarations(myFixture.getFile)
      Scenarios.backgrounds(declarations.head).map(_.getSteps.head.getName) shouldBe Seq("account exists", "account local")
      Scenarios.backgrounds(declarations.last).map(_.getSteps.head.getName) shouldBe Seq("account exists")
    }
    myFixture.configureByText("author.feature", "Feature: Author\n Scenario: Author\n  Given acc<caret>\n")
    val completions = myFixture.completeBasic()
    Seq("account exists" -> 2, "account local" -> 1).foreach { case (wording, count) =>
      val presentation = new LookupElementPresentation
      completions.find(_.getLookupString == wording).get.renderElement(presentation)
      presentation.getTailText should include(s"$count matching scenarios")
    }
  }

  @Test
  def testExamplesTagsSelectTheSameRowsForSuiteRunningAndExport(): Unit = {
    val file = myFixture.configureByText("tags.feature", """@feature
      |Feature: Payments
      | Rule: Transfer
      |  @outline
      |  Scenario Outline: Pay
      |   When pay <amount>
      |   @first
      |   Examples: First
      |    | amount |
      |    | 10     |
      |   @second
      |   Examples: Second
      |    | amount |
      |    | 20     |
      |""".stripMargin).asInstanceOf[GherkinFile]
    ScCucumber.inReadAction {
      val scope = GlobalSearchScope.fileScope(file)
      val report = SuiteAnalysis.scan(getProject, Seq(myFixture.getModule), scope)
      val scenario = report.findings.find(_.view == SuiteView.Scenarios).get
      Seq("@feature and @outline and @second and not @first" -> 1, "@first and @second" -> 0).foreach {
        case (expression, count) =>
          val parsed = TagExpressionParser.parse(expression)
          val locations = Scenarios.locations(getProject, scope, expression).toOption.get
          locations.size shouldBe count
          scenario.tagSets.exists(tags => parsed.evaluate(tags.asJava)) shouldBe locations.nonEmpty
          if (locations.nonEmpty) {
            val html = FeatureHtml.render(file, Some(locations.map(_.scenario.getTextOffset).toSet), Some(locations.map(_.target.getTextOffset).toSet))
            html should include("20")
            html should not include ">10<"
            html should not include "Examples: First"
            locations.head.tags.toSet shouldBe Set("@feature", "@outline", "@second")
          }
      }
      Scenarios.locations(getProject, scope, "@second and (").isLeft shouldBe true
    }
  }

  @Test
  def testEmptyOutlineIsOneDeclarationWithoutExecutableRows(): Unit = {
    myFixture.configureByText("empty.feature", "Feature: Empty\n Scenario Outline: Empty\n  Given <value>\n  Examples:\n   | value |\n")
    ScCucumber.inReadAction {
      Scenarios.declarations(getProject, GlobalSearchScope.projectScope(getProject)).size shouldBe 1
      Scenarios.locations(getProject, GlobalSearchScope.projectScope(getProject), "").toOption.get shouldBe empty
    }
  }
}
