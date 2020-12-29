package com.github.vbmacher.intellij.cucumber.scala.search

import com.github.vbmacher.intellij.cucumber.scala.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition
import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition.implicits._
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.pom.{PomDeclarationSearcher, PomTarget}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}

class StepDeclarationSearcher extends PomDeclarationSearcher {

  override def findDeclarationsAt(psiElement: PsiElement, offsetInElement: Int, consumer: Consumer[PomTarget]): Unit = {
    val injectionHost = InjectedLanguageManager.getInstance(psiElement.getProject).getInjectionHost(psiElement)
    val injectionHostOrElement = Option(injectionHost).getOrElse(psiElement)

    ProgressManager.checkCanceled()
    val stepDeclaration = inReadAction {
      injectionHostOrElement.getParent match {
        case literal: ScLiteral => findStepDeclaration(literal)
        case expr: ScInfixExpr => findStepDeclaration(expr)
        case _ => None
      }
    }
    stepDeclaration.foreach(consumer.consume)
  }

  private def findStepDeclaration(element: ScExpression): Option[StepDeclaration] = {
    for {
      stepDefinition <- StepDefinition.fromScExpression(element)
      stepName <- stepDefinition.getName
      stepDeclaration <- getStepDeclaration(stepDefinition.getFirstChild, stepName)
    } yield stepDeclaration
  }

  def getStepDeclaration(element: PsiElement, stepName: String): Option[StepDeclaration] = {
    Option(CachedValuesManager.getCachedValue(element, () => {
      CachedValueProvider.Result.create(StepDeclaration(element, stepName), element)
    }))
  }
}
