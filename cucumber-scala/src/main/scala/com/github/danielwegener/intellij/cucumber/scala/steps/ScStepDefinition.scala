package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.collection.JavaConverters._
import scala.util.Try

class ScStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod.getFirstChild) {

  override def getVariableNames: util.List[String] = {
    seqAsJavaList(ScCucumberUtil.getStepArguments(scMethod).map(_.getName()))
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = Try {
    scMethod match {
      case mc: ScMethodCall => ScCucumberUtil.getStepRegex(mc).orNull
      case _ => null
    }
  }.getOrElse(null)

}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}