package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.util.OllamaHttp
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.{FormBuilder, UIUtil}

import java.awt.BorderLayout
import javax.swing.{JCheckBox, JPanel, JTextField}

class FailureExplanationSettings(project: Project) extends JPanel(new BorderLayout()) {
  private val enabledOption = new JCheckBox("Enable failure explanations")
  private val url = new JTextField(28)
  private val model = new JTextField(28)
  private val token = new AccessTokenSettings(project, "failure explanation", "Failure explanation access token", url)
  private def settings = ScalaCucumberSettings.getInstance(project)
  private def hint(text: String): JBLabel =
    new JBLabel(text, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER).setAllowAutoWrapping(true)

  url.getAccessibleContext.setAccessibleName("Failure explanation Ollama URL")
  model.getAccessibleContext.setAccessibleName("Failure explanation chat model")
  enabledOption.setToolTipText("Offer Explain Cucumber Failure in feature files and retain recent failed results. No model request is automatic.")
  url.setToolTipText("Address of a running local or remote Ollama-compatible server, independent of semantic search and edge-case drafts.")
  model.setToolTipText("An installed chat model, not an embedding model.")
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
    .addComponent(hint("Local Ollama needs no token: leave this blank. Only protected remote servers need a bearer token; saved tokens are masked and stored in IDE Password Safe."))
    .getPanel, BorderLayout.CENTER)
  private def controls(): Unit = Seq(url, model, token).foreach(_.setEnabled(enabledOption.isSelected))
  enabledOption.addActionListener(_ => controls())

  def isModified: Boolean = token.isModified || enabledOption.isSelected != settings.failureExplanations || url.getText.trim != settings.explanationUrl || model.getText.trim != settings.explanationModel
  def apply(): Unit = {
    if (enabledOption.isSelected) {
      try OllamaHttp.endpoint(url.getText.trim, "")
      catch { case error: java.io.IOException => throw new ConfigurationException(error.getMessage) }
      if (model.getText.trim.isEmpty) throw new ConfigurationException("Enter an installed chat model for failure explanations.")
    }
    token.apply()
    settings.failureExplanations = enabledOption.isSelected
    settings.explanationUrl = url.getText.trim
    settings.explanationModel = model.getText.trim
  }
  def reset(): Unit = {
    enabledOption.setSelected(settings.failureExplanations)
    url.setText(settings.explanationUrl)
    model.setText(settings.explanationModel)
    token.reset()
    controls()
  }
}
