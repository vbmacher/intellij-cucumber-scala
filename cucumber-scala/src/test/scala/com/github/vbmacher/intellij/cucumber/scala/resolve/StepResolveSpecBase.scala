package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.{ScCucumberExtension, ScCucumberSpecBase}
import org.jetbrains.plugins.cucumber.{CucumberJvmExtensionPoint, CucumberUtil}
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.{AbstractStepDefinition, CucumberStepHelper}
import org.scalatest.Assertion

import scala.collection.JavaConverters._

abstract class StepResolveSpecBase extends ScCucumberSpecBase {

  def findStepDefinitions(text: String): Seq[AbstractStepDefinition] = {
    val step = myFixture.findElementByText(text, classOf[GherkinStep])
    CucumberStepHelper.findStepDefinitions(myFixture.getFile, step).asScala.toSeq
  }

  def findAllMatchingDefinitions(text: String): Seq[AbstractStepDefinition] = {
    val extension = CucumberJvmExtensionPoint.EP_NAME.getExtensionList().get(0).asInstanceOf[ScCucumberExtension]
    val allSteps = extension.loadStepsFor(myFixture.getFile, myFixture.getModule).asScala
    allSteps.filter(_.matches(text))
  }

  def singleResolve(files: String*)(step: String): Assertion = {
    multiResolve(1, files: _*)(step)
  }

  def multiResolve(expectedCount: Int, files: String*)(step: String): Assertion = {
    loadTestCase(files: _*)
    inReadAction {
      val definitions = findAllMatchingDefinitions(step)
      definitions.size shouldBe expectedCount
    }
  }
}
