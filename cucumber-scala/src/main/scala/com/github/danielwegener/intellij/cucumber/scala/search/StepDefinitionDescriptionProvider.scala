package com.github.danielwegener.intellij.cucumber.scala.search

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.{ElementDescriptionLocation, ElementDescriptionProvider, PsiElement}
import com.intellij.usageView.{UsageViewLongNameLocation, UsageViewNodeTextLocation, UsageViewTypeLocation}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

class StepDefinitionDescriptionProvider extends ElementDescriptionProvider {
  private val stepDefinitionTitle = CucumberBundle.message("step.definition")

  @Nullable
  override def getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String = {
    location match {
      case _: UsageViewNodeTextLocation => getStepDefinitionTitle(element)
      case _: UsageViewTypeLocation => getStepDefinitionTitle(element)
      case _: UsageViewLongNameLocation => getStepDefinitionTitle(element)
      case _ => null
    }
  }

  private def getStepDefinitionTitle(element: PsiElement) = element match {

    case method: ScMethodCall if ScCucumberUtil.isStepDefinition(method) =>
      stepDefinitionTitle

    case expr: ScReferenceExpression => Option(expr.getParent) match {
      case Some(parentMethod: ScMethodCall) if ScCucumberUtil.isStepDefinition(parentMethod) =>
        stepDefinitionTitle
      case _ => null
    }

    case pomTarget: PomTargetPsiElement if pomTarget.getTarget.isInstanceOf[ScStepDefinition] =>
      stepDefinitionTitle

    case _ => null
  }
}
