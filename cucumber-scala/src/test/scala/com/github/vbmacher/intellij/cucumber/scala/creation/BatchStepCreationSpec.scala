package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, ActionPlaces, AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, DialogWrapperPeer, DialogWrapperPeerFactory, Messages, TestDialogManager, TextFieldWithBrowseButton}
import com.intellij.openapi.ui.impl.{DialogWrapperPeerFactoryImpl, DialogWrapperPeerImpl}
import com.intellij.openapi.util.{Disposer, TextRange}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.{PsiTestUtil, ServiceContainerUtil}
import com.intellij.ui.UiInterceptors
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.cucumber.inspections.ui.CreateStepDefinitionFileDialog
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import javax.swing.{JComponent, JTextField}
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class BatchStepCreationSpec extends StepCreationSpecBase {
  private def generate(): Unit = {
    val context: DataContext = key => {
      if (CommonDataKeys.PROJECT.is(key)) getProject
      else if (CommonDataKeys.EDITOR.is(key)) myFixture.getEditor
      else if (CommonDataKeys.PSI_FILE.is(key)) myFixture.getFile
      else null
    }
    new BatchStepCreationAction().actionPerformed(
      AnActionEvent.createFromDataContext(ActionPlaces.ACTION_SEARCH, null, context))
  }

  private def enableModalDialogs(): Unit = {
    // The platform's headless peer reports every dialog as modeless. Keep the real
    // dialog and its validation, allowing showAndGet to reach the UI interceptor.
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication, classOf[DialogWrapperPeerFactory],
      new DialogWrapperPeerFactoryImpl {
        override def createPeer(wrapper: DialogWrapper, project: Project, canBeParent: Boolean,
                                modality: DialogWrapper.IdeModalityType): DialogWrapperPeer =
          new DialogWrapperPeerImpl(wrapper, project, canBeParent, modality) {
            override def isModal: Boolean = true
          }
      }, getTestRootDisposable)
    PsiTestUtil.addSourceRoot(getModule, myFixture.getTempDirFixture.findOrCreateDir("testSteps"), true)
  }

  @Test
  def testQuickFixDestinationReceivesDistinctMissingSteps(): Unit = {
    val destination = myFixture.addFileToProject("Steps.scala",
      "class Steps { When(\"I sign in\") { () => () } }")
    val file = myFixture.configureByText("batch.feature",
      "Feature: Login\n  Scenario: Login\n    Given account \"alice\" exists\n    And account \"bob\" exists\n    When I sign in\n    Then I save 42 documents\n      \"\"\"json\n      {}\n      \"\"\"\n").asInstanceOf[GherkinFile]
    val steps = ScCucumber.inReadAction(BatchStepCreation.missingSteps(file, None))
    steps.size shouldBe 2

    generate()

    val text = destination.getText
    text.sliding("account {string} exists".length).count(_ == "account {string} exists") shouldBe 1
    text.sliding("I sign in".length).count(_ == "I sign in") shouldBe 1
    text should include("(string: String) =>")
    text should include("I save {int} documents")
    text should include("(int1: Int, docString: String) =>")
    text should include("throw new io.cucumber.scala.PendingException()")
  }

  @Test
  def testSelectionLimitsGenerationThroughTheQuickFix(): Unit = {
    val destination = myFixture.addFileToProject("Steps.scala",
      "class Steps { Given(\"existing\") { () => () } }")
    val feature = myFixture.configureByText("batch.feature",
      "Feature: Select\n  Scenario: Select\n    Given first\n    When second\n").asInstanceOf[GherkinFile]
    val offset = feature.getText.indexOf("When second")
    myFixture.getEditor.getSelectionModel.setSelection(offset, offset + 11)
    val steps = ScCucumber.inReadAction(BatchStepCreation.missingSteps(feature, Some(new TextRange(offset, offset + 11))))
    steps.map(_.getName) shouldBe Seq("second")

    generate()

    destination.getText should include("When(\"\"\"second\"\"\")")
    destination.getText should not include "first"
  }

  @Test
  def testSharedNewFileDialogCreatesOneFileForAllMissingSteps(): Unit = {
    enableModalDialogs()
    myFixture.configureByText("batch.feature",
      "Feature: New file\n  Scenario: New file\n    Given first\n    When second\n")
    // The light fixture omits the standard Java file-header include.
    val templates = FileTemplateManager.getInstance(getProject)
    val patterns = templates.getAllPatterns.toSeq
    templates.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY,
      (patterns :+ new CustomFileTemplate("File Header", "java")).asJava)
    Disposer.register(getTestRootDisposable, () =>
      templates.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, patterns.asJava))
    val directory = FileUtil.createTempDirectory("scala-step-generation", null)
    var shown = false
    UiInterceptors.registerPossible(getTestRootDisposable, new UiInterceptors.UiInterceptor[CreateStepDefinitionFileDialog](classOf[CreateStepDefinitionFileDialog]) {
      override protected def doIntercept(dialog: CreateStepDefinitionFileDialog): Unit = {
        shown = true
        dialog.getTitle shouldBe "Create New Step Definition File"
        val name = dialog.getPreferredFocusedComponent.asInstanceOf[JTextField]
        name.getText shouldBe "StepDefinitions"
        name.setText("GeneratedSteps")
        UIUtil.findComponentOfType(name.getParent.asInstanceOf[JComponent], classOf[TextFieldWithBrowseButton]).setText(directory.getPath)
        dialog.performOKAction()
      }
    })
    try {
      generate()
      shown shouldBe true
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new java.io.File(directory, "GeneratedSteps.scala"))
      virtualFile should not be null
      val text = PsiManager.getInstance(getProject).findFile(virtualFile).getText
      text should include("class GeneratedSteps extends ScalaDsl with EN")
      text should include("Given(\"\"\"first\"\"\")")
      text should include("When(\"\"\"second\"\"\")")
      directory.list().toSeq shouldBe Seq("GeneratedSteps.scala")
    } finally {
      FileUtil.delete(directory)
    }
  }

  @Test
  def testCancellingSharedNewFileDialogLeavesTheFeatureUnchanged(): Unit = {
    enableModalDialogs()
    val feature = myFixture.configureByText("batch.feature",
      "Feature: Cancel\n  Scenario: Cancel\n    Given first\n    When second\n")
    val original = feature.getText
    var shown = false
    UiInterceptors.registerPossible(getTestRootDisposable, new UiInterceptors.UiInterceptor[CreateStepDefinitionFileDialog](classOf[CreateStepDefinitionFileDialog]) {
      override protected def doIntercept(dialog: CreateStepDefinitionFileDialog): Unit = {
        shown = true
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
      }
    })
    generate()
    shown shouldBe true
    feature.getText shouldBe original
    myFixture.getTempDirFixture.getFile("testSteps/StepDefinitions.scala") shouldBe null
  }

  @Test
  def testAmbiguousParameterTypesUseTheSingleStepChoice(): Unit = {
    myFixture.addFileToProject("Types.scala",
      """case class Account(id: String)
        |case class Reference(id: String)
        |class Types {
        |  ParameterType("account", "ACC-[0-9]+") { (id: String) => new Account(id) }
        |  ParameterType("reference", "ACC-[0-9]+") { (id: String) => new Reference(id) }
        |}
        |""".stripMargin)
    val original = "class Steps { Given(\"existing\") { () => () } }"
    val destination = myFixture.addFileToProject("Steps.scala", original)
    myFixture.configureByText("batch.feature",
      "Feature: Accounts\n  Scenario: Existing\n    Given account ACC-42 exists\n")

    // The shared Scala creator declines ambiguous mappings in unit-test mode.
    generate()

    destination.getText shouldBe original
  }

  @Test
  def testNoMissingStepsShowsInformationWithoutEditing(): Unit = {
    val original = "class Steps { Given(\"existing\") { () => () } }"
    val destination = myFixture.addFileToProject("Steps.scala", original)
    myFixture.configureByText("batch.feature", "Feature: Existing\n  Scenario: Existing\n    Given existing\n")
    var shown = false
    TestDialogManager.setTestDialog(message => {
      message shouldBe "No missing steps in the selected scope."
      shown = true
      Messages.OK
    }, getTestRootDisposable)

    generate()

    shown shouldBe true
    destination.getText shouldBe original
  }

  @Test
  def testActionBelongsToGenerateMenuOnly(): Unit = {
    val manager = ActionManager.getInstance()
    val action = manager.getAction("ScalaCucumber.CreateMissingSteps")
    action shouldBe a[BatchStepCreationAction]
    manager.getAction("GenerateGroup").asInstanceOf[ActionGroup].getChildren(null).toSeq should contain(action)
    manager.getAction("EditorPopupMenu").asInstanceOf[ActionGroup].getChildren(null).toSeq should not contain action
  }
}
