package com.github.vbmacher.intellij.cucumber.scala.refactoring

import com.github.vbmacher.intellij.cucumber.scala.resolve.StepResolveSpecBase
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScCucumber, StepDeclaration}
import com.intellij.pom.references.PomService
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
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
  def testNativeRenameRejectsUnsupportedPatternsWithoutEditingSources(): Unit = {
    Seq(
      "\"^account ([0-9]+)$\"" -> "account 42",
      "\"account(s) {int}\"" -> "accounts 42",
      "\"account/customer {int}\"" -> "customer 42",
      "\"\"\"account \\(primary) {int}\"\"\"" -> "account (primary) 42",
      "(\"account \" + \"{int}\")" -> "account 42"
    ).zipWithIndex.foreach { case ((expression, wording), index) =>
      val definition = myFixture.addFileToProject(s"Steps$index.scala", s"class Steps$index { Given($expression) { (id: Int) => () } }")
      val feature = myFixture.configureByText(s"unsupported$index.feature", s"Feature: Unsupported\n Scenario: Unsupported\n  Given $wording\n")
      val beforeDefinition = definition.getText
      val beforeFeature = feature.getText
      val declaration = ScCucumber.inReadAction {
        val matched = findAllMatchingDefinitions(wording).find(_.getElement.getContainingFile == definition).get
        StepDeclaration(matched.getElement, matched.getExpression)
      }
      intercept[ConflictsInTestsException] {
        new RenameProcessor(getProject, PomService.convertToPsi(getProject, declaration), "new " + declaration.getName, false, false).run()
      }
      definition.getText shouldBe beforeDefinition
      feature.getText shouldBe beforeFeature
    }
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

  @Test
  def testRenameMustRejectCollidingExpression(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer {int} exists\") { (id: Int) => () }; Given(\"registered customer {int} exists\") { (id: Int) => () } }")
    myFixture.configureByText("rename.feature", "Feature: Rename\n  Scenario: Customer\n    Given customer 42 exists\n")
    ScCucumber.inReadAction {
      val definition = findAllMatchingDefinitions("customer 42 exists").head
      val declaration = StepDeclaration(definition.getElement, "customer {int} exists")
      val step = myFixture.findElementByText("Given customer 42 exists", classOf[GherkinStep])
      ScStepRename.plan(declaration, "registered customer {int} exists", Seq(step)).isLeft shouldBe true
      ScStepRename.plan(declaration, "registered customer {int} exists", Seq.empty).isLeft shouldBe true
    }
  }

  @Test
  def testRenameMustRejectOverlappingExpression(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer {int} exists\") { (id: Int) => () }; Given(\"registered customer {word} exists\") { (id: String) => () } }")
    myFixture.configureByText("overlap.feature", "Feature: Rename\n  Scenario: Customer\n    Given customer 42 exists\n")
    ScCucumber.inReadAction {
      val definition = findAllMatchingDefinitions("customer 42 exists").head
      val declaration = StepDeclaration(definition.getElement, "customer {int} exists")
      val step = myFixture.findElementByText("Given customer 42 exists", classOf[GherkinStep])
      ScStepRename.plan(declaration, "registered customer {int} exists", Seq(step)).isLeft shouldBe true
    }
  }

  @Test
  def testNativeRenameMustNotIntroduceAmbiguity(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer {int} exists\") { (id: Int) => () }; Given(\"registered customer {int} exists\") { (id: Int) => () } }")
    myFixture.configureByText("native.feature", "Feature: Rename\n  Scenario: Customer\n    Given customer 42 exists\n")
    val declaration = ScCucumber.inReadAction {
      val definition = findAllMatchingDefinitions("customer 42 exists").head
      StepDeclaration(definition.getElement, "customer {int} exists")
    }
    val error = intercept[ConflictsInTestsException] {
      new RenameProcessor(getProject, PomService.convertToPsi(getProject, declaration), "registered customer {int} exists", false, false).run()
    }
    error.getMessage should include("conflicts with another step definition")
    ScCucumber.inReadAction {
      findAllMatchingDefinitions("customer 42 exists").size shouldBe 1
      findAllMatchingDefinitions("registered customer 42 exists").size shouldBe 1
      myFixture.getFile.getText should include("Given customer 42 exists")
    }
  }

  @Test
  def testRenameMustRejectInvalidCucumberExpression(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"customer {int} exists\") { (id: Int) => () } }")
    myFixture.configureByText("invalid.feature", "Feature: Invalid\n  Scenario: Customer\n    Given customer 42 exists\n")
    ScCucumber.inReadAction {
      val definition = findAllMatchingDefinitions("customer 42 exists").head
      val declaration = StepDeclaration(definition.getElement, "customer {int} exists")
      val step = myFixture.findElementByText("Given customer 42 exists", classOf[GherkinStep])
      Seq("customer {int} exists {", "customer {int} exists {missing").foreach { bad =>
        ScStepRename.plan(declaration, bad, Seq(step)).isLeft shouldBe true
      }
    }
  }

}
