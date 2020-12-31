package com.github.vbmacher.intellij.cucumber.scala.search

import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition
import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition.implicits._
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.CucumberIcons

class ScCucumberLineMarkerProvider extends LineMarkerProvider {

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = {
    (element match {

      case id: LeafPsiElement => // according to javadoc, we should relate to leaf elements due to performance reasons
        for {
          stepDefinition <- StepDefinition.fromLeafPsiElement(id)
          stepName <- stepDefinition.getName
        } yield {
          new LineMarkerInfo(
            element, element.getTextRange, CucumberIcons.Cucumber, (_: PsiElement) => stepName,
            null, GutterIconRenderer.Alignment.RIGHT, () => stepName
          )
        }
      case _ => None
    }).orNull
  }
}
