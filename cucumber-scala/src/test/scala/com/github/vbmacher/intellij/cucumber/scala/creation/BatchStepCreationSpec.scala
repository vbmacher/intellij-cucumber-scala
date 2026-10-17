package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class BatchStepCreationSpec extends StepCreationSpecBase {
  @Test
  def testEquivalentExpressionsAreCreatedOnceAndImplementedStepsSkipped(): Unit = {
    myFixture.addFileToProject("Existing.scala", "class Existing { When(\"I sign in\") { () => () } }")
    val destination = loadTestCase("creation/StepDefinitions.scala")(0).asInstanceOf[ScalaFile]
    val file = myFixture.configureByText("batch.feature",
      "Feature: Login\n  Scenario: Login\n    Given account \"alice\" exists\n    And account \"bob\" exists\n    When I sign in\n").asInstanceOf[GherkinFile]
    val choices = ScCucumber.inReadAction(BatchStepCreation.proposals(file, None))
    choices.size shouldBe 1
    val sources = choices.map(_.head)
    sources.head should include("account {string} exists")
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = BatchStepCreation.apply(destination, sources) shouldBe true
    })
    destination.getText.sliding("account {string} exists".length).count(_ == "account {string} exists") shouldBe 1
    destination.getText should not include "I sign in"
  }

  @Test
  def testSelectedStepsAndBodylessDestination(): Unit = {
    val destination = myFixture.addFileToProject("Steps.scala", "class Steps").asInstanceOf[ScalaFile]
    val feature = myFixture.configureByText("batch.feature",
      "Feature: Select\n  Scenario: Select\n    Given first\n    When second\n").asInstanceOf[GherkinFile]
    val offset = feature.getText.indexOf("When second")
    val proposals = ScCucumber.inReadAction(BatchStepCreation.proposals(feature, Some(new TextRange(offset, offset + 11))))
    proposals.size shouldBe 1
    proposals.head.head should include("second")
    WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = BatchStepCreation.apply(destination, proposals.map(_.head)) shouldBe false
    })
    destination.getText shouldBe "class Steps"
  }
}
