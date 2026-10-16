package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala._
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep, GherkinTag}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class EditorFeaturesSpec extends ScCucumberSpecBase {
  @Test
  def testFuzzySuggestionsPreserveArgumentsWithoutChangingResolution(): Unit = {
    myFixture.addFileToProject("Accounts.scala", "class Accounts { Given(\"account {string} exists\") { (name: String) => () } }")
    myFixture.configureByText("accounts.feature", "Feature: Accounts\n  Scenario: Accounts\n    Given account \"Alice\" exist\n")
    ScCucumber.inReadAction {
      val step = myFixture.findElementByText("Given account \"Alice\" exist", classOf[GherkinStep])
      ScCucumber.matchingSteps(step) shouldBe empty
      val suggestions = ScFuzzySteps.suggestions(step)
      suggestions.map(_.wording) should contain("account \"Alice\" exists")
      step.getName shouldBe "account \"Alice\" exist"
      ScCucumber.matchingSteps(step) shouldBe empty
    }
  }


}
