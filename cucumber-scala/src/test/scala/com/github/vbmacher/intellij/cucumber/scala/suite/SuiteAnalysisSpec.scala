package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.{ScCucumber, ScCucumberSpecBase, ScalaTableClass, SuiteAnalysis}
import com.intellij.psi.search.GlobalSearchScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class SuiteAnalysisSpec extends ScCucumberSpecBase {
  @Test
  def testCaseClassInferenceUsesEveryRowAndQuotesIdentifiers(): Unit = {
    ScalaTableClass.source("TransferRow", Seq(Seq("account", "amount", "active"), Seq("Alice", "10", "true"), Seq("Bob", "20", "false"))) shouldBe
      Right("case class TransferRow(account: String, amount: Int, active: Boolean)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("type", "mixed", "missing"), Seq("a", "1", ""), Seq("b", "text", "2"))) shouldBe
      Right("case class DataRow(`type`: String, mixed: String, missing: String)\n")
    ScalaTableClass.source("DataRow", Seq(Seq("x", "x"), Seq("1", "2"))).isLeft shouldBe true
  }
}
