package com.github.danielwegener.intellij.cucumber.scala.resolve

import com.github.danielwegener.intellij.cucumber.scala.{ScCucumberSpecBase, ScCucumberUtil, inReadAction}
import com.intellij.psi._
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.scalatest.Assertion

import scala.collection.JavaConverters._

abstract class StepResolveSpecBase extends ScCucumberSpecBase {

  def findDefinitions(text: String): Seq[AbstractStepDefinition] = {
    for {
      step <- Seq(myFixture.findElementByText(text, classOf[GherkinStep]))
      definitions <- step.findDefinitions().asScala
    } yield definitions
  }

  def singleResolve(files: String*)(step: String): Assertion = {
    loadTestCase(files: _*)
    inReadAction {
      val definitions = findDefinitions(step)
      definitions.size shouldBe 1
    }
  }
}
