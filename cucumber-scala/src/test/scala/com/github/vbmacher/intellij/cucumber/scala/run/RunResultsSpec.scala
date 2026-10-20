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
  def testFastProcessCompletesEveryNodeInTheNativeConsole(): Unit = {
    val path = java.nio.file.Files.createTempFile("cucumber-native-results-", ".ndjson")
    java.nio.file.Files.write(path, envelopes)
    val handler = new com.intellij.execution.process.ProcessHandler {
      override def destroyProcessImpl(): Unit = notifyProcessTerminated(0)
      override def detachProcessImpl(): Unit = notifyProcessDetached()
      override def detachIsDefault: Boolean = false
      override def getProcessInput: java.io.OutputStream = null
    }
    val messages = new CucumberMessages("fast-run", text => handler.notifyTextAvailable(text, com.intellij.execution.process.ProcessOutputTypes.STDOUT), _ => (), _ => ())
    new CucumberMessageTail(path, handler, messages)
    val configuration = new ScCucumberConfigurationType().getConfigurationFactories.head.createTemplateConfiguration(getProject).asInstanceOf[ScCucumberRunConfiguration]
    val properties = new ScCucumberConsoleProperties(configuration, com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance)
    val console = com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil.createAndAttachConsole("ScalaCucumber", handler, properties)
    com.intellij.openapi.util.Disposer.register(getTestRootDisposable, console)
    handler.startNotify()
    handler.destroyProcess()
    com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val root = console.asInstanceOf[com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView].getResultsViewer.getTestsRootNode
    import scala.jdk.CollectionConverters._
    val tests = root.getAllTests.asScala.filterNot(_.isSuite)
    tests.size shouldBe 6
    tests.foreach(_.isPassed shouldBe true)
    root.isInProgress shouldBe false
    java.nio.file.Files.exists(path) shouldBe false
  }

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
    service.forEditor(step).size shouldBe 1
    service.stale(service.forEditor(step).head) shouldBe false
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
      override def run(): Unit = myFixture.getEditor.getDocument.insertString(myFixture.getEditor.getDocument.getTextLength, "# change\n")
    })
    com.intellij.psi.PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    service.stale(service.forEditor(step).head) shouldBe true
    Seq(("FAILED", 0), ("PASSED", 1), ("PASSED", 0), ("FAILED", 0)).zipWithIndex.foreach { case ((status, attempt), i) =>
      service.record(CucumberMessages.ScenarioResult(i.toString, file.getVirtualFile.getUrl, 2, "Result", attempt, status, attempt == 0 && status == "FAILED"), context)
    }
    service.history.size shouldBe 3
    RunResults.summary(service.history) should include("2 initial attempts")
    RunResults.summary(service.history) should include("1 retries")
    RunResults.summary(service.history) should include("Signal:")
    service.clearEditorResults(); service.forEditor(step) shouldBe empty
    service.clearHistory(); service.history shouldBe empty
    settings.runHistory = false
    service.record(CucumberMessages.ScenarioResult("off", "uri", 2, "Result", 0, "PASSED", false), context)
    service.history shouldBe empty
  }
  @Test
  def testClearingSavedHistoryPreservesActiveErrorsAndIndependentConsumers(): Unit = {
    val file = myFixture.configureByText("active.feature", "Feature: Active\n Scenario: Active\n  Given example\n")
    val step = myFixture.findElementByText("Given example", classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep])
    val settings = ScalaCucumberSettings.getInstance(getProject)
    val service = RunResults.getInstance(getProject)
    val context = RunResults.Context(com.intellij.psi.util.PsiModificationTracker.getInstance(getProject).getModificationCount,
      "revision", "environment", Map.empty, 123L)
    val uri = file.getVirtualFile.getUrl
    try {
      for (editor <- Seq(false, true); history <- Seq(false, true); explanations <- Seq(false, true)) {
        service.clearEditorResults()
        service.clearHistory()
        settings.resultDecorations = editor
        settings.runHistory = history
        settings.failureExplanations = explanations
        service.record(CucumberMessages.ScenarioResult("old", uri, 2, "Active", 0, "PASSED", false), context)
        service.record(CucumberMessages.StepResult("active", uri, 3, uri + ":2", "FAILED", 5L, "Active assertion"), context)
        service.clearHistory()
        service.history shouldBe empty
        service.forEditor(step).nonEmpty shouldBe editor
        service.failures(uri).nonEmpty shouldBe (editor || explanations)
        service.record(CucumberMessages.ScenarioResult("active", uri, 2, "Active", 0, "FAILED", false), context)
        service.history.map(_.error) shouldBe (if (history) Seq("Active assertion") else Seq.empty)
        if (explanations) service.failures(uri).find(_.result.line == 2).get.result.error shouldBe "Active assertion"
        service.clearEditorResults()
        service.forEditor(step) shouldBe empty
        service.failures(uri) shouldBe empty
        service.history.size shouldBe (if (history) 1 else 0)
      }
    } finally {
      settings.resultDecorations = true
      settings.runHistory = false
      settings.failureExplanations = false
      service.clearEditorResults()
      service.clearHistory()
    }
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

  @Test
  def testHookFailuresRetainErrorOnTheSelectedExampleWithoutAStepBinding(): Unit = {
    import scala.jdk.CollectionConverters._
    val steps = ArrayBuffer.empty[CucumberMessages.StepResult]
    val scenarios = ArrayBuffer.empty[CucumberMessages.ScenarioResult]
    val bridge = new CucumberMessages("hooks", _ => (), steps += _, scenarios += _)
    val messages = envelopes.asScala.map(s => JsonParser.parseString(s).getAsJsonObject)
    val hookIds = messages.filter(_.has("testCase")).flatMap(_.getAsJsonObject("testCase")
      .getAsJsonArray("testSteps").asScala.map(_.getAsJsonObject).filter(_.has("hookId")).map(_.get("id").getAsString)).toSet
    messages.foreach { envelope =>
      if (envelope.has("testStepFinished")) {
        val finished = envelope.getAsJsonObject("testStepFinished")
        if (hookIds(finished.get("testStepId").getAsString)) {
          finished.getAsJsonObject("testStepResult").addProperty("status", "FAILED")
          finished.getAsJsonObject("testStepResult").addProperty("message", "Hook setup failed")
        }
      }
      bridge.consume(envelope.toString)
    }
    val failed = steps.filter(_.status == "FAILED")
    failed.map(_.line).toSet shouldBe Set(11, 12)
    failed.foreach { failure =>
      failure.error shouldBe "Hook setup failed"
      failure.selector should endWith(":" + failure.line)
    }
    scenarios.foreach(_.status shouldBe "FAILED")
  }

  @Test
  def testReducingRetentionTrimsHistoryImmediately(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    val service = RunResults.getInstance(getProject)
    val retention = settings.historyRetention
    settings.runHistory = true
    settings.historyRetention = 3
    try {
      val context = RunResults.Context(0L, "revision", "environment", Map.empty, 0L)
      (1 to 3).foreach(i => service.record(CucumberMessages.ScenarioResult(i.toString, "uri", 2, "Result", 0, "PASSED", false), context))
      service.history.size shouldBe 3
      settings.historyRetention = 1
      service.retentionChanged()
      service.history.map(_.run) shouldBe Seq("3")
    } finally {
      settings.runHistory = false
      settings.historyRetention = retention
      service.clearHistory()
    }
  }

  @Test
  def testVfsUrlsWithSpecialCharactersMatchCucumberUris(): Unit = {
    RunResults.fileUri("file:///home/u/my proj/a#1.feature") shouldBe RunResults.uri("file:///home/u/my%20proj/a%231.feature")
    RunResults.fileUri("file:///home/u/my proj/a.feature") shouldBe RunResults.uri("file:/home/u/my%20proj/a.feature")
    RunResults.key(RunResults.fileUri("file:///home/u/my proj/a.feature"), 3) shouldBe RunResults.key("file:/home/u/my%20proj/a.feature", 3)
    RunResults.uri("file:///home/u/not encoded.feature") shouldBe "file:///home/u/not encoded.feature"
    com.github.vbmacher.intellij.cucumber.scala.debug.FeatureBreakpoints.matches(
      "file:///home/u/my proj/a.feature", 4, None, "file:/home/u/my%20proj/a.feature", 4, 9) shouldBe true
  }

  @Test
  def testOutlineStepGutterShowsWorstOutcomeOfLatestRun(): Unit = {
    val file = myFixture.configureByText("outline.feature",
      "Feature: Outline\n  Scenario Outline: Rows\n    Given value <n>\n    Examples:\n      | n |\n      | 1 |\n      | 2 |\n")
    val url = file.getVirtualFile.getUrl
    val service = RunResults.getInstance(getProject)
    val stamp = com.intellij.psi.util.PsiModificationTracker.getInstance(getProject).getModificationCount
    val step = myFixture.findElementByText("Given value <n>", classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep])
    def icon = new FeatureResultLineMarker().getLineMarkerInfo(step.getFirstChild).getIcon
    try {
      val first = RunResults.Context(stamp, "revision", "environment", Map.empty, 1L)
      service.record(CucumberMessages.StepResult("first", url, 3, url + ":6", "FAILED", 1L, "assertion"), first)
      service.record(CucumberMessages.StepResult("first", url, 3, url + ":7", "PASSED", 1L, ""), first)
      icon shouldBe com.intellij.icons.AllIcons.RunConfigurations.TestFailed
      val second = RunResults.Context(stamp, "revision", "environment", Map.empty, 2L)
      service.record(CucumberMessages.StepResult("second", url, 3, url + ":6", "PASSED", 1L, ""), second)
      service.record(CucumberMessages.StepResult("second", url, 3, url + ":7", "PASSED", 1L, ""), second)
      icon shouldBe com.intellij.icons.AllIcons.RunConfigurations.TestPassed
    } finally service.clearEditorResults()
  }

  @Test
  def testConsoleLocatorDecodesCucumberUris(): Unit = {
    val directory = java.nio.file.Files.createTempDirectory("scala cucumber ")
    com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable, directory.toString)
    try {
      val feature = java.nio.file.Files.writeString(directory.resolve("my feature.feature"), "Feature: Located\n  Scenario: Here\n    Given step\n")
      com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(feature) should not be null
      val locations = ScCucumberConsole.Locator.getLocation("scala-cucumber", feature.toUri.toString + ":2", getProject,
        com.intellij.psi.search.GlobalSearchScope.allScope(getProject))
      locations.size() shouldBe 1
      locations.get(0).getVirtualFile.getName shouldBe "my feature.feature"
    } finally com.intellij.openapi.util.io.FileUtil.delete(directory)
  }
}
