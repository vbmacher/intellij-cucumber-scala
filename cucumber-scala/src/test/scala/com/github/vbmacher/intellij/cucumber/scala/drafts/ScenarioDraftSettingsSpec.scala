package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.settings.{ScalaCucumberConfigurable, ScalaCucumberSettings}
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, AnActionEvent, CommonDataKeys, DataContext, DefaultActionGroup}
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
class ScenarioDraftSettingsSpec extends ScCucumberSpecBase {
  private def children(container: Container): Seq[Component] = container.getComponents.toSeq.flatMap {
    case child: Container => Seq(child) ++ children(child)
    case child => Seq(child)
  }

  @Test
  def testDraftTokensAreEndpointScopedAndSeparateFromFailureTokens(): Unit = {
    val url = "https://drafts.example.test/ollama"
    val credentials = new OllamaCredentials(getProject, "scenario drafts")
    val failureCredentials = new OllamaCredentials(getProject, "failure explanation")
    val future = AppExecutorUtil.getAppExecutorService.submit(new Callable[Unit] {
      override def call(): Unit = {
        try {
          credentials.save(url, Some("fixture-draft-secret"))
          credentials.token(url + "/") shouldBe Some("fixture-draft-secret")
          credentials.token("https://other.example.test") shouldBe None
          failureCredentials.token(url) shouldBe None
          JDOMUtil.writeElement(XmlSerializer.serialize(ScalaCucumberSettings.getInstance(getProject))) should not include "fixture-draft-secret"
        } finally credentials.save(url, None)
        credentials.token(url) shouldBe None
      }
    })
    PlatformTestUtil.waitForFuture(future)
  }
  private def event: AnActionEvent = {
    val context: DataContext = key =>
      if (CommonDataKeys.PROJECT.is(key)) getProject
      else if (CommonDataKeys.PSI_FILE.is(key)) myFixture.getFile
      else if (CommonDataKeys.EDITOR.is(key)) myFixture.getEditor
      else null
    AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, context)
  }

  @Test
  def testIndependentOptInConfigurationAndActionRegistration(): Unit = {
    val settings = ScalaCucumberSettings.getInstance(getProject)
    settings.scenarioDrafts shouldBe false
    myFixture.configureByText("payments.feature", "Feature: Payments\n  Scenario: Transfer\n    Given account\n")
    val action = new SuggestEdgeCasesAction
    def visible: Boolean = { val update = event; action.update(update); update.getPresentation.isEnabledAndVisible }
    visible shouldBe false
    val configurable = new ScalaCucumberConfigurable(getProject)
    val component = configurable.createComponent().asInstanceOf[Container]
    configurable.reset()
    configurable.isModified shouldBe false
    val enable = children(component).collectFirst { case c: JCheckBox if c.getText == "Enable edge-case scenario drafts" => c }.get
    val url = children(component).collectFirst { case t: JTextField if t.getAccessibleContext.getAccessibleName == "Edge-case draft Ollama URL" => t }.get
    val model = children(component).collectFirst { case t: JTextField if t.getAccessibleContext.getAccessibleName == "Edge-case draft chat model" => t }.get
    url.isEnabled shouldBe false
    enable.doClick()
    url.isEnabled shouldBe true
    url.setText("http://user:secret@localhost")
    intercept[ConfigurationException](configurable.apply())
    settings.scenarioDrafts shouldBe false
    url.setText("http://localhost:11434")
    model.setText("")
    intercept[ConfigurationException](configurable.apply())
    model.setText("installed-chat")
    configurable.apply()
    configurable.isModified shouldBe false
    settings.draftModel shouldBe "installed-chat"
    settings.failureExplanations shouldBe false
    visible shouldBe true
    val manager = ActionManager.getInstance()
    manager.getAction("ScalaCucumber.SuggestEdgeCases") should not be null
    manager.getAction("EditorPopupMenu").asInstanceOf[DefaultActionGroup].getChildren(null: AnActionEvent)
      .contains(manager.getAction("ScalaCucumber.SuggestEdgeCases")) shouldBe true
    myFixture.configureByText("plain.txt", "Payments")
    visible shouldBe false
    configurable.disposeUIResources()
  }
}
