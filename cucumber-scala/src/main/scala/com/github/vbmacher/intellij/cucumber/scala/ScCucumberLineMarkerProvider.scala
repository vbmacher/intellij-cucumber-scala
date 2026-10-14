package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.CucumberIcons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

class ScCucumberLineMarkerProvider extends LineMarkerProvider {

  // Only leaf elements, for performance (see javadoc)
  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = (element match {
    case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER && isStepKeyword(leaf.getText) =>
      callOfKeyword(leaf).flatMap(_.definition).map { case (keyword, pattern) =>
        val name = s"$keyword $pattern"
        new LineMarkerInfo[PsiElement](
          element, element.getTextRange, CucumberIcons.Cucumber, (_: PsiElement) => name,
          null, GutterIconRenderer.Alignment.RIGHT, () => name
        )
      }
    case _ => None
  }).orNull
}
