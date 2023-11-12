package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.psi.CustomParameterType
import com.github.vbmacher.intellij.cucumber.scala.psi.StepDefinition.implicits._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import io.cucumber.cucumberexpressions.{ParameterType, ParameterTypeRegistry}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import java.util
import java.util.Locale
import java.util.regex.{Matcher, Pattern}
import scala.jdk.CollectionConverters._

class ScStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod.getFirstChild) {
  private val paramRegistry = new ParameterTypeRegistry(Locale.ENGLISH)
  private val parameterTypeByName = paramRegistry.getClass.getDeclaredField("parameterTypeByName")

  parameterTypeByName.setAccessible(true)

  // See: io.cucumber.cucumberexpressions.ParameterTypeRegistry
  private val knownParameterTypes = parameterTypeByName
    .get(paramRegistry)
    .asInstanceOf[util.Map[String, ParameterType[_]]]
    .asScala
    .toSeq
    .filter(_._1.nonEmpty)

  // Replacements of parameters with their regexps
  // e.g. "{int}" => (-?\d+)
  private val paramReplacements: Seq[(Pattern, String)] = {
    val knownReplacements = knownParameterTypes.map {
      case (name, paramType) =>
        s"\\{$name\\}" -> paramType.getRegexps.asScala.foldLeft("") {
          case ("", r) => Matcher.quoteReplacement(r)
          case (acc, r) => s"$acc|${Matcher.quoteReplacement(r)}"
        }
    }

    val escapeReplacements = Seq(
      "\\\\\\{([^\\s]*)\\}" -> "\\\\{$1\\\\}", // escaping \{}
      "\\\\\\(([^\\s]*)\\)" -> "\\\\($1\\\\)" // escaping \()
    )

    val preventEscapedMatchReplacements = knownReplacements
      .map { case (k, v) => s"(?<!\\\\)$k" -> v }

    (preventEscapedMatchReplacements ++ escapeReplacements).map {
      case (k, v) => Pattern.compile(k) -> v
    }
  }

  private val additionalOneTimeReplacements: Seq[(Pattern, String)] = {
    val optAltReplacements = Seq(
      "\\(([^\\s]*)\\)" -> "(?:$1)?", // Optional text
      "([^\\s]+)/([^\\s]+)" -> "(?:$1|$2)" // Alternative text
    )
    optAltReplacements
      .map { case (k, v) => s"(?<!\\\\)$k" -> v }
      .map {
        case (k, v) => Pattern.compile(k) -> v
      }
  }

  override def getVariableNames: util.List[String] = {
    scMethod.getArguments.map(_.getName()).asJava
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = {
    // Find the step definition text, with all parameters replaced with actual values
    scMethod.getRegex.map(replaceParametersWithRegex).orNull
  }

  private def replaceParametersWithRegex(regex: String): String = {
    val constantReplacement = additionalOneTimeReplacements.foldLeft(regex) {
      case (acc, (pattern, replacement)) =>
        pattern.matcher(acc).replaceAll(replacement)
    }

    // Replace known parameter names with their regexps
    val knownReplaced = paramReplacements.foldLeft(constantReplacement) {
      case (acc, (pattern, replacement)) =>
        pattern.matcher(acc).replaceAll(replacement)
    }

    // Replace custom parameters with their regexps
    val paramTypes = CustomParameterType.findAll(scMethod)
    paramTypes.foldLeft(knownReplaced) {
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
