package com.github.vbmacher.intellij.cucumber.scala.search

import com.intellij.ide.util.EditSourceUtil
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.PsiElement

case class StepDeclaration(element: PsiElement, stepName: String) extends PomNamedTarget {
  def isValid: Boolean = element.isValid

  def navigate(requestFocus: Boolean): Unit = {
    Option(EditSourceUtil.getDescriptor(element)).foreach(_.navigate(requestFocus))
  }

  def canNavigate: Boolean = EditSourceUtil.canNavigate(element)

  def canNavigateToSource: Boolean = canNavigate()

  def getName: String = stepName
}