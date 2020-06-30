package com.github.vbmacher.intellij.cucumber.scala.search

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil._
import com.github.vbmacher.intellij.cucumber.scala.inReadAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.pom.{PomDeclarationSearcher, PomTarget}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScInfixExpr, ScMethodCall}

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
      arguments <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScArgumentExprList]))
      innerMethod <- Option(arguments.getParent)
      outerMethod <- Option(innerMethod.getParent)

      if isStepDefinition(outerMethod)

      stepName <- getStepName(outerMethod.asInstanceOf[ScMethodCall])
      stepDeclaration <- getStepDeclaration(innerMethod, stepName)
    } yield stepDeclaration
  }

  def getStepDeclaration(element: PsiElement, stepName: String): Option[StepDeclaration] = {
    Option(CachedValuesManager.getCachedValue(element, () => {
      CachedValueProvider.Result.create(StepDeclaration(element, stepName), element)
    }))
  }
}
