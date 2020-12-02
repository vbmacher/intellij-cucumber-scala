package com.github.vbmacher.intellij.cucumber.scala.steps

import java.util

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.jdk.CollectionConverters._
import scala.util.Try

class ScStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod.getFirstChild) {
  private val INTEGER_REGEXP = "-?\\\\d+"
  private val FLOAT_REGEXP = "[+-]?(\\\\d+\\\\.)?\\\\d+"
  private val WORD_REGEXP = "\\\\w"

  // See: io.cucumber.cucumberexpressions.ParameterTypeRegistry
  private val paramRegexp = Map(
    "\\{biginteger\\}" -> INTEGER_REGEXP,
    "\\{int\\}" -> INTEGER_REGEXP,
    "\\{byte\\}" -> INTEGER_REGEXP,
    "\\{short\\}" -> INTEGER_REGEXP,
    "\\{long\\}" -> INTEGER_REGEXP,
    "\\{bigdecimal\\}" -> FLOAT_REGEXP,
    "\\{float\\}" -> FLOAT_REGEXP,
    "\\{double\\}" -> FLOAT_REGEXP,
    "\\{word\\}" -> WORD_REGEXP,
    "\\{string\\}" -> "(.*)"
  ).view.mapValues("(" + _ + ")")

  override def getVariableNames: util.List[String] = {
    ScCucumberUtil.getStepArguments(scMethod).map(_.getName()).asJava
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = Try {
    scMethod match {
      case mc: ScMethodCall => ScCucumberUtil.getStepRegex(mc).map(replaceParametersWithRegex).orNull
      case _ => null
    }
  }.getOrElse(null)

  def replaceParametersWithRegex(regex: String): String = {
    paramRegexp.foldLeft(regex) {
      case (acc, (key, replacement)) => acc.replaceAll(key, replacement)
    }
  }
}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}