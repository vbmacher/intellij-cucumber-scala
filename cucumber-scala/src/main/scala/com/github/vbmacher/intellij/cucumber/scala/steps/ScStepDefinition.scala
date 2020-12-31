package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.psi.CustomParameterType
import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition.implicits._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import java.util
import scala.jdk.CollectionConverters._

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
    "\\{string\\}" -> "(.*)",
    "\\(([^\\s]*)\\)" -> "(?:$1)?", // Optional text
    "([^\\s]*)/([^\\s]*)" -> "(?:$1|$2)" // Alternative text
  ).view.mapValues("(" + _ + ")")

  override def getVariableNames: util.List[String] = {
    scMethod.getArguments.map(_.getName()).asJava
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = {
    scMethod.getRegex.map(replaceParametersWithRegex).orNull
  }

  private def replaceParametersWithRegex(regex: String): String = {
    val constantReplacement = paramRegexp.foldLeft(regex) {
      case (acc, (key, replacement)) => acc.replaceAll(key, replacement)
    }

    val paramTypes = CustomParameterType.findAll(scMethod)
    paramTypes.foldLeft(constantReplacement) {
      case (acc, CustomParameterType(name, regex)) =>
        val key = s"\\{$name\\}"
        acc.replaceAll(key, s"($regex)")
    }
  }
}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}
