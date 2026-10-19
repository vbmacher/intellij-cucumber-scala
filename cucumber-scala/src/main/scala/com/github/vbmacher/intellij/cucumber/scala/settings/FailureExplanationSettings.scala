package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaHttp
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.{FormBuilder, UIUtil}

import java.awt.{BorderLayout, FlowLayout}
import javax.swing.{JButton, JCheckBox, JPanel, JPasswordField, JTextField}

class FailureExplanationSettings(project: Project) extends JPanel(new BorderLayout()) {
  private val enabledOption = new JCheckBox("Enable failure explanations")
  private val url = new JTextField(28)
  private val model = new JTextField(28)
  private val token = new JPasswordField(28)
  private val save = new JButton("Save token")
  private val clear = new JButton("Clear token")
  private val status = new JBLabel("Save token and Clear token act immediately for this URL, even if you cancel settings. Other changes require Apply.").setAllowAutoWrapping(true)
  private def settings = ScalaCucumberSettings.getInstance(project)
  private def hint(text: String): JBLabel =
    new JBLabel(text, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER).setAllowAutoWrapping(true)

  url.getAccessibleContext.setAccessibleName("Failure explanation Ollama URL")
  model.getAccessibleContext.setAccessibleName("Failure explanation chat model")
  token.getAccessibleContext.setAccessibleName("Failure explanation access token")
  enabledOption.setToolTipText("Offer Explain Cucumber Failure in feature files and retain recent failed results. No model request is automatic.")
  url.setToolTipText("Address of a running local or remote Ollama-compatible server, independent of semantic search and edge-case drafts.")
  model.setToolTipText("An installed chat model, not an embedding model.")
  token.setToolTipText("Optional bearer token. Save it in IDE Password Safe for the URL above; stored tokens are never displayed here.")
  val buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
  buttons.add(save)
  buttons.add(clear)
  add(FormBuilder.createFormBuilder()
    .addComponent(new TitledSeparator("Failure explanations"), 0)
    .addComponent(enabledOption)
    .addComponent(hint("Suggest possible causes of a recorded failure using its error, Gherkin and matching Scala code. Verify suggestions against the run results."))
    .addComponent(hint("After a run, right-click a failed step, scenario or Examples row and choose Explain Cucumber Failure…"))
    .addComponent(hint("Review or redact the context, then click Explain to send it to the server below."))
    .addLabeledComponent("Ollama URL:", url)
    .addLabeledComponent("Chat model:", model)
    .addComponent(hint("Use a chat model already installed on that server, such as llama3.2:3b. The plugin does not download models."))
    .addLabeledComponent("Access token (optional):", token)
    .addComponent(hint("Only needed if the server requires a bearer token. Saved in IDE Password Safe for this project and URL; stored tokens stay hidden."))
    .addComponent(buttons)
    .addComponent(status)
    .getPanel, BorderLayout.CENTER)
  private def controls(): Unit = Seq(url, model, token, save, clear).foreach(_.setEnabled(enabledOption.isSelected))
  enabledOption.addActionListener(_ => controls())
  save.addActionListener(_ => {
    val password = token.getPassword
    val value = new String(password)
    java.util.Arrays.fill(password, '\u0000')
    token.setText("")
    if (value.isEmpty) status.setText("Enter a token to save, or use Clear token to remove the stored token.")
    else updateToken(Some(value))
  })
  clear.addActionListener(_ => updateToken(None))
  private def updateToken(value: Option[String]): Unit = {
    val endpoint = url.getText.trim
    save.setEnabled(false)
    clear.setEnabled(false)
    new Task.Backgroundable(project, "Update failure explanation access token", false) {
      override def run(indicator: ProgressIndicator): Unit = {
        OllamaHttp.endpoint(endpoint, "")
        new OllamaCredentials(project, "failure explanation").save(endpoint, value)
      }
      override def onSuccess(): Unit = if (!project.isDisposed) {
        controls()
        status.setText(if (value.nonEmpty) "Token saved in IDE Password Safe for the URL above." else "Stored token cleared for the URL above.")
      }
      override def onThrowable(error: Throwable): Unit = if (!project.isDisposed) {
        controls()
        status.setText("Could not update the token. Check the URL and IDE Password Safe settings.")
      }
    }.queue()
  }

  def isModified: Boolean = enabledOption.isSelected != settings.failureExplanations || url.getText != settings.explanationUrl || model.getText != settings.explanationModel
  def apply(): Unit = {
    try OllamaHttp.endpoint(url.getText.trim, "")
    catch { case error: java.io.IOException => throw new ConfigurationException(error.getMessage) }
    if (enabledOption.isSelected && model.getText.trim.isEmpty) throw new ConfigurationException("Enter an installed chat model for failure explanations.")
    settings.failureExplanations = enabledOption.isSelected
    settings.explanationUrl = url.getText.trim
    settings.explanationModel = model.getText.trim
  }
  def reset(): Unit = {
    enabledOption.setSelected(settings.failureExplanations)
    url.setText(settings.explanationUrl)
    model.setText(settings.explanationModel)
    token.setText("")
    controls()
  }
}
