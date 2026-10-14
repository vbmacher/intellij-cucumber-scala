package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.pom.{PomDeclarationSearcher, PomNamedTarget, PomTarget, PomTargetPsiElement}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Consumer, Processor}
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

case class StepDeclaration(element: PsiElement, pattern: String) extends PomNamedTarget {
  override def getName: String = pattern

  override def isValid: Boolean = element.isValid

  override def navigate(requestFocus: Boolean): Unit =
    Option(EditSourceUtil.getDescriptor(element)).foreach(_.navigate(requestFocus))

  override def canNavigate: Boolean = EditSourceUtil.canNavigate(element)

  override def canNavigateToSource: Boolean = canNavigate
}

object StepDeclaration {
  def of(element: PsiElement): Option[StepDeclaration] = element match {
    case target: PomTargetPsiElement => Option(target.getTarget).collect { case d: StepDeclaration => d }
    case _ => None
  }
}

class StepDeclarationSearcher extends PomDeclarationSearcher {

  override def findDeclarationsAt(element: PsiElement, offsetInElement: Int, consumer: Consumer[_ >: PomTarget]): Unit = {
    val host = Option(InjectedLanguageManager.getInstance(element.getProject).getInjectionHost(element)).getOrElse(element)
    (host.getParent match {
      case pattern @ (_: ScLiteral | _: ScInfixExpr) =>
        for (call <- callOfPattern(pattern); (_, p) <- call.definition) yield StepDeclaration(call.getFirstChild, p)
      case _ => None
    }).foreach(consumer.consume)
  }
}

class StepDefinitionSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters](true) {

  override def processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor[_ >: PsiReference]): Unit =
    StepDeclaration.of(parameters.getElementToSearch).foreach { declaration =>
      CucumberUtil.findGherkinReferencesToElement(declaration.element, declaration.pattern, consumer, parameters.getEffectiveSearchScope)
    }
}

class StepFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  override def canFindUsages(element: PsiElement): Boolean = StepDeclaration.of(element).nonEmpty

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler =
    new FindUsagesHandler(element) {}
}
