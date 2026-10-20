package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.awt.{Component, Container}
import java.util.concurrent.Callable
import javax.swing.{JButton, JCheckBox, JPasswordField, JTextField}

@RunWith(classOf[JUnit4])
class AccessTokenSettingsSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }

  private def background[A](body: => A): A = PlatformTestUtil.waitForFuture(
    AppExecutorUtil.getAppExecutorService.submit(new Callable[A] {
      override def call(): A = body
    }))

  @Test
  def testBothProviderTokensRequireApplyAndResetDiscardsEdits(): Unit = {
    val providers = Seq(
      ("failure explanation", "Failure explanation", "Enable failure explanations"),
      ("scenario drafts", "Edge-case draft", "Enable edge-case scenario drafts"))
    providers.foreach { case (purpose, name, enableLabel) =>
      val endpoint = s"https://${purpose.replace(' ', '-')}.example.test/ollama"
      val otherEndpoint = "https://other.example.test/ollama"
      val credentials = new OllamaCredentials(getProject, purpose)
      val configurable = new ScalaCucumberConfigurable(getProject)
      val component = configurable.createComponent().asInstanceOf[Container]
      configurable.reset()
      val controls = children(component)
      controls.collect { case b: JButton => b.getText } should not contain "Save token"
      controls.collect { case b: JButton => b.getText } should not contain "Clear token"
      val enable = controls.collectFirst { case c: JCheckBox if c.getText == enableLabel => c }.get
      if (!enable.isSelected) enable.doClick()
      val url = controls.collectFirst { case f: JTextField if f.getAccessibleContext.getAccessibleName == s"$name Ollama URL" => f }.get
      val token = controls.collectFirst { case f: JPasswordField if f.getAccessibleContext.getAccessibleName == s"$name access token" => f }.get
      controls.collect { case c: JCheckBox => c.getText } should not contain "Remove saved token"
      try {
        background {
          credentials.save(endpoint, Some("original"))
          credentials.save(otherEndpoint, Some("other"))
        }
        url.setText(endpoint)
        token.setText("original")
        configurable.apply()
        configurable.reset()
        new String(token.getPassword) shouldBe "original"
        token.echoCharIsSet shouldBe true
        // Applying unchanged masked values preserves the saved token.
        configurable.apply()
        background(credentials.token(endpoint)) shouldBe Some("original")
        configurable.isModified shouldBe false
        token.setText("replacement")
        configurable.isModified shouldBe true
        background(credentials.token(endpoint)) shouldBe Some("original")
        configurable.reset()
        new String(token.getPassword) shouldBe "original"
        configurable.isModified shouldBe false
        background(credentials.token(endpoint)) shouldBe Some("original")
        token.setText("replacement")
        configurable.apply()
        background(credentials.token(endpoint)) shouldBe Some("replacement")
        configurable.isModified shouldBe false
        new String(token.getPassword) shouldBe "replacement"
        token.setText("")
        configurable.isModified shouldBe true
        background(credentials.token(endpoint)) shouldBe Some("replacement")
        configurable.reset()
        new String(token.getPassword) shouldBe "replacement"
        token.setText("")
        configurable.apply()
        background(credentials.token(endpoint)) shouldBe None
        background(credentials.token(otherEndpoint)) shouldBe Some("other")
        token.getPassword shouldBe empty
        configurable.isModified shouldBe false
      } finally {
        background {
          credentials.save(endpoint, None)
          credentials.save(otherEndpoint, None)
        }
        configurable.disposeUIResources()
      }
    }
  }

  @Test
  def testChangingUrlDoesNotCopyTokensAndBlankClearsOnlyTheEnteredUrl(): Unit = {
    val endpoint = "https://first.example.test/ollama"
    val otherEndpoint = "https://second.example.test/ollama"
    val credentials = new OllamaCredentials(getProject, "scenario drafts")
    val url = new JTextField(endpoint)
    val view = new AccessTokenSettings(getProject, "scenario drafts", "Draft token", url)
    try {
      background {
        credentials.save(endpoint, Some("first-token"))
        credentials.save(otherEndpoint, Some("second-token"))
      }
      view.reset()
      new String(view.getPassword) shouldBe "first-token"
      view.isModified shouldBe false
      url.setText(otherEndpoint)
      view.getPassword shouldBe empty
      view.isModified shouldBe true
      background(credentials.token(otherEndpoint)) shouldBe Some("second-token")
      view.apply()
      background(credentials.token(otherEndpoint)) shouldBe None
      background(credentials.token(endpoint)) shouldBe Some("first-token")
      view.isModified shouldBe false
    } finally background {
      credentials.save(endpoint, None)
      credentials.save(otherEndpoint, None)
    }
  }

  @Test
  def testInvalidEndpointDoesNotSaveOrDiscardPendingToken(): Unit = {
    val url = new JTextField("http://user:secret@localhost")
    val view = new AccessTokenSettings(getProject, "scenario drafts", "Draft token", url)
    view.setText("pending-token")
    intercept[ConfigurationException](view.apply())
    view.isModified shouldBe true
    view.reset()
    view.isModified shouldBe false
  }
}
