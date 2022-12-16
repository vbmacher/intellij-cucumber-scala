package com.github.vbmacher.intellij.cucumber.scala

import io.cucumber.gherkin.GherkinDialectProvider

import scala.jdk.CollectionConverters._

object ScCucumberUtil {

  final val CUCUMBER_PACKAGES = Seq(
    "cucumber.api.scala", // 4.x
    "io.cucumber.scala"   // 5.x, 6.x
  )

  private lazy val allKeywords = {
    val provider = new GherkinDialectProvider()
    val languages = provider.getLanguages.asScala
    val dialects = languages.map(provider.getDialect)

    dialects.flatMap(d => Option(d.orElseGet(null)) match {
      case None => Seq.empty
      case Some(dialect) => dialect.getStepKeywords.asScala.map(_.trim)
    }).toSet
  }

  def isKeywordValid(keyword: String): Boolean = allKeywords.contains(keyword)
}
