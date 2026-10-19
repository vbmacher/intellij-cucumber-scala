package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.{TestDialogManager, TestInputDialog}
import com.intellij.testFramework.PlatformTestUtil
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

  @Test
  def testUnknownSampleIsAskedOutsideTheWriteActionAndInserted(): Unit = {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable)
    myFixture.addFileToProject("CodeSteps.scala", "class CodeSteps { ParameterType(\"code\", \"[A-Z]{3}-[0-9]{4}\") { (c: String) => c }; Given(\"order {code} ships\") { (c: String) => () }; Given(\"order is empty\") { () => () } }")
    myFixture.configureByText("codes.feature", "Feature: Codes\n  Scenario: Codes\n    Given ord<caret>\n")
    var askedInWriteAction = Option.empty[Boolean]
    TestDialogManager.setTestInputDialog((_: String) => {
      askedInWriteAction = Some(ApplicationManager.getApplication.isWriteAccessAllowed)
      "order ABC-1234 ships"
    })
    try {
      myFixture.completeBasic()
      val item = myFixture.getLookupElements.find(_.getLookupString.contains("ships")).get
      myFixture.getLookup.setCurrentItem(item)
      myFixture.finishLookup('\n')
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      askedInWriteAction shouldBe Some(false)
      myFixture.getEditor.getDocument.getText should include("Given order ABC-1234 ships\n")
      TemplateManagerImpl.getTemplateState(myFixture.getEditor) should not be null
      myFixture.getEditor.getSelectionModel.getSelectedText shouldBe "ABC-1234"
      TemplateManagerImpl.getTemplateState(myFixture.getEditor).gotoEnd(false)
    } finally TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
  }
}
