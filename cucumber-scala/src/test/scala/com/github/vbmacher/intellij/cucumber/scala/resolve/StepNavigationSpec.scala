package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepNavigationSpec extends ScCucumberSpecBase {

  private def assertTargets(text: String, expected: (String, String)*): Unit = {
    myFixture.configureByText("navigation.feature", s"Feature: Navigation\n  Scenario: Resolve\n    Given $text\n")
    inReadAction {
      val step = myFixture.findElementByText(text, classOf[GherkinStep])
      val reference = step.getReferences.collectFirst { case r: CucumberStepReference => r }
        .getOrElse(fail("Expected a Cucumber step reference"))
      reference.getRangeInElement.substring(step.getText) shouldBe text
      val targets = reference.multiResolve(false).map(_.getElement)
        .map(e => e.getContainingFile.getName -> e.getText).toSeq
      targets should contain theSameElementsAs expected
    }
  }

  @Test
  def testUnrelatedWhenDoesNotResolve(): Unit = {
    myFixture.addFileToProject("Unrelated.scala",
      "package unrelated\nclass Unrelated { def When(s: String)(f: => Unit): Unit = (); When(\"review\") { () } }")
    assertTargets("review")
  }

  @Test
  def testUnrelatedWhenDoesNotMakeARealStepAmbiguous(): Unit = {
    myFixture.addFileToProject("Unrelated.scala",
      "package unrelated\nclass Unrelated { def When(s: String)(f: => Unit): Unit = (); When(\"review\") { () } }")
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"review\") { () => () } }")
    assertTargets("review", "Steps.scala" -> "When(\"review\")")
  }
}
