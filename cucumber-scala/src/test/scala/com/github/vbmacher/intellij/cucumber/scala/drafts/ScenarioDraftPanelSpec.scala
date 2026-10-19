package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.EditorTextField
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import javax.swing._

@RunWith(classOf[JUnit4])
class ScenarioDraftPanelSpec extends ScCucumberSpecBase {
  private val valid = ScenarioDraft("Zero", "Lower boundary", "Scenario: Zero\n  When transfer 0\n  Then rejected")
  private val second = ScenarioDraft("Duplicate", "Repeat request", "Scenario: Duplicate\n  When transfer 10")
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(view: ScenarioDraftPanel, text: String): JButton =
    children(view).collectFirst { case b: JButton if b.getText == text => b }.get
  private def status(view: ScenarioDraftPanel, text: String): Boolean = children(view).exists {
    case label: JLabel => Option(label.getText).exists(_.contains(text))
    case _ => false
  }
  private def await(view: ScenarioDraftPanel, text: String): Unit =
    PlatformTestUtil.waitWithEventsDispatching(text, () => status(view, text), 10)
  private def panel(provider: ScenarioDraftProvider): ScenarioDraftPanel = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.scenarioDrafts = true
    settings.draftUrl = "http://localhost:11434"
    settings.draftModel = "test-chat"
    val file = myFixture.addFileToProject(s"panel-${System.nanoTime()}.feature", "Feature: Payments\n  Scenario: Existing\n    Given account\n")
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile)
    val snapshot = inReadAction(DraftContext.collect(file.asInstanceOf[GherkinFile], myFixture.getEditor.getDocument, file.getText.indexOf("Existing"), None))
      .fold(fail(_), identity)
    val view = new ScenarioDraftPanel(getProject, snapshot, providerFactory = (_, _, _) => provider)
    Disposer.register(getTestRootDisposable, view)
    view
  }

  @Test
  def testExplicitSubmissionPreviewAndSelectedOnlyInsertion(): Unit = {
    val submitted = new AtomicReference[String]()
    val view = panel(new ScenarioDraftProvider {
      override def suggest(context: String, check: () => Unit): Seq[ScenarioDraft] = { submitted.set(context); Seq(valid, second) }
    })
    submitted.get() shouldBe null
    status(view, "Nothing has been sent") shouldBe true
    button(view, "Insert selected").isEnabled shouldBe false
    val original = myFixture.getFile.getText
    children(view).collectFirst { case a: JTextArea if a.getAccessibleContext.getAccessibleName == "Context to send" => a }.get
      .setText("reviewed specification")
    button(view, "Generate drafts").doClick()
    await(view, "select drafts to insert")
    submitted.get() shouldBe "reviewed specification"
    myFixture.getFile.getText shouldBe original
    val preview = children(view).collectFirst { case e: EditorTextField => e }.get
    preview.getText should include("Scenario: Zero")
    children(view).collectFirst { case a: JTextArea if a.getAccessibleContext.getAccessibleName == "Draft validation" => a }.get.getText should include("Unresolved")
    val checks = children(view).collect { case c: JCheckBox => c }
    checks.map(_.isSelected) shouldBe Seq(false, false)
    checks.head.doClick()
    button(view, "Insert selected").doClick()
    myFixture.getFile.getText should include("Scenario: Zero")
    myFixture.getFile.getText should not include "Scenario: Duplicate"
    status(view, "Not executed") shouldBe true
  }

  @Test
  def testEditingContextInvalidatesPreviousDrafts(): Unit = {
    val view = panel(new ScenarioDraftProvider {
      override def suggest(context: String, check: () => Unit): Seq[ScenarioDraft] = Seq(valid)
    })
    button(view, "Generate drafts").doClick()
    await(view, "select drafts to insert")
    children(view).collectFirst { case c: JCheckBox => c }.get.doClick()
    button(view, "Insert selected").isEnabled shouldBe true
    children(view).collectFirst { case a: JTextArea if a.getAccessibleContext.getAccessibleName == "Context to send" => a }.get
      .append("\nChanged requirement")
    button(view, "Insert selected").isEnabled shouldBe false
    children(view).collect { case c: JCheckBox => c } shouldBe empty
    myFixture.getFile.getText should not include "Scenario: Zero"
  }

  @Test
  def testInvalidDraftCannotBeSelectedAndModelErrorsCanBeRetried(): Unit = {
    var requests = 0
    val view = panel(new ScenarioDraftProvider {
      override def suggest(context: String, check: () => Unit): Seq[ScenarioDraft] = {
        requests += 1
        if (requests == 1) throw new java.io.IOException("HTTP 404: check installed model")
        Seq(valid.copy(gherkin = "not a scenario"))
      }
    })
    val original = myFixture.getFile.getText
    button(view, "Generate drafts").doClick()
    await(view, "HTTP 404")
    button(view, "Generate drafts").isEnabled shouldBe true
    button(view, "Generate drafts").doClick()
    await(view, "select drafts to insert")
    children(view).collect { case c: JCheckBox => c }.foreach(_.isEnabled shouldBe false)
    button(view, "Insert selected").isEnabled shouldBe false
    myFixture.getFile.getText shouldBe original
  }

  @Test
  def testCancelCloseChangedSourceAndChangedProviderRejectLateResults(): Unit =
    PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () => {
      Seq("cancel", "close", "source", "provider").foreach { operation =>
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)
        val finished = new CountDownLatch(1)
        val view = panel(new ScenarioDraftProvider {
          override def suggest(context: String, check: () => Unit): Seq[ScenarioDraft] = {
            entered.countDown()
            try { release.await(10, TimeUnit.SECONDS); Seq(valid) } finally finished.countDown()
          }
        })
        button(view, "Generate drafts").doClick()
        try {
          PlatformTestUtil.waitWithEventsDispatching("request entered", () => entered.getCount == 0, 10)
          operation match {
            case "cancel" => button(view, "Cancel request").doClick()
            case "close" => Disposer.dispose(view)
            case "source" => WriteCommandAction.runWriteCommandAction(getProject, new Runnable {
              override def run(): Unit = myFixture.getEditor.getDocument.insertString(0, "# changed\n")
            })
            case "provider" => ScalaCucumberSettings.getInstance(getProject).draftModel = "changed"
          }
        } finally release.countDown()
        PlatformTestUtil.waitWithEventsDispatching("provider returned", () => finished.getCount == 0, 10)
        if (operation == "source") await(view, "feature changed")
        else if (operation == "provider") await(view, "settings changed")
        else PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        children(view).collect { case c: JCheckBox => c } shouldBe empty
        myFixture.getEditor.getDocument.getText should not include "Scenario: Zero"
      }
    })
}
