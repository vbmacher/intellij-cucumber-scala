package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, DialogWrapperPeer, DialogWrapperPeerFactory}
import com.intellij.openapi.ui.impl.DialogWrapperPeerFactoryImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.{PlatformTestUtil, ServiceContainerUtil}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.atomic.AtomicReference
import javax.swing.{JButton, JLabel, JSplitPane, JTable, JTextArea}

@RunWith(classOf[JUnit4])
class RunHistoryPanelSpec extends ScCucumberSpecBase {
  private def settings = ScalaCucumberSettings.getInstance(getProject)
  private def results = RunResults.getInstance(getProject)
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(view: Container, name: String): JButton = children(view).collectFirst {
    case b: JButton if b.getText == name || b.getToolTipText == name => b
  }.get
  private def table(view: Container): JTable = children(view).collectFirst { case t: JTable => t }.get
  private def hasText(view: Container, text: String): Boolean = children(view).exists {
    case label: JLabel => Option(label.getText).exists(_.contains(text))
    case _ => false
  }
  private def panel(): RunHistoryPanel = {
    val view = new RunHistoryPanel(getProject)
    Disposer.register(getTestRootDisposable, view)
    view
  }
  private def awaitRows(view: Container, count: Int): Unit =
    PlatformTestUtil.waitWithEventsDispatching("history rows updated", () => table(view).getRowCount == count, 10)

  override def setUp(): Unit = {
    super.setUp()
    settings.runHistory = false
    settings.historyRetention = 200
    results.clearHistory()
  }
  override def tearDown(): Unit = {
    try {
      settings.runHistory = false
      settings.historyRetention = 200
      results.clearHistory()
      results.clearDecorations()
    } finally super.tearDown()
  }

  @Test
  def testEmptyStatesExplainRecordingAndContainNoFakeRowsOrSplit(): Unit = {
    val view = panel()
    table(view).getRowCount shouldBe 0
    children(view).exists(_.isInstanceOf[JSplitPane]) shouldBe false
    hasText(view, "History is off") shouldBe true
    Seq("Attempts", "Open Scenario", "Clear History").foreach(button(view, _).isEnabled shouldBe false)
    def layout(container: Container): Unit = {
      container.doLayout()
      container.getComponents.collect { case child: Container => child }.foreach(layout)
    }
    Seq(360, 1066).foreach { width =>
      view.setSize(width, 400)
      (1 to 3).foreach(_ => layout(view))
      Seq("Refresh", "Attempts", "Open Scenario", "Clear History", "History Settings", "Enable History").foreach { name =>
        val control = button(view, name)
        val bounds = javax.swing.SwingUtilities.convertRectangle(control.getParent, control.getBounds, view)
        bounds.x should be >= 0
        bounds.x + bounds.width should be <= width
        bounds.height should be > 0
      }
    }
    button(view, "Enable History").doClick()
    PlatformTestUtil.waitWithEventsDispatching("history enabled", () => !button(view, "Enable History").isVisible, 10)
    settings.runHistory shouldBe true
    hasText(view, "appear automatically") shouldBe true
    table(view).getRowCount shouldBe 0
    settings.historyRetention = 0
    results.refreshHistory()
    PlatformTestUtil.waitWithEventsDispatching("zero retention explained", () => hasText(view, "Maximum history entries is 0"), 10)
    button(view, "History Settings").isEnabled shouldBe true
  }

  @Test
  def testFastRuntimeResultsAppearAndClearWithoutRefresh(): Unit = {
    settings.runHistory = true
    val view = panel()
    val path = java.nio.file.Files.createTempFile("cucumber-history-", ".ndjson")
    java.nio.file.Files.write(path, java.nio.file.Files.readAllLines(java.nio.file.Path.of(getTestDataPath, "results", "outline-messages.ndjson")))
    val context = RunResults.Context(0L, "revision", "environment", Map.empty, 0L)
    val handler = new com.intellij.execution.process.ProcessHandler {
      override def destroyProcessImpl(): Unit = notifyProcessTerminated(0)
      override def detachProcessImpl(): Unit = notifyProcessDetached()
      override def detachIsDefault: Boolean = false
      override def getProcessInput: java.io.OutputStream = null
    }
    val bridge = new CucumberMessages("fast-history", _ => (), results.record(_, context), results.record(_, context))
    new CucumberMessageTail(path, handler, bridge)
    handler.startNotify()
    handler.destroyProcess()
    awaitRows(view, 2)
    results.history.size shouldBe 2
    (0 until 2).foreach { row =>
      table(view).getValueAt(row, 1) shouldBe "PASSED"
      table(view).getValueAt(row, 2) shouldBe "1 initial, 0 retries"
    }
    table(view).setRowSelectionInterval(0, 0)
    button(view, "Attempts").isEnabled shouldBe true
    button(view, "Open Scenario").isEnabled shouldBe false
    button(view, "Clear History").doClick()
    awaitRows(view, 0)
    results.history shouldBe empty
    Seq("Attempts", "Open Scenario", "Clear History").foreach(button(view, _).isEnabled shouldBe false)
  }

  @Test
  def testLatestAttemptUsesRecordingOrderWhenTimestampsMatch(): Unit = {
    settings.runHistory = true
    val state = new ScenarioHistoryState
    Seq("FAILED", "PASSED").zipWithIndex.foreach { case (status, attempt) =>
      val entry = new ScenarioHistoryEntry
      entry.name = "Fast retry"
      entry.status = status
      entry.attempt = attempt
      entry.timestamp = 123L
      state.entries.add(entry)
    }
    results.loadState(state)
    val view = panel()
    awaitRows(view, 1)
    table(view).getValueAt(0, 1) shouldBe "PASSED"
    table(view).getValueAt(0, 2) shouldBe "1 initial, 1 retries"
  }

  @Test
  def testSelectionSurvivesNewResultsAndAttemptsShowFailuresAndRetries(): Unit = {
    settings.runHistory = true
    val file = myFixture.addFileToProject("history.feature", "Feature: History\n  Scenario: Result\n    Given example\n")
    val locations = ScenarioSelection.locations(getProject, com.intellij.psi.search.GlobalSearchScope.projectScope(getProject), "").toOption.get
    val location = locations.find(_.scenario.getScenarioName == "Result").get
    val context = RunResults.Context(0L, "revision", "environment", Map(RunResults.key(file.getVirtualFile.getUrl, 2) -> RunResults.identity(location)), 0L)
    val view = panel()
    val failure = CucumberMessages.ScenarioResult("one", file.getVirtualFile.getUrl, 2, "Result", 0, "FAILED", true)
    results.record(CucumberMessages.StepResult("one", file.getVirtualFile.getUrl, 3, file.getVirtualFile.getUrl + ":2", "FAILED", 5L, "Expected 42"), context)
    results.record(failure, context)
    awaitRows(view, 1)
    table(view).setRowSelectionInterval(0, 0)
    button(view, "Open Scenario").isEnabled shouldBe true
    button(view, "Open Scenario").doClick()
    val editor = FileEditorManager.getInstance(getProject).getSelectedTextEditor
    editor.getDocument.getText shouldBe file.getText
    editor.getCaretModel.getOffset shouldBe ScenarioSelection.sourceOffset(location.target)
    results.record(failure.copy(run = "retry", attempt = 1, status = "PASSED", willRetry = false), context)
    PlatformTestUtil.waitWithEventsDispatching("retry shown", () => table(view).getValueAt(0, 2) == "1 initial, 1 retries", 10)
    table(view).getSelectedRow shouldBe 0
    table(view).getValueAt(0, 1) shouldBe "PASSED"
    val shown = new AtomicReference[DialogWrapper]()
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication, classOf[DialogWrapperPeerFactory], new DialogWrapperPeerFactoryImpl {
      override def createPeer(wrapper: DialogWrapper, project: Project, canBeParent: Boolean, modality: DialogWrapper.IdeModalityType): DialogWrapperPeer = {
        shown.set(wrapper)
        super.createPeer(wrapper, project, canBeParent, modality)
      }
    }, getTestRootDisposable)
    button(view, "Attempts").doClick()
    val dialog = shown.get()
    try {
      val text = dialog.getPreferredFocusedComponent.asInstanceOf[JTextArea].getText
      Seq("Expected 42", "FAILED", "PASSED", "1 retries", "Revision: revision", "Environment: environment").foreach(text should include(_))
    } finally dialog.close(DialogWrapper.OK_EXIT_CODE)
    results.record(failure.copy(run = "other"), context.copy(environment = "other environment"))
    awaitRows(view, 2)
    table(view).getSelectedRow shouldBe 1
    table(view).getValueAt(1, 5) shouldBe "environment"
    Disposer.dispose(view)
    results.clearHistory()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    table(view).getRowCount shouldBe 2
  }
}
