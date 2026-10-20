package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaHttp
import com.intellij.openapi.components.{PersistentStateComponent, State, Storage}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.{JBLabel, JBScrollPane}
import com.intellij.util.ui.{FormBuilder, JBUI, UIUtil}
import com.intellij.util.xmlb.XmlSerializerUtil

import java.awt.{BorderLayout, Dimension, FlowLayout, Rectangle}
import javax.swing.{JCheckBox, JComponent, JPanel, Scrollable, ScrollPaneConstants}
import scala.beans.BeanProperty

@State(name = "ScalaCucumberSettings", storages = Array(new Storage("scala-cucumber.xml")))
class ScalaCucumberSettings extends PersistentStateComponent[ScalaCucumberSettings] {
  @BeanProperty var runHistory: Boolean = false
  @BeanProperty var historyRetention: Int = 200
  @BeanProperty var resultDecorations: Boolean = true
  @BeanProperty var rankByReuse: Boolean = true
  @BeanProperty var rankByContext: Boolean = true
  @BeanProperty var tagCategories: String = ""
  @BeanProperty var requirementMappings: String = ""
  @BeanProperty var tableNavigation: Boolean = false
  @BeanProperty var featureWording: Boolean = true
  @BeanProperty var ollamaUrl: String = "http://localhost:11434"
  @BeanProperty var embeddingModel: String = "embeddinggemma"
  @BeanProperty var failureExplanations: Boolean = false
  @BeanProperty var explanationUrl: String = "http://localhost:11434"
  @BeanProperty var explanationModel: String = "llama3.2:3b"
  @BeanProperty var scenarioDrafts: Boolean = false
  @BeanProperty var draftUrl: String = "http://localhost:11434"
  @BeanProperty var draftModel: String = "llama3.2:3b"
  override def getState: ScalaCucumberSettings = this
  override def loadState(state: ScalaCucumberSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object ScalaCucumberSettings {
  def getInstance(project: Project): ScalaCucumberSettings = project.getService(classOf[ScalaCucumberSettings])
}

class ScalaCucumberConfigurable(project: Project) extends Configurable {
  private val history = new JCheckBox("Keep scenario run history in this workspace")
  private val retention = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(200, 0, 10000, 50))
  private val results = new JCheckBox("Show run results in feature gutters and documentation")
  private val clearResults = new javax.swing.JButton("Clear Editor Results")
  private val clearHistory = new javax.swing.JButton("Clear Run History")
  clearResults.addActionListener(_ => RunResults.getInstance(project).clearEditorResults())
  clearHistory.addActionListener(_ => RunResults.getInstance(project).clearHistory())
  private val categories = new javax.swing.JTextArea(3, 36)
  private val requirements = new javax.swing.JTextArea(3, 36)
  private val tables = new JCheckBox("Navigate table cells with Tab, Shift-Tab and Enter")
  private val reuse = new JCheckBox("Show frequently used step definitions higher in autocomplete")
  private val context = new JCheckBox("Show steps from this module or with shared tags higher in autocomplete")
  private val wording = new JCheckBox("Include unimplemented steps from existing feature files in autocomplete")
  private val ollamaUrl = new javax.swing.JTextField(28)
  private val embeddingModel = new javax.swing.JTextField(28)
  private val explanations = new FailureExplanationSettings(project)
  private val drafts = new JCheckBox("Enable edge-case scenario drafts")
  private val draftUrl = new javax.swing.JTextField(28)
  private val draftModel = new javax.swing.JTextField(28)
  private val draftToken = new AccessTokenSettings(project, "scenario drafts", "Edge-case draft access token", draftUrl)
  private def updateDraftControls(): Unit = Seq(draftUrl, draftModel, draftToken).foreach(_.setEnabled(drafts.isSelected))
  drafts.addActionListener(_ => updateDraftControls())
  private val retentionLabel = new JBLabel("Maximum history entries:")
  private def updateHistoryControls(): Unit = {
    retention.setEnabled(history.isSelected)
    retentionLabel.setEnabled(history.isSelected)
  }
  history.addActionListener(_ => updateHistoryControls())
  private def settings = ScalaCucumberSettings.getInstance(project)
  override def getDisplayName: String = "Scala Cucumber"
  override def createComponent(): JComponent = {
    def hint(text: String): JBLabel =
      new JBLabel(text, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER).setAllowAutoWrapping(true)

    def row(components: JComponent*): JPanel = {
      val panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
      components.foreach(panel.add)
      panel
    }

    def editor(area: javax.swing.JTextArea): JBScrollPane = {
      area.setLineWrap(true)
      area.setBorder(JBUI.Borders.empty(5))
      new JBScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    }

    retention.setToolTipText("Maximum stored scenario results. Oldest entries are removed first; 0 keeps none.")
    clearResults.setToolTipText("Immediately clear run results shown in feature gutters and documentation.")
    clearHistory.setToolTipText("Immediately delete the scenario run history stored in this workspace.")
    ollamaUrl.setToolTipText("Scenario text and queries are sent here only when semantic search or index rebuild is requested.")
    embeddingModel.setToolTipText("An embedding model converts text into vectors for similarity search. Changing it requires rebuilding the index.")
    drafts.setToolTipText("Offer Suggest edge cases in feature files. Context is sent only after review and explicit generation.")
    draftUrl.setToolTipText("Address of a running local or remote Ollama-compatible server, independent of semantic search and failure explanations.")
    draftModel.setToolTipText("An installed chat model that supports JSON output, not an embedding model.")
    updateDraftControls()
    retentionLabel.setLabelFor(retention)
    updateHistoryControls()

    val form = FormBuilder.createFormBuilder()
      .addComponent(new TitledSeparator("Editor and completion"), 0)
      .addComponent(hint("Autocomplete in .feature files. Ranking changes suggestion order, not which Scala definition runs."))
      .addComponent(reuse)
      .addComponent(hint("Gives a larger boost to definitions used by more scenarios."), 0)
      .addComponent(context)
      .addComponent(hint("Raises definitions in this IntelliJ module and those used by scenarios sharing tags with yours, such as @payments."), 0)
      .addComponent(wording)
      .addComponent(hint("Suggests step text with no matching Scala definition from this module and its dependencies. Marked Feature wording · undefined; accepting inserts text only."), 0)
      .addComponent(tables)
      .addComponent(hint("Tab: next cell; Shift-Tab: previous cell; Enter: cell below. Moving past the last row adds an empty row."), 0)
      .addComponent(new TitledSeparator("Run results and history"), 16)
      .addComponent(results)
      .addComponent(history)
      .addComponent(row(retentionLabel, retention))
      .addComponent(row(clearResults, clearHistory))
      .addComponent(new TitledSeparator("Tags and requirements"), 16)
      .addComponent(hint("Both settings are optional. Leave a field empty if you do not use it."))
      .addLabeledComponent("Scenario categories:", editor(categories), true)
      .addComponent(hint("Group tagged scenarios in Scala Cucumber → Suite: select Group → Category."))
      .addComponent(hint("One per line: category name => tag expression. Example: Smoke => @smoke"), 0)
      .addLabeledComponent("Requirement mappings:", editor(requirements), true)
      .addComponent(hint("Open documentation or issues from feature tags with Go to Declaration."))
      .addComponent(hint("One per line: tag regular expression => Markdown path#id or URL."), 0)
      .addComponent(hint("Example: @REQ-(.*) => docs/requirements.md#$1"), 0)
      .addComponent(hint("For @REQ-PAY-12, (.*) captures PAY-12; $1 inserts it as the Markdown heading or anchor ID."), 0)
      .addComponent(new TitledSeparator("Scenario semantic search"), 16)
      .addLabeledComponent("Ollama URL:", ollamaUrl)
      .addLabeledComponent("Embedding model:", embeddingModel)
      .addComponent(hint("Use a model already installed in Ollama. Rebuild the index after changing it."))
      .addComponent(explanations, 16)
      .addComponent(new TitledSeparator("Edge-case drafts"), 16)
      .addComponent(drafts)
      .addComponent(hint("Suggest additional Gherkin scenarios for boundary conditions and missing cases. Review drafts and choose which to insert; they have not been executed."))
      .addComponent(hint("Right-click a feature, Rule or scenario and choose Suggest edge cases…"))
      .addComponent(hint("Review or redact the context, then click Generate drafts to send it to the server below."))
      .addLabeledComponent("Ollama URL:", draftUrl)
      .addLabeledComponent("Chat model:", draftModel)
      .addComponent(hint("Use a chat model already installed on that server with JSON output support, such as llama3.2:3b. The plugin does not download models."))
      .addLabeledComponent("Access token (optional):", draftToken)
      .addComponent(hint("Local Ollama needs no token: leave this blank. Only protected remote servers need a bearer token; draft tokens are masked and separate from failure-explanation tokens."))
      .getPanel
    draftUrl.getAccessibleContext.setAccessibleName("Edge-case draft Ollama URL")
    draftModel.getAccessibleContext.setAccessibleName("Edge-case draft chat model")
    form.setBorder(JBUI.Borders.empty(8, 12, 16, 12))

    // Track the viewport width so only the page scrolls vertically, without stretching control heights.
    val content = new JPanel(new BorderLayout()) with Scrollable {
      add(form, BorderLayout.NORTH)
      override def getPreferredScrollableViewportSize: Dimension = getPreferredSize
      override def getScrollableTracksViewportWidth: Boolean = true
      override def getScrollableTracksViewportHeight: Boolean = false
      override def getScrollableUnitIncrement(rect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(20)
      override def getScrollableBlockIncrement(rect: Rectangle, orientation: Int, direction: Int): Int = rect.height
    }
    val scroll = new JBScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scroll.setBorder(JBUI.Borders.empty())
    scroll
  }
  override def isModified: Boolean = draftToken.isModified || drafts.isSelected != settings.scenarioDrafts || draftUrl.getText.trim != settings.draftUrl || draftModel.getText.trim != settings.draftModel || explanations.isModified || ollamaUrl.getText.trim != settings.ollamaUrl || embeddingModel.getText.trim != settings.embeddingModel || categories.getText != settings.tagCategories || results.isSelected != settings.resultDecorations || history.isSelected != settings.runHistory || retention.getValue.asInstanceOf[Integer].intValue() != settings.historyRetention || reuse.isSelected != settings.rankByReuse || context.isSelected != settings.rankByContext || wording.isSelected != settings.featureWording || tables.isSelected != settings.tableNavigation || requirements.getText != settings.requirementMappings
  override def apply(): Unit = {
    if (drafts.isSelected) {
      try OllamaHttp.endpoint(draftUrl.getText.trim, "")
      catch { case error: java.io.IOException => throw new com.intellij.openapi.options.ConfigurationException(error.getMessage) }
      if (draftModel.getText.trim.isEmpty) throw new com.intellij.openapi.options.ConfigurationException("Enter an installed chat model for edge-case drafts.")
    }
    explanations.apply()
    draftToken.apply()
    settings.scenarioDrafts = drafts.isSelected
    settings.draftUrl = draftUrl.getText.trim
    settings.draftModel = draftModel.getText.trim
    settings.resultDecorations = results.isSelected
    settings.runHistory = history.isSelected
    settings.historyRetention = retention.getValue.asInstanceOf[Integer].intValue()
    settings.tagCategories = categories.getText
    settings.requirementMappings = requirements.getText
    settings.tableNavigation = tables.isSelected
    settings.rankByReuse = reuse.isSelected
    settings.rankByContext = context.isSelected
    settings.featureWording = wording.isSelected
    settings.ollamaUrl = ollamaUrl.getText.trim
    settings.embeddingModel = embeddingModel.getText.trim
    RunResults.getInstance(project).retentionChanged()
  }
  override def reset(): Unit = {
    results.setSelected(settings.resultDecorations)
    history.setSelected(settings.runHistory)
    retention.setValue(Integer.valueOf(settings.historyRetention))
    categories.setText(settings.tagCategories)
    requirements.setText(settings.requirementMappings)
    tables.setSelected(settings.tableNavigation)
    reuse.setSelected(settings.rankByReuse)
    context.setSelected(settings.rankByContext)
    wording.setSelected(settings.featureWording)
    ollamaUrl.setText(settings.ollamaUrl)
    embeddingModel.setText(settings.embeddingModel)
    explanations.reset()
    drafts.setSelected(settings.scenarioDrafts)
    draftUrl.setText(settings.draftUrl)
    draftModel.setText(settings.draftModel)
    draftToken.reset()
    updateDraftControls()
    updateHistoryControls()
  }
}
