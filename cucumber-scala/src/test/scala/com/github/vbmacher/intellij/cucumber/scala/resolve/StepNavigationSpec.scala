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

  @Test
  def testCustomParameterInSeparateGlueClass(): Unit = {
    myFixture.addFileToProject("ParameterTypes.scala",
      "class ParameterTypes { ParameterType(\"weight\", \"[0-9]+kg\") { (s: String) => s } }")
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"weight {weight}\") { (s: String) => () } }")
    assertTargets("weight 10kg", "Steps.scala" -> "When(\"weight {weight}\")")
    assertTargets("weight 10")
  }

  @Test
  def testUnknownCustomParameterDoesNotResolve(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"weight {missing}\") { (s: String) => () } }")
    assertTargets("weight 10kg")
  }

  @Test
  def testCustomParameterInInheritedTrait(): Unit = {
    myFixture.addFileToProject("ParameterTypes.scala",
      "trait ParameterTypes { ParameterType(\"weight\", \"[0-9]+kg\") { (s: String) => s } }")
    myFixture.addFileToProject("Steps.scala", "class Steps extends ParameterTypes { When(\"weight {weight}\") { (s: String) => () } }")
    assertTargets("weight 10kg", "Steps.scala" -> "When(\"weight {weight}\")")
    assertTargets("weight 10")
  }

  @Test
  def testUnrelatedParameterTypeDoesNotRegister(): Unit = {
    myFixture.addFileToProject("ParameterTypes.scala",
      "package unrelated\nclass ParameterTypes { def ParameterType(n: String, r: String)(f: String => String): Unit = (); ParameterType(\"weight\", \"[0-9]+kg\") { (s: String) => s } }")
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"weight {weight}\") { (s: String) => () } }")
    assertTargets("weight 10kg")
  }

  @Test
  def testConsecutivePackageClausesResolve(): Unit = {
    myFixture.addFileToProject("Steps.scala", "package outer\npackage inner\nclass Steps { When(\"review\") { () => () } }")
    assertTargets("review", "Steps.scala" -> "When(\"review\")")
  }

  @Test
  def testNestedBracedPackagesResolve(): Unit = {
    myFixture.addFileToProject("Steps.scala", "package outer { package middle { package inner { class Steps { When(\"review\") { () => () } } } } }")
    assertTargets("review", "Steps.scala" -> "When(\"review\")")
  }

  @Test
  def testParameterTypesUnderNestedPackagesResolve(): Unit = {
    myFixture.addFileToProject("ParameterTypes.scala",
      "package outer\npackage inner\nclass ParameterTypes { ParameterType(\"weight\", \"[0-9]+kg\") { (s: String) => s } }")
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"weight {weight}\") { (s: String) => () } }")
    assertTargets("weight 10kg", "Steps.scala" -> "When(\"weight {weight}\")")
    assertTargets("weight 10")
  }

  @Test
  def testScientificAndSignedNumericParametersResolve(): Unit = {
    val types = Seq("float", "double", "bigdecimal")
    val steps = types.map(tpe => s"When(\"$tpe {$tpe}\") { (n: Double) => () }").mkString("; ")
    myFixture.addFileToProject("Steps.scala", s"class Steps { $steps }")
    for (tpe <- types; number <- Seq("1.2E3", "-1.2E-3", "+1.2", "1,234.5", ".5")) {
      assertTargets(s"$tpe $number", "Steps.scala" -> s"When(\"$tpe {$tpe}\")")
    }
  }

  @Test
  def testInvalidNumericParametersDoNotResolve(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { When(\"number {double}\") { (n: Double) => () } }")
    for (number <- Seq("1.2E", "1.2E+", "abc", "+")) assertTargets(s"number $number")
  }
}
