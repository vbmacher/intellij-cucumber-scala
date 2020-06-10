package com.github.danielwegener.intellij.cucumber.scala.steps

import java.lang.reflect.Type
import java.text.MessageFormat
import java.util

import io.cucumber.core.backend.Snippet

import collection.JavaConverters._

object ScStepSnippet extends Snippet {

  override def template(): MessageFormat = {
    new MessageFormat("""
      |
      |{0}("{1}") '{' ({3}) =>
      |  // {4}
      |'}'""".stripMargin)
  }

  override def tableHint(): String = ""

  override def arguments(argumentTypes: util.Map[String, Type]): String = {
    argumentTypes.asScala.map {
      case (argName, argType) => s"$argName: ${argType.getTypeName}"
    }.mkString(",")
  }

  override def escapePattern(pattern: String): String = {
    pattern.replace("\\", "\\\\").replace("\"", "\\\"")
  }
}
