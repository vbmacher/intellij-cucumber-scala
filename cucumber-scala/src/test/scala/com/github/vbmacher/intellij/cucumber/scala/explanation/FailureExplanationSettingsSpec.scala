package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.github.vbmacher.intellij.cucumber.scala.settings.{FailureExplanationSettings, ScalaCucumberSettings}
import com.intellij.openapi.actionSystem.{ActionPlaces, AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.Callable
import javax.swing._

@RunWith(classOf[JUnit4])
class FailureExplanationSettingsSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }

  @Test
  def testOptInActionAndSettingsValidation(): Unit = {
    myFixture.configureByText("failure.feature", "Feature: Failure\n  Scenario: Transfer\n    Then transfer succeeds\n")
    val context: DataContext = key =>
      if (CommonDataKeys.PROJECT.is(key)) getProject
      else if (CommonDataKeys.PSI_FILE.is(key)) myFixture.getFile
      else if (CommonDataKeys.EDITOR.is(key)) myFixture.getEditor
      else null
    val action = new ExplainFailureAction()
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.failureExplanations = false
    def visible: Boolean = {
      val event = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, context)
      action.update(event)
      event.getPresentation.isVisible
    }
    visible shouldBe false
    val view = new FailureExplanationSettings(getProject)
    view.reset()
    children(view).collectFirst { case c: JCheckBox => c }.get.doClick()
    val url = children(view).collectFirst { case f: JTextField if f.getAccessibleContext.getAccessibleName == "Failure explanation Ollama URL" => f }.get
    url.setText("http://user:secret@localhost")
    intercept[ConfigurationException](view.apply())
    settings.failureExplanations shouldBe false
    url.setText("https://provider.example/ollama")
    view.apply()
    visible shouldBe true
    settings.explanationUrl shouldBe "https://provider.example/ollama"
    view.isModified shouldBe false
    com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("ScalaCucumber.ExplainFailure") should not be null
  }

  @Test
  def testDisabledExplanationsDoNotBlockApplyAndTrimmedValuesAreUnchanged(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    val previous = settings.explanationUrl
    settings.failureExplanations = false
    try {
      val view = new FailureExplanationSettings(getProject)
      view.reset()
      val url = children(view).collectFirst { case f: JTextField if f.getAccessibleContext.getAccessibleName == "Failure explanation Ollama URL" => f }.get
      url.setText("******localhost")
      view.apply()
      url.setText(" http://localhost:11434 ")
      view.apply()
      settings.explanationUrl shouldBe "http://localhost:11434"
      view.isModified shouldBe false
    } finally settings.explanationUrl = previous
  }

  @Test
  def testPasswordSafeIsEndpointScopedAndExcludedFromProjectSettings(): Unit = {
    val url = "https://example.test/ollama"
    val credentials = new OllamaCredentials(getProject, "failure explanation")
    val future = AppExecutorUtil.getAppExecutorService.submit(new Callable[Unit] {
      override def call(): Unit = {
        try {
          credentials.save(url, Some("fixture-private-token"))
          credentials.token(url + "/") shouldBe Some("fixture-private-token")
          credentials.token("https://other.example.test") shouldBe None
          val serialized = JDOMUtil.writeElement(XmlSerializer.serialize(ScalaCucumberSettings.getInstance(getProject)))
          serialized should not include "fixture-private-token"
        } finally credentials.save(url, None)
        credentials.token(url) shouldBe None
      }
    })
    PlatformTestUtil.waitForFuture(future)
  }
}
