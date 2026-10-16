package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.{GherkinExamplesBlock, GherkinTable, GherkinTableRow}

class ExampleRunLineMarker extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info = element.getParent match {
    case row: GherkinTableRow if element == row.getFirstChild && row.getParent.isInstanceOf[GherkinTable] &&
      row.getParent.getParent.isInstanceOf[GherkinExamplesBlock] && row.getParent.asInstanceOf[GherkinTable].getHeaderRow != row =>
      new RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0), new java.util.function.Function[PsiElement, String] { override def apply(element: PsiElement): String = "Run or debug this example row" })
    case _ => null
  }
}
