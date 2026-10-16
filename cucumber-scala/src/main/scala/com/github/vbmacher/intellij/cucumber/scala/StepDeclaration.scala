package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.pom.{PomDeclarationSearcher, PomRenameableTarget, PomTarget, PomTargetPsiElement}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Consumer, Processor}
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

case class StepDeclaration(element: PsiElement, pattern: String) extends PomRenameableTarget[StepDeclaration] {
  override def getName: String = asCall(element.getParent).flatMap(_.pattern).getOrElse(pattern)

  override def isWritable: Boolean = element.isWritable

  override def setName(name: String): StepDeclaration = {
    ScStepRename.setDefinition(this, name)
    copy(pattern = name)
  }

  override def isValid: Boolean = element.isValid

  override def navigate(requestFocus: Boolean): Unit =
    if (canNavigate) Option(EditSourceUtil.getDescriptor(element)).foreach(_.navigate(requestFocus))

  override def canNavigate: Boolean = !DumbService.isDumb(element.getProject) && EditSourceUtil.canNavigate(element)

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
    if (!DumbService.isDumb(element.getProject)) {
      val host = Option(InjectedLanguageManager.getInstance(element.getProject).getInjectionHost(element)).getOrElse(element)
      (host.getParent match {
        case pattern @ (_: ScLiteral | _: ScInfixExpr) =>
          for (call <- callOfPattern(pattern); (_, p) <- call.definition) yield StepDeclaration(call.getFirstChild, p)
        case _ => None
      }).foreach(consumer.consume)
    }
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
