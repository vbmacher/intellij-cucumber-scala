package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.{ScCucumberSpecBase, ScalaCucumberSettings}
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepCompletionSpec extends ScCucumberSpecBase {
  @Test
  def testFeatureWordingIsExplicitlyUndefined(): Unit = {
    myFixture.addFileToProject("consent.feature", "Feature: Consent\n  Scenario: Consent\n    Given customer has consented\n")
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer exists\") { () => () } }")
    myFixture.configureByText("author.feature", "Feature: Author\n  Scenario: Author\n    Given customer<caret>\n")
    val completions = myFixture.completeBasic()
    val presentation = new LookupElementPresentation()
    val implemented = completions.find(_.getLookupString == "customer exists").getOrElse(fail("Missing Scala completion"))
    implemented.renderElement(presentation)
    presentation.getTypeText should include("Steps.scala")
    presentation.getTailText should include("matching scenarios")
  }


}
