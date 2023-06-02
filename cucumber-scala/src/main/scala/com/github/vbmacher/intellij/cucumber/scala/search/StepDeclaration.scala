package com.github.vbmacher.intellij.cucumber.scala.search

import com.intellij.ide.util.EditSourceUtil
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.PsiElement

case class StepDeclaration(element: PsiElement, stepName: String) extends PomNamedTarget {
  def isValid: Boolean = element.isValid

  override def navigate(requestFocus: Boolean): Unit = {
    Option(EditSourceUtil.getDescriptor(element)).foreach(_.navigate(requestFocus))
  }

  override def canNavigate: Boolean = EditSourceUtil.canNavigate(element)

  override def canNavigateToSource: Boolean = canNavigate()

  def getName: String = stepName
}