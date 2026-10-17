package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.moduleOf
import com.intellij.execution.actions.{ConfigurationContext, RunConfigurationProducer}
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi._

class ScCucumberRunProducer extends RunConfigurationProducer[ScCucumberRunConfiguration](classOf[ScCucumberConfigurationType]) {
  private[scala] def selection(element: PsiElement): Option[(GherkinFile, Int, PsiElement)] = {
    Option(element).flatMap(e => Option(e.getContainingFile)).collect { case f: GherkinFile => f }.map { file =>
      val row = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinTableRow], false))
        .filter(ScCucumberRunProducer.isExampleRow)
      val holder = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinStepsHolder], false))
        .filterNot { case s: GherkinScenario => s.isBackground; case _ => false }
      val target = row.orElse(holder).getOrElse(file)
      val line = if (target == file) 0 else file.getText.take(ScenarioSelection.sourceOffset(target)).count(_ == '\n') + 1
      (file, line, target)
    }
  }
  override def setupConfigurationFromContext(configuration: ScCucumberRunConfiguration, context: ConfigurationContext, source: Ref[PsiElement]): Boolean = {
    selection(source.get()).exists { case (file, line, target) =>
      moduleOf(file).exists { module =>
        configuration.setModule(module)
        configuration.featureUrl = file.getVirtualFile.getUrl
        configuration.setLine(line)
        configuration.setName(configuration.suggestedName())
        source.set(target)
        true
      }
    }
  }
  override def isConfigurationFromContext(configuration: ScCucumberRunConfiguration, context: ConfigurationContext): Boolean =
    Option(context.getPsiLocation).flatMap(selection).exists { case (file, line, _) =>
      configuration.featureUrl == file.getVirtualFile.getUrl && configuration.line == line && Option(configuration.getConfigurationModule.getModule) == moduleOf(file)
    }
}

object ScCucumberRunProducer {
  def isExampleRow(row: GherkinTableRow): Boolean = row.getParent match {
    case table: GherkinTable => table.getParent.isInstanceOf[GherkinExamplesBlock] && table.getHeaderRow != row
    case _ => false
  }
}

class ExampleRunLineMarker extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info = element.getParent match {
    case row: GherkinTableRow if element == row.getFirstChild && ScCucumberRunProducer.isExampleRow(row) =>
      new RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0),
        new java.util.function.Function[PsiElement, String] { override def apply(element: PsiElement): String = "Run or debug this example row" })
    case _ => null
  }
}
