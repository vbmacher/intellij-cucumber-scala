package com.github.vbmacher.intellij.cucumber.scala.search

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil._
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.CucumberIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

class ScCucumberLineMarkerProvider extends LineMarkerProvider {

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = {
    val result = element match {

      case id: LeafPsiElement => // according to javadoc, we should relate to leaf elements due to performance reasons
        for {
          expr <- Option(id.getParent)
          if expr.isInstanceOf[ScReferenceExpression]

          inner <- Option(expr.getParent)
          outer <- Option(inner.getParent)

          if isStepDefinition(outer)

          stepName <- getStepName(outer.asInstanceOf[ScMethodCall])
        } yield {
          new LineMarkerInfo[PsiElement](
            element, element.getTextRange, CucumberIcons.Cucumber, (_: PsiElement) => stepName,
            null, GutterIconRenderer.Alignment.RIGHT
          )
        }
      case _ => None
    }

    result.orNull
  }
}
