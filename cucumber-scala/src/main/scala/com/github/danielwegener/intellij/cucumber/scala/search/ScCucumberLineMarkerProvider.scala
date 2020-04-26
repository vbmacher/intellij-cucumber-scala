package com.github.danielwegener.intellij.cucumber.scala.search

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import icons.CucumberIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

class ScCucumberLineMarkerProvider extends LineMarkerProvider {

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (ScCucumberUtil.isStepDefinition(element)) {
      val anchor: PsiElement = PsiTreeUtil.getDeepestFirst(element)

      val presentableText = ScCucumberUtil
        .getCachedStepDefinition(element.asInstanceOf[ScMethodCall])
        .map(_.getPresentation.getPresentableText)

      presentableText.map(text => {
        new LineMarkerInfo[PsiElement](
          anchor, anchor.getTextRange, CucumberIcons.Cucumber, (_: PsiElement) => text,
          null, GutterIconRenderer.Alignment.RIGHT
        )
      }).orNull
    } else null
  }
}
