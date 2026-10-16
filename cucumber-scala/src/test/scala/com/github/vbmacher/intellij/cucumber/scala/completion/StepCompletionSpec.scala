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
    val wording = completions.find(_.getLookupString == "customer has consented").getOrElse(fail("Missing feature wording"))
    val presentation = new LookupElementPresentation()
    wording.renderElement(presentation)
    presentation.getTypeText should include("Feature wording")
    presentation.getTailText should include("undefined")
    val implemented = completions.find(_.getLookupString == "customer exists").getOrElse(fail("Missing Scala completion"))
    implemented.renderElement(presentation)
    presentation.getTypeText should include("Steps.scala")
    presentation.getTailText should include("matching scenarios")
  }

  @Test
  def testCustomParameterCompletionPreservesStringAndBraces(): Unit = {
    myFixture.addFileToProject("Types.scala", "class Types { ParameterType(\"account\", \"ACC-[0-9]+\") { (id: String) => id } }")
    myFixture.configureByText("Steps.scala", "class Steps { When(\"I transfer to {acc<caret>}\") { () => () } }")
    myFixture.completeBasic()
    if (myFixture.getLookup != null) {
      val item = myFixture.getLookupElements.find(_.getLookupString == "account").getOrElse(fail("Missing account parameter"))
      myFixture.getLookup.setCurrentItem(item)
      myFixture.finishLookup('\n')
    }
    myFixture.getFile.getText shouldBe "class Steps { When(\"I transfer to {account}\") { () => () } }"
  }

  @Test
  def testFeatureWordingCanBeDisabled(): Unit = {
    val settings = getProject.getService(classOf[ScalaCucumberSettings])
    settings.featureWording = false
    try {
      myFixture.addFileToProject("consent.feature", "Feature: Consent\n  Scenario: Consent\n    Given customer has consented\n")
      myFixture.configureByText("author.feature", "Feature: Author\n  Scenario: Author\n    Given customer<caret>\n")
      Option(myFixture.completeBasic()).toSeq.flatten.exists(_.getLookupString == "customer has consented") shouldBe false
    } finally settings.featureWording = true
  }
}
