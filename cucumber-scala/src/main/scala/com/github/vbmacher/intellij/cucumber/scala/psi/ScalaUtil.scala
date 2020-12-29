package com.github.vbmacher.intellij.cucumber.scala.psi

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiMember
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

object ScalaUtil {

  def getPackageName(member: PsiMember): Option[String] = {
    for {
      containingClass <- Option(member.getContainingClass)
      qualifiedName <- Option(containingClass.getQualifiedName)
    } yield StringUtil.getPackageName(qualifiedName)
  }

  def innerMethod(outerMethod: ScMethodCall): Option[ScMethodCall] = {
    Option(outerMethod.getEffectiveInvokedExpr).collect { case some: ScMethodCall => some }
  }
}
