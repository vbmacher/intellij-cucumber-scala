package com.github.vbmacher.intellij.cucumber.scala.creation

import com.github.vbmacher.intellij.cucumber.scala.{ScCucumberSpecBase, ScStepDefinitionCreator}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepCreationDestinationSpec extends ScCucumberSpecBase {

  private def assertInsertionRejected(source: String): Unit = {
    val destination = myFixture.addFileToProject("Destination.scala", source)
    val feature = myFixture.configureByText("destination.feature", "Feature: Destination\n  Scenario: Insert\n    Given review\n")
    val step = PsiTreeUtil.findChildOfType(feature, classOf[GherkinStep])
    WriteCommandAction.runWriteCommandAction(getProject, new Computable[Unit] {
      override def compute(): Unit = {
        assert(!new ScStepDefinitionCreator().createStepDefinition(step, destination, withTemplate = false))
        PsiDocumentManager.getInstance(getProject).commitAllDocuments()
        destination.getText shouldBe source
      }
    })
  }

  @Test
  def testBodylessClassIsRejected(): Unit = assertInsertionRejected("class Destination")

  @Test
  def testBodylessTraitIsRejected(): Unit = assertInsertionRejected("trait Destination")

  @Test
  def testFileWithoutATypeIsRejected(): Unit = assertInsertionRejected("package destination\n")
}
