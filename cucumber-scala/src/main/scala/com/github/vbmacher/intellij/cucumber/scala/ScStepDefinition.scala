package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.{CucumberUtil, MapParameterTypeManager}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import java.util
import scala.jdk.CollectionConverters._

class ScStepDefinition(call: ScMethodCall) extends AbstractStepDefinition(call.getFirstChild) {

  override def getVariableNames: util.List[String] = call.parameters.map(_.getName).asJava

  override def getCucumberRegexFromElement(element: PsiElement): String = call.pattern.map(toRegex).orNull

  private def toRegex(pattern: String): String =
    if (CucumberUtil.isCucumberExpression(pattern)) {
      val parameterTypes = ScStepDefinition.BuiltInParameterTypes ++ call.customParameterTypes
      CucumberUtil.buildRegexpFromCucumberExpression(pattern, new MapParameterTypeManager(parameterTypes.asJava))
    } else {
      val prefixed = if (pattern.startsWith("^")) pattern else s"^$pattern"
      if (prefixed.endsWith("$")) prefixed else s"$prefixed$$"
    }
}

object ScStepDefinition {

  private val BuiltInParameterTypes: Map[String, String] = {
    val standard = CucumberUtil.STANDARD_PARAMETER_TYPES.asScala.toMap
    standard ++
      Seq("biginteger", "byte", "short", "long").map(_ -> standard("int")) ++
      Seq("bigdecimal", "double").map(_ -> standard("float"))
  }
}
