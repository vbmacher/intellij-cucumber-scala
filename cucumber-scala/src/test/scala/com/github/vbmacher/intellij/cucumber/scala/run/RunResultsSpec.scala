package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala._
import com.google.gson.JsonParser
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnit4])
class RunResultsSpec extends ScCucumberSpecBase {
  private def envelopes = java.nio.file.Files.readAllLines(java.nio.file.Path.of(getTestDataPath, "results", "outline-messages.ndjson"))

  @Test
  def testRealRuntimeMessagesKeepRowsAndRerunSelectorsDistinct(): Unit = {
    val emitted = ArrayBuffer.empty[String]
    val steps = ArrayBuffer.empty[CucumberMessages.StepResult]
    val scenarios = ArrayBuffer.empty[CucumberMessages.ScenarioResult]
    val bridge = new CucumberMessages("run-one", emitted += _, steps += _, scenarios += _)
    envelopes.forEach(bridge.consume)
    scenarios.size shouldBe 2
    scenarios.map(_.line).toSet shouldBe Set(11, 12)
    steps.size shouldBe 4
    steps.map(_.line).toSet shouldBe Set(6, 7)
    steps.map(_.selector).distinct.size shouldBe 2
    scenarios.foreach(_.status shouldBe "PASSED")
    emitted.count(_.contains("testStarted ")) shouldBe 6
    emitted.filter(_.contains("testStarted ")).foreach { line =>
      line should include("nodeId='run-one:")
      line should include("parentNodeId='run-one:")
      line should include("metainfo='")
    }
    emitted.exists(_.contains("left=5, right=6, result=11")) shouldBe true
  }

  @Test
  def testInterleavedExecutionsAndInterruptedAttempt(): Unit = {
    import scala.jdk.CollectionConverters._
    val emitted = ArrayBuffer.empty[String]
    val scenarios = ArrayBuffer.empty[CucumberMessages.ScenarioResult]
    val bridge = new CucumberMessages("concurrent", emitted += _, _ => (), scenarios += _)
    val messages = envelopes.asScala.map(s => JsonParser.parseString(s).getAsJsonObject)
    messages.filterNot(e => Seq("testCaseStarted", "testStepStarted", "testStepFinished", "testCaseFinished", "testRunFinished").exists(e.has)).foreach(e => bridge.consume(e.toString))
    messages.filter(_.has("testCaseStarted")).foreach(e => bridge.consume(e.toString))
    val firstSteps = messages.filter(_.has("testStepStarted")).groupBy(_.getAsJsonObject("testStepStarted").get("testCaseStartedId").getAsString).values.map(_.head).toSeq
    firstSteps.foreach(e => bridge.consume(e.toString))
    bridge.close(interrupted = true)
    scenarios.size shouldBe 2
    scenarios.foreach(_.status shouldBe "INTERRUPTED")
    emitted.count(_.contains("message='Interrupted'")) shouldBe 2
    emitted.filter(_.contains("testStarted ")).distinct.size shouldBe 2
  }


}
