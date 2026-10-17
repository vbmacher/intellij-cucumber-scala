package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepTemplateSpec extends ScCucumberSpecBase {
  @Test
  def testCompletionInsertsValidQuotedEditableArguments(): Unit = {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable)
    myFixture.addFileToProject("TemplateSteps.scala", "class TemplateSteps { Given(\"account {string} has {int} credits\") { (name: String, credits: Int) => () }; Given(\"account is empty\") { () => () } }")
    myFixture.configureByText("templates.feature", "Feature: Templates\n  Scenario: Templates\n    Given acc<caret>\n")
    myFixture.completeBasic()
    val item = myFixture.getLookupElements.find(_.getLookupString.contains("has")).get
    myFixture.getLookup.setCurrentItem(item)
    myFixture.finishLookup('\n')
    myFixture.getEditor.getDocument.getText should include("account \"value\" has 1 credits")
    val state = TemplateManagerImpl.getTemplateState(myFixture.getEditor)
    state should not be null
    myFixture.getEditor.getSelectionModel.getSelectedText shouldBe "\"value\""
    state.nextTab()
    myFixture.getEditor.getSelectionModel.getSelectedText shouldBe "1"
    state.gotoEnd(false)
  }
}
