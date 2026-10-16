package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.execution.actions.{ConfigurationContext, RunConfigurationProducer}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi._

class ScCucumberRunProducer extends RunConfigurationProducer[ScCucumberRunConfiguration](classOf[ScCucumberConfigurationType]) {
  private[scala] def selection(element: PsiElement): Option[(GherkinFile, Int, PsiElement)] = {
    Option(element).flatMap(e => Option(e.getContainingFile)).collect { case f: GherkinFile => f }.map { file =>
      val holder = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinStepsHolder], false))
        .filterNot { case s: GherkinScenario => s.isBackground; case _ => false }
      val target = holder.getOrElse(file)
      val line = if (target == file) 0 else file.getText.take(ScenarioSelection.sourceOffset(target)).count(_ == '\n') + 1
      (file, line, target)
    }
  }
  override def setupConfigurationFromContext(configuration: ScCucumberRunConfiguration, context: ConfigurationContext, source: Ref[PsiElement]): Boolean = {
    selection(source.get()).exists { case (file, line, target) =>
      Option(ModuleUtilCore.findModuleForPsiElement(file)).exists { module =>
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
      configuration.featureUrl == file.getVirtualFile.getUrl && configuration.line == line && configuration.getConfigurationModule.getModule == ModuleUtilCore.findModuleForPsiElement(file)
    }
}
