package com.github.vbmacher.intellij.cucumber.scala.refactoring

import com.github.vbmacher.intellij.cucumber.scala._
import com.github.vbmacher.intellij.cucumber.scala.resolve.StepResolveSpecBase
import com.intellij.pom.references.PomService
import com.intellij.refactoring.rename.RenameProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepRenameSpec extends StepResolveSpecBase {
  @Test
  def testNativeRenamePreservesArgumentsAndKeywords(): Unit = {
    val definition = myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer {string} exists\") { (name: String) => () } }")
    val feature = myFixture.configureByText("customers.feature",
      "Feature: Customers\n  Scenario: Customers\n    Given customer \"Alice\" exists\n    And customer \"Bob\" exists\n")
    val declaration = ScCucumber.inReadAction {
      StepDeclaration(findAllMatchingDefinitions("customer \"Alice\" exists").head.getElement, "customer {string} exists")
    }
    val target = PomService.convertToPsi(getProject, declaration)
    new RenameProcessor(getProject, target, "registered customer {string} exists", false, false).run()
    definition.getText should include("Given(\"registered customer {string} exists\")")
    feature.getText should include("Given registered customer \"Alice\" exists")
    feature.getText should include("And registered customer \"Bob\" exists")
  }

  @Test
  def testAmbiguousOrChangedParametersDoNotProduceAPlan(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"account {int} exists\") { (id: Int) => () }; Given(\"account {word} exists\") { (id: String) => () } }")
    myFixture.configureByText("accounts.feature", "Feature: Accounts\n  Scenario: Accounts\n    Given account 42 exists\n")
    ScCucumber.inReadAction {
      val definition = findAllMatchingDefinitions("account 42 exists").head
      val declaration = StepDeclaration(definition.getElement, definition.getExpression)
      val step = myFixture.findElementByText("Given account 42 exists", classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep])
      ScStepRename.plan(declaration, "new " + declaration.getName, Seq(step)).isLeft shouldBe true
      ScStepRename.plan(declaration, "account {string} exists", Seq.empty).isLeft shouldBe true
    }
  }
}
