package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.{ScCucumber, ScCucumberSpecBase, ScalaTableClass, SuiteAnalysis}
import com.intellij.psi.search.GlobalSearchScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class SuiteAnalysisSpec extends ScCucumberSpecBase {
  @Test
  def testHealthSeparatesUsagesDefinitionsAndScenarios(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"account {int}\") { (id: Int) => () }; Given(\"account {word}\") { (id: String) => () }; Then(\"unused\") { () => () } }")
    myFixture.addFileToProject("first.feature", "Feature: First\n  Scenario: First\n    Given account 42\n    Then missing\n")
    myFixture.configureByText("second.feature", "Feature: Second\n  Scenario: Second\n    Given account 42\n    Then missing\n")
    ScCucumber.inReadAction {
      val report = SuiteAnalysis.scan(getProject, Seq(myFixture.getModule), GlobalSearchScope.projectScope(getProject))
      report.features shouldBe 2
      report.scenarios shouldBe 2
      report.stepUsages shouldBe 4
      report.definitions shouldBe 3
      report.findings.count(_.category == "Undefined") shouldBe 2
      report.findings.count(f => f.category == "Ambiguous" && f.target.getContainingFile.getName.endsWith(".feature")) shouldBe 2
      report.findings.count(_.category == "Unused") shouldBe 1
      report.findings.count(_.category == "Duplicate scenarios") shouldBe 2
    }
  }

  @Test
  def testCaseClassInferenceUsesEveryRowAndQuotesIdentifiers(): Unit = {
    ScalaTableClass.source("TransferRow", Seq(Seq("account", "amount", "active"), Seq("Alice", "10", "true"), Seq("Bob", "20", "false"))) shouldBe
      Right("case class TransferRow(account: String, amount: Int, active: Boolean)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("type", "mixed", "missing"), Seq("a", "1", ""), Seq("b", "text", "2"))) shouldBe
      Right("case class DataRow(`type`: String, mixed: String, missing: String)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("x", "x"), Seq("1", "2"))).isLeft shouldBe true
  }
}
