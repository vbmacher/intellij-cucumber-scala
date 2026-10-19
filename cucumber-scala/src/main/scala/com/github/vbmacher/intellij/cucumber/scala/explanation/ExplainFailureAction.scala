package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.util.GherkinFileAction
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.ui.{Messages, popup}
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class ExplainFailureAction extends GherkinFileAction("Explain Cucumber Failure…") {
  override def update(event: AnActionEvent): Unit = {
    super.update(event)
    event.getPresentation.setEnabledAndVisible(event.getPresentation.isVisible &&
      Option(event.getProject).exists(p => ScalaCucumberSettings.getInstance(p).failureExplanations))
  }

  override def actionPerformed(event: AnActionEvent): Unit = {
    for (project <- Option(event.getProject); editor <- Option(event.getData(CommonDataKeys.EDITOR));
         file <- Option(event.getData(CommonDataKeys.PSI_FILE)).collect { case f: GherkinFile => f }) {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      val choices = inReadAction(FailureContext.candidates(file, editor.getCaretModel.getOffset))
      if (choices.isEmpty) Messages.showInfoMessage(project,
        "No recorded failed result for this selection. Run the scenario with failure explanations enabled, then try again.", "Explain Cucumber Failure")
      else if (choices.size == 1) ExplainFailureAction.open(file, choices.head)
      else {
        final case class Choice(value: RunResults.Decoration) {
          override def toString: String = s"${value.result.status} · ${value.result.selector} · run ${value.result.run} · ${java.time.Instant.ofEpochMilli(value.timestamp)}"
        }
        import scala.jdk.CollectionConverters._
        popup.JBPopupFactory.getInstance().createPopupChooserBuilder(choices.map(Choice).asJava)
          .setTitle("Choose the failed run / example to explain")
          .setItemChosenCallback((choice: Choice) => ExplainFailureAction.open(file, choice.value))
          .createPopup().showInBestPositionFor(event.getDataContext)
      }
    }
  }
}

object ExplainFailureAction {
  def open(file: GherkinFile, result: RunResults.Decoration): Unit = {
    val project = file.getProject
    new Task.Backgroundable(project, "Collect Cucumber failure context", true) {
      private var context: Either[String, FailureContext.Snapshot] = Left("Context was not collected.")
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.checkCanceled()
        context = ReadAction.nonBlocking[Either[String, FailureContext.Snapshot]](() => FailureContext.collect(file, result))
          .inSmartMode(project).expireWith(project).executeSynchronously()
        indicator.checkCanceled()
      }
      override def onSuccess(): Unit = if (!project.isDisposed) context match {
        case Left(message) => Messages.showInfoMessage(project, message, "Explain Cucumber Failure")
        case Right(value) => new FailureExplanationDialog(project, value).show()
      }
      override def onThrowable(error: Throwable): Unit = if (!project.isDisposed)
        Messages.showErrorDialog(project, "Could not collect the failure context. Run the scenario again and retry.", "Explain Cucumber Failure")
    }.queue()
  }
}
