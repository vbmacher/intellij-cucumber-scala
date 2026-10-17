package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.CucumberIcons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

import java.util
import scala.jdk.CollectionConverters._

class ScCucumberLineMarkerProvider extends LineMarkerProvider {
  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = null

  override def collectSlowLineMarkers(elements: util.List[_ <: PsiElement], result: util.Collection[_ >: LineMarkerInfo[_]]): Unit = {
    elements.asScala.foreach {
      case leaf: LeafPsiElement if !DumbService.isDumb(leaf.getProject) && leaf.getElementType == ScalaTokenTypes.tIDENTIFIER && isStepKeyword(leaf.getText) =>
        for (call <- callOfKeyword(leaf); (keyword, pattern) <- call.definition) {
          val scenarios = StepUsage.scenarios(call.getFirstChild)
          val name = s"$keyword $pattern · ${scenarios.size} matching scenarios (includes ambiguous matches; outlines count once)"
          val marker = NavigationGutterIconBuilder.create(CucumberIcons.Cucumber)
            .setTargets(scenarios.map(s => s: PsiElement).asJava)
            .setTooltipText(name).setPopupTitle("Matching scenarios · module scope")
            .setEmptyPopupText("No matching scenarios in this module's dependency scope; external consumers are unknown")
            .createLineMarkerInfo(leaf)
          result.add(marker)
        }
      case _ =>
    }
  }
}
