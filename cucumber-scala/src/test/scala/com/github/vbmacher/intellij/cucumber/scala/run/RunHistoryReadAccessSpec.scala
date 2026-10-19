package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.{EdtTestUtil, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import javax.swing.{JButton, JTable, SwingUtilities}
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class RunHistoryReadAccessSpec extends ScCucumberSpecBase {
  override protected def runInDispatchThread(): Boolean = false

  @Test
  def testSelectionChecksCurrentAndDeletedSourcesWithoutAnImplicitReadAction(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    val results = RunResults.getInstance(getProject)
    val previousHistory = settings.runHistory
    var view: RunHistoryPanel = null
    var table: JTable = null
    var open: JButton = null
    var file: PsiFile = null
    try {
      EdtTestUtil.runInEdtAndWait(() => {
        settings.runHistory = true
        file = myFixture.addFileToProject("read-access.feature", "Feature: History\n  Scenario: Result\n    Given example\n")
        val location = ScenarioSelection.locations(getProject, GlobalSearchScope.projectScope(getProject), "").toOption.get.head
        val entry = new ScenarioHistoryEntry
        entry.identity = RunResults.identity(location)
        entry.name = "Result"
        entry.status = "PASSED"
        val state = new ScenarioHistoryState
        state.entries.add(entry)
        results.loadState(state)
        view = new RunHistoryPanel(getProject)
        Disposer.register(getTestRootDisposable, view)
        table = UIUtil.findComponentOfType(view, classOf[JTable])
        open = UIUtil.findComponentsOfType(view, classOf[JButton]).asScala.find(_.getText == "Open Scenario").get
        PlatformTestUtil.waitWithEventsDispatching("history row loaded", () => table.getRowCount == 1, 10)
      })

      EdtTestUtil.runInEdtAndWait(() => {
        SwingUtilities.isEventDispatchThread shouldBe true
        ApplicationManager.getApplication.isReadAccessAllowed shouldBe false
        table.setRowSelectionInterval(0, 0)
        open.isEnabled shouldBe true
        table.clearSelection()
        open.isEnabled shouldBe false
      }, false)

      EdtTestUtil.runInEdtAndWait(() => WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
        override def run(): Unit = file.delete()
      }))
      EdtTestUtil.runInEdtAndWait(() => {
        ApplicationManager.getApplication.isReadAccessAllowed shouldBe false
        table.setRowSelectionInterval(0, 0)
        open.isEnabled shouldBe false
      }, false)
    } finally {
      EdtTestUtil.runInEdtAndWait(() => {
        if (view != null) Disposer.dispose(view)
        results.clearHistory()
        settings.runHistory = previousHistory
      })
    }
  }
}
