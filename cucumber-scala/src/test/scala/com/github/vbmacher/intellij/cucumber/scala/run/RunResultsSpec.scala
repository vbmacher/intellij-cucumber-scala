package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
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

  @Test
  def testResultStalenessHistoryRetentionRetriesAndClear(): Unit = {
    val file = myFixture.configureByText("results.feature", "Feature: Results\n  Scenario: Result\n    Given example\n")
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.runHistory = true
    settings.historyRetention = 3
    val service = RunResults.getInstance(getProject)
    val context = RunResults.Context(com.intellij.psi.util.PsiModificationTracker.getInstance(getProject).getModificationCount, "revision", "environment", Map.empty, 123L)
    service.record(CucumberMessages.StepResult("one", file.getVirtualFile.getUrl, 3, "selection", "FAILED", 4, "assertion"), context)
    val step = myFixture.findElementByText("Given example", classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep])
    service.at(step).size shouldBe 1
    service.stale(service.at(step).head) shouldBe false
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = myFixture.getEditor.getDocument.insertString(myFixture.getEditor.getDocument.getTextLength, "# change\n")
    })
    com.intellij.psi.PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    service.stale(service.at(step).head) shouldBe true
    Seq(("FAILED", 0), ("PASSED", 1), ("PASSED", 0), ("FAILED", 0)).zipWithIndex.foreach { case ((status, attempt), i) =>
      service.record(CucumberMessages.ScenarioResult(i.toString, file.getVirtualFile.getUrl, 2, "Result", attempt, status, attempt == 0 && status == "FAILED"), context)
    }
    service.history.size shouldBe 3
    RunResults.summary(service.history) should include("2 initial attempts")
    RunResults.summary(service.history) should include("1 retries")
    RunResults.summary(service.history) should include("Signal:")
    service.clearDecorations(); service.at(step) shouldBe empty
    service.clearHistory(); service.history shouldBe empty
    settings.runHistory = false
    service.record(CucumberMessages.ScenarioResult("off", "uri", 2, "Result", 0, "PASSED", false), context)
    service.history shouldBe empty
  }
  @Test
  def testHistoryKeepsErrorsWhenDecorationsAreDisabled(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.runHistory = true
    settings.resultDecorations = false
    val service = RunResults.getInstance(getProject)
    val context = RunResults.Context(0L, "revision", "environment", Map.empty, 0L)
    try {
      service.record(CucumberMessages.StepResult("run", "uri", 4, "uri:2", "FAILED", 5L, "Assertion failed"), context)
      service.record(CucumberMessages.ScenarioResult("run", "uri", 2, "Scenario", 0, "FAILED", false), context)
      service.history.last.error shouldBe "Assertion failed"
      val snapshot = service.getState
      service.clearHistory()
      snapshot.entries.size() shouldBe 1
    } finally {
      settings.runHistory = false
      settings.resultDecorations = true
      service.clearHistory()
    }
  }

}
