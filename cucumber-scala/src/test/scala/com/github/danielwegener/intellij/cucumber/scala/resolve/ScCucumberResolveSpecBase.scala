package com.github.danielwegener.intellij.cucumber.scala.resolve

import com.github.danielwegener.intellij.cucumber.scala.{ScCucumberSpecBase, ScCucumberUtil, inReadAction}
import com.intellij.psi._
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.collection.JavaConverters._

abstract class ScCucumberResolveSpecBase extends ScCucumberSpecBase {

  val CARET_STR = "<caret>"

  def findOffsetBySignature(signature: String): Int = {
    findOffsetBySignature(signature, myFixture.getFile)
  }

  def findOffsetBySignature(signature: String, psiFile: PsiFile): Int = {
    val caretSignature = CARET_STR
    val caretOffset = signature.indexOf(caretSignature)
    assert(caretOffset >= 0)

    val nsignature = signature.substring(0, caretOffset) + signature.substring(caretOffset + caretSignature.length())
    val pos = PsiDocumentManager.getInstance(getProject).getDocument(psiFile).getText().indexOf(nsignature)
    assert(pos >= 0)
    pos + caretOffset
  }

  def findReferenceBySignature(signature: String): PsiReference = {
    val offset = findOffsetBySignature(signature)
    inReadAction(myFixture.getFile.findReferenceAt(offset))
  }

  def findPsiFileInTempDirBy(relPath: String): PsiFile = {
    val virtualFile = myFixture.getTempDirFixture.getFile(relPath)
    if (virtualFile != null && !virtualFile.isDirectory) PsiManager.getInstance(getProject).findFile(virtualFile) else null
  }

  def findGherkinStep(text: String): Option[GherkinStep] = {
    Option(myFixture.findElementByText(text, classOf[GherkinStep]))
  }

  def findDefinitions(text: String): Seq[AbstractStepDefinition] = {
    for {
      step <- Seq(myFixture.findElementByText(text, classOf[GherkinStep]))
      definitions <- step.findDefinitions().asScala
    } yield definitions
  }

  def checkReference(element: String, stepDefinitionName: String) = inReadAction {
    val result = getResolveResult(element)

    println("RESOLVED:" + result.mkString(","))
    var ok = stepDefinitionName == null
    for (rr <- result if !ok) {
      val resolvedElement = rr.getElement
      if (resolvedElement != null) {
        if (stepDefinitionName == null) {
          ok = false
        } else {
          val resolvedStepDefName = getStepDefinitionName(resolvedElement)
          if (resolvedStepDefName != null && resolvedStepDefName.equals(stepDefinitionName)) {
            ok = true
          }
        }
      }
    }
    assert(ok)
  }

  def getResolveResult(step: String): Array[ResolveResult] = {
    val reference = findReferenceBySignature(step)
    reference match {
      case reference1: PsiPolyVariantReference =>
        inReadAction(reference1.multiResolve(true))
      case _ => Array(new PsiElementResolveResult(reference.resolve()))
    }
  }

  def getStepDefinitionName(stepDefinition: PsiElement): String = {
    stepDefinition match {
      case m: ScMethodCall => ScCucumberUtil.getStepName(m).orNull
      case _ => null
    }
  }
}
