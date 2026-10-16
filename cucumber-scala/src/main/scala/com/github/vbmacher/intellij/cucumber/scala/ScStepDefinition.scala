package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.psi.PsiElement
import io.cucumber.cucumberexpressions.{ExpressionFactory, ParameterTypeRegistry}
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.{CucumberUtil, MapParameterTypeManager}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import java.util
import java.util.Locale
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

// Instances live in the loadStepsFor cache, which is dropped on any Scala PSI change
class ScStepDefinition(call: ScMethodCall, customParameterTypes: Map[String, String]) extends AbstractStepDefinition(call.getFirstChild) {

  private val regex = call.pattern.map(ScStepDefinition.toRegex(_, customParameterTypes)).orNull

  private lazy val compiled = super.getPattern

  override def getPattern: Pattern = compiled

  override def getVariableNames: util.List[String] =
    Option(getElement).flatMap(e => asCall(e.getParent)).map(_.parameters.map(_.getName)).getOrElse(Seq.empty).asJava

  override def getCucumberRegex: String = regex

  override def getExpression: String = call.pattern.orNull

  override def setValue(value: String): Unit = ScStepRename.setDefinition(StepDeclaration(getElement, getExpression), value)

  override def getCucumberRegexFromElement(element: PsiElement): String = regex
}

object ScStepDefinition {

  private def toRegex(pattern: String, customParameterTypes: Map[String, String]): String =
    if (CucumberUtil.isCucumberExpression(pattern)) {
      val parameterTypes = BuiltInParameterTypes ++ customParameterTypes
      CucumberUtil.buildRegexpFromCucumberExpression(pattern, new MapParameterTypeManager(parameterTypes.asJava))
    } else {
      val regexp = if (pattern.length >= 2 && pattern.startsWith("/") && pattern.endsWith("/"))
        pattern.substring(1, pattern.length - 1)
      else pattern
      val prefixed = if (regexp.startsWith("^")) regexp else s"^$regexp"
      if (prefixed.endsWith("$")) prefixed else s"$prefixed$$"
    }

  private val BuiltInParameterTypes: Map[String, String] = {
    val standard = CucumberUtil.STANDARD_PARAMETER_TYPES.asScala.toMap
    val factory = new ExpressionFactory(new ParameterTypeRegistry(Locale.ENGLISH))
    val numeric = Seq("float", "double", "bigdecimal").map { name =>
      // A single parameter compiles as ^(regex)$; Gherkin adds the capture group itself.
      name -> factory.createExpression(s"{$name}").getRegexp.pattern.stripPrefix("^(").stripSuffix(")$")
    }
    standard ++
      Seq("biginteger", "byte", "short", "long").map(_ -> standard("int")) ++
      numeric
  }
}
