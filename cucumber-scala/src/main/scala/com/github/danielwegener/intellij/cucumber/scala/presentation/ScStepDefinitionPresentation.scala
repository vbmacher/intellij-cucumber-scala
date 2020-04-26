package com.github.danielwegener.intellij.cucumber.scala.presentation

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.navigation.{ItemPresentation, ItemPresentationProviders}
import javax.swing.Icon
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

class ScStepDefinitionPresentation(item: ScStepDefinition) extends ItemPresentation {

  override def getPresentableText: String = {
    val method = item.getElement.asInstanceOf[ScMethodCall]
    ScCucumberUtil.getStepDefinitionExpr(method).zip(ScCucumberUtil.getStepName(method)).headOption match {
      case Some((expr, name)) => CucumberBundle.message("step.definition.0.1", expr.getText, name)
      case _ => null
    }
  }

  override def getLocationString: String = {
    val file = item.getElement.getContainingFile
    val presentation = ItemPresentationProviders.getItemPresentation(file)

    presentation.getPresentableText
  }

  override def getIcon(unused: Boolean): Icon = null
}

object ScStepDefinitionPresentation {
  def apply(item: ScStepDefinition): ScStepDefinitionPresentation = new ScStepDefinitionPresentation(item)
}