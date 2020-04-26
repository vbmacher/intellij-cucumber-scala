package com.github.danielwegener.intellij.cucumber.scala.presentation

import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition.LOG
import com.intellij.navigation.{ItemPresentation, ItemPresentationProvider}
import org.jetbrains.annotations.Nullable

class StepDefinitionPresentationProvider extends ItemPresentationProvider[ScStepDefinition] {

  @Nullable
  override def getPresentation(item: ScStepDefinition): ItemPresentation = {
    ScStepDefinitionPresentation(item)
  }
}