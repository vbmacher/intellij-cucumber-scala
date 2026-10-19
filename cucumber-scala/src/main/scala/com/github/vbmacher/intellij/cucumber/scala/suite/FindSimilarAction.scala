package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.util.GherkinFileAction
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.{GherkinFeature, GherkinFile, GherkinStep, GherkinStepsHolder, GherkinTokenTypes}

class FindSimilarAction extends GherkinFileAction("Find similar...") {
  override def update(event: AnActionEvent): Unit =
    event.getPresentation.setEnabledAndVisible(event.getProject != null && searchText(event).nonEmpty)

  private[suite] def searchText(event: AnActionEvent): Option[String] = for {
    file <- Option(event.getData(CommonDataKeys.PSI_FILE)).collect { case file: GherkinFile => file }
    editor <- Option(event.getData(CommonDataKeys.EDITOR))
    text <- Option(editor.getSelectionModel.getSelectedText).filter(_.trim.nonEmpty).orElse {
      val offset = editor.getCaretModel.getOffset
      Iterator.iterate(file.findElementAt(offset))(_.getParent).takeWhile(_ != null).collectFirst {
        case step: GherkinStep if onHeader(step, offset) => step.getName
        case scenario: GherkinStepsHolder if onHeader(scenario, offset) => scenario.getScenarioName
        case feature: GherkinFeature if onHeader(feature, offset) => feature.getFeatureName
      }
    }
    if text.trim.nonEmpty
  } yield text.trim

  private def onHeader(element: PsiElement, offset: Int): Boolean =
    Option(element.getNode.findChildByType(GherkinTokenTypes.KEYWORDS)).exists { keyword =>
      val start = keyword.getStartOffset
      val end = element.getContainingFile.getText.indexOf('\n', start)
      offset >= start && (end < 0 || offset <= end)
    }

  override def actionPerformed(event: AnActionEvent): Unit = for {
    project <- Option(event.getProject)
    text <- searchText(event)
    window <- Option(ToolWindowManager.getInstance(project).getToolWindow("Scala Cucumber"))
  } window.activate(() => {
    window.getContentManager.getContents.find(_.getComponent.isInstanceOf[ScenarioSearchPanel]).foreach { content =>
      window.getContentManager.setSelectedContent(content)
      content.getComponent.asInstanceOf[ScenarioSearchPanel].findSimilar(text)
    }
  })
}
