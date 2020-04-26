package com.github.danielwegener.intellij.cucumber.scala.search

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.pom.{PomDeclarationSearcher, PomTarget}
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager}
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}

class StepDeclarationSearcher extends PomDeclarationSearcher {
  private val LOG = Logger.getInstance(classOf[StepDeclarationSearcher])

  override def findDeclarationsAt(psiElement: PsiElement, offsetInElement: Int, consumer: Consumer[PomTarget]): Unit = {
    val host = InjectedLanguageManager.getInstance(psiElement.getProject).getInjectionHost(psiElement)
    val hostOrElement = Option(host).getOrElse(psiElement)

    val stepDefinition = hostOrElement.getParent match {
      case literal: ScLiteral => findStepDefinition(literal)
      case expr: ScInfixExpr => findStepDefinition(expr)
      case _ => None
    }

    stepDefinition.foreach(consumer.consume)
  }

  private def findStepDefinition(element: ScExpression): Option[ScStepDefinition] = {
    for {
      parent <- Option(element.getParent) //~literal
      pparent <- Option(parent.getParent) //(~literal)
      ppparent <- Option(pparent.getParent) //When(~literal)

      if ScCucumberUtil.isStepDefinition(ppparent)
      stepDefinition <- getCachedStepDefinition(ppparent.asInstanceOf[ScMethodCall])

    } yield stepDefinition
  }

  private def getCachedStepDefinition(statement: ScMethodCall): Option[ScStepDefinition] = {
    Option(CachedValuesManager.getCachedValue(statement, () => {
      val document = Option(statement.getContainingFile)
        .map(PsiDocumentManager.getInstance(statement.getProject).getDocument)

      document
        .map(d => CachedValueProvider.Result.create(ScStepDefinition(statement), d))
        .getOrElse(CachedValueProvider.Result.create(ScStepDefinition(statement)))
    }))
  }
}
