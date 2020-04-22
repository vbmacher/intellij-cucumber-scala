package com.github.danielwegener.intellij.cucumber.scala.search

import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.github.danielwegener.intellij.cucumber.scala.{ScCucumberUtil, inReadAction}
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.util.Try

class StepDefinitionSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters] {

  override def processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[_ >: PsiReference]): Unit = {
    Try {
      val element = getStepDefinition(queryParameters.getElementToSearch)
      val stepName = element.flatMap(ScCucumberUtil.getStepName)

      if (element.nonEmpty && stepName.nonEmpty) {
        CucumberUtil.findGherkinReferencesToElement(
          element.get, stepName.get, consumer, queryParameters.getEffectiveSearchScope
        )
      }
    }
  }

  def getStepDefinition(element: PsiElement): Option[ScMethodCall] = element match {
    case method: ScMethodCall if ScCucumberUtil.isStepDefinition(method) => Some(method)
    case p: PomTargetPsiElement if p.getTarget.isInstanceOf[ScStepDefinition] =>
      val target = p.getTarget.asInstanceOf[ScStepDefinition]
      Option(inReadAction(target.getElement)).map(_.asInstanceOf[ScMethodCall])
    case _ => None
  }
}
