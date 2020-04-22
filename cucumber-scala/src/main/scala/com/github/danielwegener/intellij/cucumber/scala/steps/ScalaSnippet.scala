package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util

import cucumber.runtime.snippets.Snippet
import collection.JavaConverters._

class ScalaSnippet extends Snippet {

  override def template(): String = {
    """
      |
      |{0}("{1}") '{' ({3}) =>
      |  // {4}
      |'}'""".stripMargin
  }

  override def tableHint(): String = ""

  override def arguments(argumentTypes: util.List[Class[_]]): String = {
    val sb = new StringBuilder()
    argumentTypes.asScala.zipWithIndex.foreach {
      case (arg, index) =>
        if (index > 0) sb.append(", ")
        sb.append(s"arg$index: ${getArgType(arg)}")
    }
    sb.toString()
  }

  override def namedGroupStart(): String = null

  override def namedGroupEnd(): String = null

  override def escapePattern(pattern: String): String = {
    pattern.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  private def getArgType(arg: Class[_]): String = arg.getSimpleName
}
