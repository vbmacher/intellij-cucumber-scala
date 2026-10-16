package com.github.vbmacher.intellij.cucumber.scala

import com.google.gson.{JsonObject, JsonParser}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try

object CucumberMessages {
  final case class StepResult(run: String, uri: String, line: Int, selector: String, status: String, durationMillis: Long, error: String)
  final case class ScenarioResult(run: String, uri: String, line: Int, name: String, attempt: Int, status: String, willRetry: Boolean)
  private final case class Ast(uri: String, line: Int, name: String, parent: Option[String], kind: String)
  private final case class Running(pickle: JsonObject, testCase: JsonObject, line: Int, parent: String, attempt: Int, statuses: mutable.ArrayBuffer[String])
}

/** Consumes Cucumber message envelopes, keeping interleaved executions separate by testCaseStartedId. */
class CucumberMessages(runId: String, emit: String => Unit, stepResult: CucumberMessages.StepResult => Unit,
                       scenarioResult: CucumberMessages.ScenarioResult => Unit, metadata: String => Unit = _ => ()) {
  import CucumberMessages._
  private val ast = mutable.Map.empty[String, Ast]
  private val pickles = mutable.Map.empty[String, JsonObject]
  private val testCases = mutable.Map.empty[String, JsonObject]
  private val hooks = mutable.Map.empty[String, String]
  private val running = mutable.Map.empty[String, Running]
  private val suites = mutable.LinkedHashMap.empty[String, String]
  private val activeSteps = mutable.Map.empty[String, (String, String)]
  private var finished = false

  private def text(value: JsonObject, key: String): String = Option(value.get(key)).filterNot(_.isJsonNull).map(_.getAsString).getOrElse("")
  private def number(value: JsonObject, key: String): Long = Try(text(value, key).toLong).getOrElse(0L)
  private def objectAt(value: JsonObject, key: String): JsonObject = Option(value.get(key)).filter(_.isJsonObject).map(_.getAsJsonObject).getOrElse(new JsonObject())
  private def objects(value: JsonObject, key: String): Seq[JsonObject] = Option(value.getAsJsonArray(key)).toSeq.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject)
  private def ids(value: JsonObject, key: String): Seq[String] = Option(value.getAsJsonArray(key)).toSeq.flatMap(_.asScala).map(_.getAsString)
  private def escape(value: String): String = value.replace("|", "||").replace("'", "|'").replace("\n", "|n").replace("\r", "|r").replace("[", "|[").replace("]", "|]")
  private def message(kind: String, fields: (String, String)*): Unit = emit("\n##teamcity[" + kind + fields.map { case (key, value) => s" $key='${escape(value)}'" }.mkString + "]\n")
  private def node(id: String): String = runId + ":" + id
  private def location(uri: String, line: Int): String = "scala-cucumber://" + uri + ":" + line
  private def suite(id: String, name: String, parent: Option[String], uri: String, line: Int): Unit = {
    if (!suites.contains(id)) {
      suites(id) = name
      message("testSuiteStarted", "name" -> name, "nodeId" -> node(id), "parentNodeId" -> parent.map(node).getOrElse("0"), "locationHint" -> location(uri, line))
    }
  }
  private def ensureParent(id: String): Unit = ast.get(id).foreach { value =>
    value.parent.foreach(ensureParent)
    suite(id, value.name, value.parent, value.uri, value.line)
  }

  def consume(line: String): Unit = {
    val envelope = JsonParser.parseString(line).getAsJsonObject
    if (envelope.has("meta")) {
      val meta = objectAt(envelope, "meta")
      metadata(Seq("implementation", "runtime", "os", "cpu").map { key =>
        val value = objectAt(meta, key)
        Seq(text(value, "name"), text(value, "version")).filter(_.nonEmpty).mkString(" ")
      }.filter(_.nonEmpty).mkString(" · "))
    }
    if (envelope.has("gherkinDocument")) {
      val document = objectAt(envelope, "gherkinDocument")
      val uri = text(document, "uri")
      val feature = objectAt(document, "feature")
      val featureId = "feature:" + uri
      ast(featureId) = Ast(uri, number(objectAt(feature, "location"), "line").toInt, text(feature, "name"), None, "feature")
      def children(parent: JsonObject, parentId: String): Unit = objects(parent, "children").foreach { child =>
        Seq("rule", "scenario", "background").filter(child.has).foreach { kind =>
          val value = objectAt(child, kind)
          val id = text(value, "id")
          ast(id) = Ast(uri, number(objectAt(value, "location"), "line").toInt, text(value, "name"), Some(parentId), kind)
          objects(value, "steps").foreach { step => ast(text(step, "id")) = Ast(uri, number(objectAt(step, "location"), "line").toInt, text(step, "text"), Some(id), "step") }
          objects(value, "examples").foreach { examples =>
            val headers = objects(objectAt(examples, "tableHeader"), "cells").map(text(_, "value"))
            objects(examples, "tableBody").foreach { row =>
              val values = objects(row, "cells").map(text(_, "value"))
              val name = headers.zip(values).map { case (key, v) => key + "=" + v }.mkString(", ")
              ast(text(row, "id")) = Ast(uri, number(objectAt(row, "location"), "line").toInt, name, Some(id), "row")
            }
          }
          if (kind == "rule") children(value, id)
        }
      }
      children(feature, featureId)
    }
    if (envelope.has("pickle")) { val value = objectAt(envelope, "pickle"); pickles(text(value, "id")) = value }
    if (envelope.has("testCase")) { val value = objectAt(envelope, "testCase"); testCases(text(value, "id")) = value }
    if (envelope.has("hook")) { val value = objectAt(envelope, "hook"); hooks(text(value, "id")) = text(value, "type") }
    if (envelope.has("testCaseStarted")) {
      val value = objectAt(envelope, "testCaseStarted")
      for (testCase <- testCases.get(text(value, "testCaseId")); pickle <- pickles.get(text(testCase, "pickleId"))) {
        val nodes = ids(pickle, "astNodeIds").flatMap(ast.get)
        val target = nodes.lastOption.getOrElse(Ast(text(pickle, "uri"), 1, text(pickle, "name"), None, "scenario"))
        val parent = target.parent
        parent.foreach(ensureParent)
        val id = text(value, "id")
        val attempt = number(value, "attempt").toInt
        val name = (if (target.kind == "row") target.name else text(pickle, "name")) + (if (attempt > 0) s" · retry $attempt" else "")
        suite(id, name, parent, text(pickle, "uri"), target.line)
        running(id) = Running(pickle, testCase, target.line, parent.getOrElse(""), attempt, mutable.ArrayBuffer.empty)
      }
    }
    if (envelope.has("testStepStarted")) {
      val value = objectAt(envelope, "testStepStarted")
      val startedId = text(value, "testCaseStartedId")
      running.get(startedId).foreach { execution =>
        val stepId = text(value, "testStepId")
        objects(execution.testCase, "testSteps").find(s => text(s, "id") == stepId).foreach { testStep =>
          val pickleStep = objects(execution.pickle, "steps").find(s => text(s, "id") == text(testStep, "pickleStepId"))
          val name = pickleStep.map(text(_, "text")).getOrElse(hooks.getOrElse(text(testStep, "hookId"), "Hook"))
          val line = pickleStep.toSeq.flatMap(ids(_, "astNodeIds")).flatMap(ast.get).find(_.kind == "step").map(_.line).getOrElse(execution.line)
          val id = startedId + ":" + stepId
          activeSteps(id) = name -> startedId
          message("testStarted", "name" -> name, "nodeId" -> node(id), "parentNodeId" -> node(startedId),
            "locationHint" -> location(text(execution.pickle, "uri"), line), "metainfo" -> (text(execution.pickle, "uri") + ":" + execution.line), "captureStandardOutput" -> "true")
        }
      }
    }
    if (envelope.has("testStepFinished")) {
      val value = objectAt(envelope, "testStepFinished")
      val startedId = text(value, "testCaseStartedId")
      val stepId = text(value, "testStepId")
      val id = startedId + ":" + stepId
      val result = objectAt(value, "testStepResult")
      val status = text(result, "status")
      val duration = objectAt(result, "duration")
      val millis = number(duration, "seconds") * 1000 + number(duration, "nanos") / 1000000
      val exception = objectAt(result, "exception")
      val error = Seq(text(result, "message"), text(exception, "message"), text(exception, "stackTrace")).filter(_.nonEmpty).distinct.mkString("\n")
      activeSteps.remove(id).foreach { case (name, _) =>
        if (Set("FAILED", "AMBIGUOUS", "UNDEFINED").contains(status)) message("testFailed", "name" -> name, "nodeId" -> node(id), "message" -> status, "details" -> error)
        else if (status != "PASSED") message("testIgnored", "name" -> name, "nodeId" -> node(id), "message" -> (status + " " + error))
        message("testFinished", "name" -> name, "nodeId" -> node(id), "duration" -> millis.toString)
      }
      running.get(startedId).foreach { execution =>
        execution.statuses += status
        val pickleStep = objects(execution.testCase, "testSteps").find(s => text(s, "id") == stepId)
          .flatMap(s => objects(execution.pickle, "steps").find(p => text(p, "id") == text(s, "pickleStepId")))
        pickleStep.foreach { step =>
          val line = ids(step, "astNodeIds").flatMap(ast.get).find(_.kind == "step").map(_.line).getOrElse(execution.line)
          val uri = text(execution.pickle, "uri")
          stepResult(StepResult(runId, uri, line, uri + ":" + execution.line, status, millis, error))
        }
      }
    }
    if (envelope.has("testCaseFinished")) {
      val value = objectAt(envelope, "testCaseFinished")
      val id = text(value, "testCaseStartedId")
      running.remove(id).foreach { execution =>
        val status = if (execution.statuses.exists(Set("FAILED", "AMBIGUOUS", "UNDEFINED"))) "FAILED"
          else if (execution.statuses.contains("PENDING")) "PENDING"
          else if (execution.statuses.contains("SKIPPED")) "SKIPPED" else "PASSED"
        scenarioResult(ScenarioResult(runId, text(execution.pickle, "uri"), execution.line, text(execution.pickle, "name"), execution.attempt, status, text(value, "willBeRetried") == "true"))
        suites.remove(id).foreach(name => message("testSuiteFinished", "name" -> name, "nodeId" -> node(id)))
      }
    }
    if (envelope.has("testRunFinished")) close(interrupted = false)
  }

  def close(interrupted: Boolean): Unit = if (!finished) {
    finished = true
    activeSteps.foreach { case (id, (name, _)) =>
      message("testIgnored", "name" -> name, "nodeId" -> node(id), "message" -> "Interrupted")
      message("testFinished", "name" -> name, "nodeId" -> node(id))
    }
    running.foreach { case (_, execution) => scenarioResult(ScenarioResult(runId, text(execution.pickle, "uri"), execution.line, text(execution.pickle, "name"), execution.attempt, "INTERRUPTED", false)) }
    suites.toSeq.reverse.foreach { case (id, name) => message("testSuiteFinished", "name" -> name, "nodeId" -> node(id)) }
    activeSteps.clear(); running.clear(); suites.clear()
  }
}
