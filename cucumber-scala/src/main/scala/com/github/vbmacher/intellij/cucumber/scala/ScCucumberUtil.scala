package com.github.vbmacher.intellij.cucumber.scala

import io.cucumber.cucumberexpressions.ParameterTypeRegistry
import io.cucumber.gherkin.GherkinDialects

import java.util.Locale
import scala.jdk.CollectionConverters._

object ScCucumberUtil {

  final val CUCUMBER_PACKAGES = Seq(
    "cucumber.api.scala", // 4.x
    "io.cucumber.scala" // 5.x and forwards
  )

  final lazy val PARAMETER_TYPE_REGISTRY = new ParameterTypeRegistry(Locale.getDefault())

  // All keywords in all languages
  final lazy val ALL_STEP_KEYWORDS: Set[String] = {
    GherkinDialects.getDialects
      .asScala
      .flatMap(_.getStepKeywords.asScala)
      .map(_.trim)
      .toSet
  }

  def isKeywordValid(keyword: String): Boolean = ALL_STEP_KEYWORDS.contains(keyword)
}
