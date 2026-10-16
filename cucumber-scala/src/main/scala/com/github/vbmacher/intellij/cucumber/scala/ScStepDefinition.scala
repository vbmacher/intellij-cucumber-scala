package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.{CucumberUtil, MapParameterTypeManager}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import java.util
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

// Instances live in the loadStepsFor cache, which is dropped on any Scala PSI change
class ScStepDefinition(call: ScMethodCall, customParameterTypes: Map[String, String]) extends AbstractStepDefinition(call.getFirstChild) {

  private val regex = call.pattern.map(ScStepDefinition.toRegex(_, customParameterTypes)).orNull

  private lazy val compiled = super.getPattern

  override def getPattern: Pattern = compiled

  override def getVariableNames: util.List[String] =
    Option(getElement).flatMap(e => asCall(e.getParent)).map(_.parameters.map(_.getName)).getOrElse(Seq.empty).asJava

  override def getCucumberRegexFromElement(element: PsiElement): String = regex
}

object ScStepDefinition {

  private def toRegex(pattern: String, customParameterTypes: Map[String, String]): String =
    if (CucumberUtil.isCucumberExpression(pattern)) {
      val parameterTypes = BuiltInParameterTypes ++ customParameterTypes
      CucumberUtil.buildRegexpFromCucumberExpression(pattern, new MapParameterTypeManager(parameterTypes.asJava))
    } else {
      val prefixed = if (pattern.startsWith("^")) pattern else s"^$pattern"
      if (prefixed.endsWith("$")) prefixed else s"$prefixed$$"
    }

  private val BuiltInParameterTypes: Map[String, String] = {
    val standard = CucumberUtil.STANDARD_PARAMETER_TYPES.asScala.toMap
    standard ++
      Seq("biginteger", "byte", "short", "long").map(_ -> standard("int")) ++
      Seq("bigdecimal", "double").map(_ -> standard("float"))
  }
}
