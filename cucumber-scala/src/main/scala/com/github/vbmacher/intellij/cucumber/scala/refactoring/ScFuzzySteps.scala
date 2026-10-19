package com.github.vbmacher.intellij.cucumber.scala.refactoring

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScCucumberExtension, ScParameterTypes}
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.cucumber.cucumberexpressions.{CucumberExpressionGenerator, ExpressionFactory}
import org.jetbrains.plugins.cucumber.psi.GherkinStep

import scala.jdk.CollectionConverters._
import scala.util.Try

object ScFuzzySteps {
  final case class Suggestion(expression: String, wording: String, target: com.intellij.psi.PsiElement, distance: Int) {
    override def toString: String = expression + " · " + target.getContainingFile.getName
  }
  def suggestions(step: GherkinStep): Seq[Suggestion] = {
    moduleOf(step).toSeq.flatMap { module =>
      val registry = ScParameterTypes.registry(Some(module), snippetsOnly = true)
      val generated = new CucumberExpressionGenerator(registry).generateExpressions(step.getName).asScala.toSeq
      val factory = new ExpressionFactory(registry)
      new ScCucumberExtension().loadStepsFor(module).asScala.toSeq.filter(_.getElement != null).sortBy(d => (d.getExpression, d.getElement.getTextOffset)).take(1000).flatMap { definition =>
        ProgressManager.checkCanceled()
        val expression = definition.getExpression
        generated.flatMap { from =>
          for {
            old <- ScStepRename.parts(from.getSource).toOption.toSeq
            fresh <- ScStepRename.parts(expression).toOption.toSeq
            if old._2 == fresh._2 && expression.length < 512 && from.getSource.length < 512
            distance = editDistance(old._1.mkString("{}"), fresh._1.mkString("{}"))
            if distance > 0 && distance <= math.max(2, from.getSource.length / 5)
            args <- Try(Option(factory.createExpression(from.getSource).`match`(step.getName))).toOption.flatten.toSeq
            captures = args.asScala.toSeq.map(a => step.getName.substring(a.getGroup.getStart, a.getGroup.getEnd))
            wording = fresh._1.head + captures.zip(fresh._1.drop(1)).map { case (value, text) => value + text }.mkString
            if definition.matches(wording)
          } yield Suggestion(expression, wording, definition.getElement, distance)
        }
      }.distinctBy(s => (s.expression, s.wording)).sortBy(s => (s.distance, s.expression)).take(10)
    }
  }
  private def editDistance(left: String, right: String): Int = {
    var previous = (0 to right.length).toArray
    left.indices.foreach { i =>
      val current = new Array[Int](right.length + 1)
      current(0) = i + 1
      right.indices.foreach { j => current(j + 1) = math.min(math.min(current(j) + 1, previous(j + 1) + 1), previous(j) + (if (left(i) == right(j)) 0 else 1)) }
      previous = current
    }
    previous(right.length)
  }
}

class UseExistingStepIntention extends IntentionAction {
  override def getText: String = "Use existing Scala step wording…"
  override def getFamilyName: String = "Scala Cucumber"
  override def startInWriteAction(): Boolean = false
  private def step(editor: Editor, file: PsiFile): Option[GherkinStep] = Option(file.findElementAt(editor.getCaretModel.getOffset))
    .flatMap(e => Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinStep], false)))
  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    !com.intellij.openapi.project.DumbService.isDumb(project) && step(editor, file).exists(s => matchingSteps(s).isEmpty)
  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = step(editor, file).foreach { current =>
    val suggestions = ScFuzzySteps.suggestions(current)
    JBPopupFactory.getInstance().createPopupChooserBuilder(suggestions.asJava).setTitle("Similar wording · suggestions do not affect exact matching")
      .setItemChosenCallback((suggestion: ScFuzzySteps.Suggestion) => {
        if (current.isValid && Dialogs.confirm(project, "Replace Step Wording", current.getName + "\n→ " + suggestion.wording))
          WriteCommandAction.runWriteCommandAction(project, "Reuse Scala step wording", null, () => { current.setName(suggestion.wording); () }, file)
      }).createPopup().showInBestPositionFor(editor)
  }
}
