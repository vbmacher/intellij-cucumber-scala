package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.CucumberIcons

class ScCucumberLineMarkerProvider extends LineMarkerProvider {

  // Only leaf elements, for performance (see javadoc)
  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = (element match {
    case leaf: LeafPsiElement =>
      stepDefinitionOfKeyword(leaf).flatMap(_.name).map { name =>
        new LineMarkerInfo[PsiElement](
          element, element.getTextRange, CucumberIcons.Cucumber, (_: PsiElement) => name,
          null, GutterIconRenderer.Alignment.RIGHT, () => name
        )
      }
    case _ => None
  }).orNull
}
