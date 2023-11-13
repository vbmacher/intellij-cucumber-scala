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
  private val knownParamTypes = parameterTypeByName
    .get(paramRegistry)
    .asInstanceOf[util.Map[String, ParameterType[_]]]
    .asScala
    .toSeq
    .filter(_._1.nonEmpty)

  private val knownParamsReplacements = {
    val replacements = knownParamTypes.map {
      case (name, paramType) =>
        s"\\{$name\\}" -> paramType.getRegexps.asScala.foldLeft("") {
          case ("", r) => Matcher.quoteReplacement(r)
          case (acc, r) => s"$acc|${Matcher.quoteReplacement(r)}"
        }
    }.map { case (k, v) => s"(?<!\\\\)$k" -> v }

    replacements ++ Seq(
      "\\\\\\{([^\\s]*)\\}" -> "\\\\{$1\\\\}", // escaping \{}
      "\\\\\\(([^\\s]*)\\)" -> "\\\\($1\\\\)" // escaping \()
    )
  }.map {
    case (k, v) => k -> s"($v)"
  }

  private val parenthesisReplacements = {
    val optAltReplacements = Seq(
      "\\(([^\\s]*)\\)" -> "(?:$1)?", // Optional text
      "([^\\s]+)/([^\\s]+)" -> "(?:$1|$2)" // Alternative text
    )
    optAltReplacements.map { case (k, v) => s"(?<!\\\\)$k" -> s"($v)" }
  }

  // Groups of replacements of parameters with their regexps (e.g. "{int}" => (-?\d+))
  // Each group is applied as the whole. Only after one group is fully applied, another group can continue.
  private val paramReplacementSets: Seq[Seq[(Pattern, String)]] = {
    Seq(parenthesisReplacements, knownParamsReplacements).map(_.map {
      case (k, v) => Pattern.compile(k) -> v
    })
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
    // Replace known parameter names with their regexp
    val knownReplaced = paramReplacementSets.foldLeft(regex) {
      case (acc, replacements) =>
        replacements.foldLeft(acc) {
          case (acc, (pattern, replacement)) =>
            pattern.matcher(acc).replaceAll(replacement)
        }
    }

    // Replace custom parameters with their regexps
    val paramTypes = CustomParameterType.findAll(scMethod)
    val replaced = paramTypes.foldLeft(knownReplaced) {
      case (acc, CustomParameterType(name, regex)) =>
        val key = s"\\{$name\\}"
        acc.replaceAll(key, s"($regex)")
    }

    val rs = if (replaced.startsWith("^")) replaced else s"^$replaced"
    if (rs.endsWith("$")) rs else s"$rs$$"
  }
}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}
