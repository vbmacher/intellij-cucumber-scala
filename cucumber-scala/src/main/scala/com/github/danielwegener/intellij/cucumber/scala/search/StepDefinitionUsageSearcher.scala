package com.github.danielwegener.intellij.cucumber.scala.search

import com.github.danielwegener.intellij.cucumber.scala.inReadAction
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil

class StepDefinitionUsageSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters] {

  override def processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[_ >: PsiReference]): Unit = {
    queryParameters.getElementToSearch match {
      case pomTarget: PomTargetPsiElement => pomTarget.getTarget match {

        case declaration: StepDeclaration =>
          inReadAction {
            CucumberUtil.findGherkinReferencesToElement(
              declaration.element, declaration.stepName, consumer, queryParameters.getEffectiveSearchScope
            )
          }

        case _ =>
      }
      case _ =>
    }
  }
}
