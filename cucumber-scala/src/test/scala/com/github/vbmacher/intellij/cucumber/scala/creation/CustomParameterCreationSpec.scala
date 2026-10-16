package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.ScStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class CustomParameterCreationSpec extends StepCreationSpecBase {
  private val quotes = "\"\"\""

  @Test
  def testDomainParameterFromSeparateGlue(): Unit = {
    myFixture.addFileToProject("accounts/Types.scala",
      """package accounts
        |case class Account(id: String)
        |class Types {
        |  ParameterType("account", "ACC-[0-9]+") { (id: String) => new Account(id) }
        |}
        |""".stripMargin)
    val destination = loadTestCase("creation/StepDefinitions.scala")(0)
    val source = myFixture.configureByText("account.feature",
      "Feature: Accounts\n  Scenario: Existing\n    Given acc<caret>ount ACC-42 exists\n")
    checkStepCreation(source, destination,
      s"""import io.cucumber.scala.ScalaDsl
         |
         |class StepDefinitions extends ScalaDsl {
         |
         |  Given(${quotes}account {account} exists$quotes) { (account: _root_.accounts.Account) =>
         |    // Write code here that turns the phrase above into concrete actions
         |    throw new io.cucumber.scala.PendingException()
         |  }
         |}""".stripMargin)
  }

  @Test
  def testAmbiguousParameterTypesRequireChoiceAndDoNotEdit(): Unit = {
    myFixture.addFileToProject("Types.scala",
      """case class Account(id: String)
        |case class Reference(id: String)
        |class Types {
        |  ParameterType("account", "ACC-[0-9]+") { (id: String) => new Account(id) }
        |  ParameterType("reference", "ACC-[0-9]+") { (id: String) => new Reference(id) }
        |}
        |""".stripMargin)
    val destination = loadTestCase("creation/StepDefinitions.scala")(0)
    myFixture.configureByText("account.feature", "Feature: Accounts\n  Scenario: Existing\n    Given account ACC-42 exists\n")
    val original = destination.getText
    inReadAction {
      val step = myFixture.findElementByText("Given account ACC-42 exists", classOf[GherkinStep])
      val choices = ScStepDefinitionCreator.snippetsFor(step)
      choices.size shouldBe 2
      choices.exists(_.contains("{account}")) shouldBe true
      choices.exists(_.contains("{reference}")) shouldBe true
      new ScStepDefinitionCreator().createStepDefinition(step, destination, withTemplate = false) shouldBe false
      destination.getText shouldBe original
    }
  }
}
