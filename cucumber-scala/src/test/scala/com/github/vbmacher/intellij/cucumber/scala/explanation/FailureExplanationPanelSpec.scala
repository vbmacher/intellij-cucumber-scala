package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.run.{CucumberMessages, RunResults}
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import javax.swing._

@RunWith(classOf[JUnit4])
class FailureExplanationPanelSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }
  private def button(panel: FailureExplanationPanel, text: String): JButton =
    children(panel).collectFirst { case b: JButton if b.getText == text => b }.get
  private def area(panel: FailureExplanationPanel, name: String): JTextArea =
    children(panel).collectFirst { case a: JTextArea if a.getAccessibleContext.getAccessibleName == name => a }.get
  private def status(panel: FailureExplanationPanel, text: String): Boolean =
    children(panel).exists { case l: JLabel => Option(l.getText).exists(_.contains(text)); case _ => false }
  private def await(panel: FailureExplanationPanel, text: String): Unit =
    PlatformTestUtil.waitWithEventsDispatching(text, () => status(panel, text), 10)
  private def panel(provider: FailureExplainer): FailureExplanationPanel = {
    ScalaCucumberSettings.getInstance(getProject).failureExplanations = true
    val file = myFixture.addFileToProject(s"failure-${System.nanoTime()}.feature", "Feature: Failure\n  Scenario: Transfer\n    Then transfer succeeds\n")
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile)
    val store = RunResults.getInstance(getProject)
    val context = RunResults.Context(PsiModificationTracker.getInstance(getProject).getModificationCount, "", "", Map.empty, 123L)
    store.record(CucumberMessages.StepResult("run", file.getVirtualFile.getUrl, 3, file.getVirtualFile.getUrl + ":2", "FAILED", 1L, "assertion"), context)
    val snapshot = inReadAction(FailureContext.collect(file.asInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile], store.failures(file.getVirtualFile.getUrl).head)).fold(message => fail(message), identity)
    val view = new FailureExplanationPanel(getProject, snapshot, (_, _, _) => provider)
    Disposer.register(getTestRootDisposable, view)
    view
  }

  @Test
  def testExplicitSubmissionUsesOnlyEditedContextAndLeavesSourceUnchanged(): Unit = {
    val submitted = new AtomicReference[String]()
    val view = panel(new FailureExplainer {
      override def explain(context: String, check: () => Unit): String = { submitted.set(context); "Possible cause" }
    })
    submitted.get() shouldBe null
    status(view, "Nothing has been sent") shouldBe true
    val original = myFixture.getFile.getText
    area(view, "Context to send").setText("redacted exact context")
    button(view, "Explain").doClick()
    await(view, "Generated explanation ready")
    submitted.get() shouldBe "redacted exact context"
    area(view, "Generated explanation").getText shouldBe "Possible cause"
    area(view, "Generated explanation").isEditable shouldBe false
    children(view).exists { case l: JLabel => l.getText.contains("not a verified cause"); case _ => false } shouldBe true
    myFixture.getFile.getText shouldBe original
    button(view, "Open source").doClick()
    myFixture.getEditor.getCaretModel.getOffset shouldBe original.indexOf("Then")
    RunResults.getInstance(getProject).clearDecorations()
    button(view, "Explain").doClick()
    status(view, "cleared or replaced") shouldBe true
  }

  @Test
  def testModelErrorCanBeRetriedAndChangedProviderRequiresNewReview(): Unit = {
    val view = panel(new FailureExplainer {
      override def explain(context: String, check: () => Unit): String = throw new java.io.IOException("HTTP 404: check chat model")
    })
    button(view, "Explain").doClick()
    await(view, "HTTP 404")
    button(view, "Explain").isEnabled shouldBe true
    ScalaCucumberSettings.getInstance(getProject).explanationModel = "different-model"
    button(view, "Explain").doClick()
    status(view, "settings changed") shouldBe true
    area(view, "Generated explanation").getText shouldBe ""
  }

  @Test
  def testCancelledAndClearedInflightRequestsNeverPublishLateAnswers(): Unit =
    PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () => {
        Seq("cancel", "clear", "close").foreach { operation =>
          val entered = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val finished = new CountDownLatch(1)
          val view = panel(new FailureExplainer {
            override def explain(context: String, check: () => Unit): String = {
              entered.countDown()
              try { release.await(10, TimeUnit.SECONDS); "late answer" } finally finished.countDown()
            }
          })
          button(view, "Explain").doClick()
          try {
            PlatformTestUtil.waitWithEventsDispatching("request entered", () => entered.getCount == 0, 10)
            button(view, "Explain").isEnabled shouldBe false
            operation match {
              case "cancel" => button(view, "Cancel request").doClick()
              case "clear" => RunResults.getInstance(getProject).clearDecorations()
              case "close" => Disposer.dispose(view)
            }
          } finally release.countDown()
          PlatformTestUtil.waitWithEventsDispatching("request finished", () => finished.getCount == 0, 10)
          if (operation == "clear") await(view, "cleared or replaced")
          else PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
          area(view, "Generated explanation").getText shouldBe ""
        }
    })
}
