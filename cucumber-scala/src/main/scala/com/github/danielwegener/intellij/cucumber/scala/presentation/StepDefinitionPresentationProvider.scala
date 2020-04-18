package com.github.danielwegener.intellij.cucumber.scala.presentation

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.navigation.{ItemPresentation, ItemPresentationProvider, ItemPresentationProviders}
import com.intellij.openapi.diagnostic.Logger
import javax.swing.Icon
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.CucumberBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMethodCallImpl

object LOGGER{
  val LOG = Logger.getInstance(classOf[StepDefinitionPresentationProvider])

}
class StepDefinitionPresentationProvider extends ItemPresentationProvider[ScMethodCallImpl] {
  import LOGGER.LOG

  @Nullable
  override def getPresentation(item: ScMethodCallImpl): ItemPresentation = {
    if (ScCucumberUtil.isStepDefinition(item)) {
      new ScMethodCallPresentation(item)
    } else {
      LOG.info("Cannot present: " + item)
      null
    }
  }
}

class ScStepDefinitionPresentation(item: ScStepDefinition) extends ItemPresentation {
  import LOGGER.LOG

  override def getPresentableText: String = {
    val method = item.getElement.asInstanceOf[ScMethodCall]
    ScCucumberUtil.getStepDefinitionExpr(method).zip(ScCucumberUtil.getStepName(method)).headOption match {
      case Some((expr, name)) =>
        LOG.info("EXPR = " + expr.getText)
        LOG.info("NAME = " + name)
        LOG.info("BUNDLE = " + CucumberBundle.message("step.definition.0.1", expr.getText, name))
        CucumberBundle.message("step.definition.0.1", expr.getText, name)
      case _ =>
        LOG.info("Unknown item/step name: " + ScCucumberUtil.getStepDefinitionExpr(method) + "; stepName=" + ScCucumberUtil.getStepName(method))
        null
    }
  }

  override def getLocationString: String = {
    val file = item.getElement.getContainingFile
    val presentation = ItemPresentationProviders.getItemPresentation(file)

    LOG.info("location string: " + presentation.getPresentableText)
    presentation.getPresentableText
  }

  override def getIcon(unused: Boolean): Icon = null
}

class ScMethodCallPresentation(item: ScMethodCallImpl) extends ItemPresentation {
  import LOGGER.LOG

  override def getPresentableText: String = {
    ScCucumberUtil.getStepDefinitionExpr(item).zip(ScCucumberUtil.getStepName(item)).headOption match {
      case Some((expr, name)) =>
        LOG.info("EXPR = " + expr.getText)
        LOG.info("NAME = " + name)
        LOG.info("BUNDLE = " + CucumberBundle.message("step.definition.0.1", expr.getText, name))
        CucumberBundle.message("step.definition.0.1", expr.getText, name)
      case _ =>
        LOG.info("Unknown item/step name: " + ScCucumberUtil.getStepDefinitionExpr(item) + "; stepName=" + ScCucumberUtil.getStepName(item))
        null
    }
  }

  override def getLocationString: String = {
    val file = item.getContainingFile
    val presentation = ItemPresentationProviders.getItemPresentation(file)

    LOG.info("location string: " + presentation.getPresentableText)
    presentation.getPresentableText
  }

  override def getIcon(unused: Boolean): Icon = null
}