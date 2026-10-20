package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumberStepIndex
import com.github.vbmacher.intellij.cucumber.scala.util.GherkinFileAction
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.cucumber.tagexpressions.TagExpressionParser
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment

import scala.jdk.CollectionConverters._
import scala.util.Try

object ScHooks {
  final case class Hook(kind: String, condition: Option[String], order: Option[Int], target: PsiElement, reason: String) {
    override def toString: String = s"$kind · order=${order.map(_.toString).getOrElse("unknown")} · ${condition.filter(_.nonEmpty).getOrElse("all scenarios / unknown if dynamic")} · ${target.getContainingFile.getName} · $reason"
  }

  def applicable(scenario: GherkinStepsHolder): Seq[Hook] = {
    val tags = Scenarios.inheritedTags(scenario).asJava
    moduleOf(scenario).toSeq.flatMap { module =>
      indexedCalls(scenario.getProject, stepSearchScope(module), ScCucumberStepIndex.Hooks).filter(_.isFromCucumber).flatMap { call =>
        val inner = asCall(call.getEffectiveInvokedExpr)
        val kind = inner.map(_.getEffectiveInvokedExpr.getText).getOrElse(call.getEffectiveInvokedExpr.getText)
        val args = inner.toSeq.flatMap(_.args.exprs)
        val values = args.map {
          case assignment: ScAssignment => assignment.rightExpression.flatMap(constantValue)
          case expression => constantValue(expression)
        }
        val condition = if (args.isEmpty || values.forall(_.exists(_.isInstanceOf[java.lang.Number]))) Some("")
          else values.collectFirst { case Some(value: String) => value }
        val order = if (args.isEmpty || values.forall(_.exists(_.isInstanceOf[String]))) Some(1000)
          else values.collectFirst { case Some(value: java.lang.Number) => value.intValue() }
        val matches = condition.flatMap(text => Try(TagExpressionParser.parse(text).evaluate(tags)).toOption)
        if (matches.contains(false)) None
        else Some(Hook(kind, condition, order, call.getFirstChild,
          if (matches.contains(true)) "tag expression matches inherited scenario tags" else "dynamic or invalid condition: applicability unknown"))
      }
    }.sortBy(h => (h.kind, h.order.getOrElse(Int.MaxValue), h.target.getContainingFile.getName, h.target.getTextOffset))
  }
}

class GoToHooksAction extends GherkinFileAction("Go to Scala Hooks…") {
  override def actionPerformed(event: AnActionEvent): Unit = {
    for {
      editor <- Option(event.getData(CommonDataKeys.EDITOR))
      file <- Option(event.getData(CommonDataKeys.PSI_FILE))
      leaf <- Option(file.findElementAt(editor.getCaretModel.getOffset))
      scenario <- Option(PsiTreeUtil.getParentOfType(leaf, classOf[GherkinStepsHolder], false))
    } {
      val hooks = ScHooks.applicable(scenario)
      JBPopupFactory.getInstance().createPopupChooserBuilder(hooks.asJava)
        .setTitle(if (hooks.isEmpty) "No applicable static hooks" else "Applicable hooks · dynamic conditions remain unknown")
        .setItemChosenCallback((hook: ScHooks.Hook) => {
          if (hook.target.isValid) new OpenFileDescriptor(file.getProject, hook.target.getContainingFile.getVirtualFile, hook.target.getTextOffset).navigate(true)
        }).createPopup().showInBestPositionFor(editor)
    }
  }
}
