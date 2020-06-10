package com.github.danielwegener.intellij.cucumber.scala.creation

import com.github.danielwegener.intellij.cucumber.scala._
import com.github.danielwegener.intellij.cucumber.scala.resolve.StepResolveSpecBase
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinitionCreator
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep

abstract class StepCreationSpecBase extends StepResolveSpecBase {

  def checkStepCreation(feature: PsiFile, stepDef: PsiFile, expectedStepDef: String) = {
    myFixture.enableInspections(classOf[CucumberStepInspection])

    val project = myFixture.getProject
    val documentManager = PsiDocumentManager.getInstance(project)

    WriteCommandAction.runWriteCommandAction(project, new Computable[Unit] {
      override def compute(): Unit = {
        documentManager.commitAllDocuments()
        val at = feature.findElementAt(myFixture.getEditor.getCaretModel.getOffset)
        val step = PsiTreeUtil.getParentOfType(at, classOf[GherkinStep])
        new ScStepDefinitionCreator().createStepDefinition(step, stepDef, withTemplate = false)
        documentManager.commitAllDocuments()
      }
    })

    inReadAction {
      assert(expectedStepDef === stepDef.getText)
    }
  }
}
