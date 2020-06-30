package com.github.vbmacher.intellij.cucumber.scala.search

import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement

class StepFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  override def canFindUsages(element: PsiElement): Boolean = {
    element match {
      case p: PomTargetPsiElement if p.getTarget.isInstanceOf[StepDeclaration] => true
      case _ => false
    }
  }

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    new FindUsagesHandler(element) {}
  }
}
