package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
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
  clearResults.addActionListener(_ => RunResults.getInstance(project).clearDecorations())
  clearHistory.addActionListener(_ => RunResults.getInstance(project).clearHistory())
  private val categories = new javax.swing.JTextArea(3, 36)
  private val requirements = new javax.swing.JTextArea(3, 36)
  private val tables = new JCheckBox("Navigate and extend Gherkin tables with Tab, Shift-Tab and Enter")
  private val reuse = new JCheckBox("Prefer steps used by more scenarios")
  private val context = new JCheckBox("Prefer steps from the same module and with shared tags")
  private val wording = new JCheckBox("Suggest wording from feature files in the current module")
  private val ollamaUrl = new javax.swing.JTextField(28)
  private val embeddingModel = new javax.swing.JTextField(28)
  private val explanations = new FailureExplanationSettings(project)
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

    reuse.setToolTipText("Rank step completion by the number of matching scenarios.")
    context.setToolTipText("Rank step completion by module proximity and shared scenario tags.")
    retention.setToolTipText("Maximum stored scenario results. Oldest entries are removed first; 0 keeps none.")
    clearResults.setToolTipText("Immediately clear run results shown in feature gutters and documentation.")
    clearHistory.setToolTipText("Immediately delete the scenario run history stored in this workspace.")
    ollamaUrl.setToolTipText("Scenario text and queries are sent here only when semantic search or index rebuild is requested.")
    embeddingModel.setToolTipText("An embedding model converts text into vectors for similarity search. Changing it requires rebuilding the index.")
    retentionLabel.setLabelFor(retention)
    updateHistoryControls()

    val form = FormBuilder.createFormBuilder()
      .addComponent(new TitledSeparator("Editor and completion"), 0)
      .addComponent(reuse)
      .addComponent(context)
      .addComponent(wording)
      .addComponent(tables)
      .addComponent(new TitledSeparator("Run results and history"), 16)
      .addComponent(results)
      .addComponent(history)
      .addComponent(row(retentionLabel, retention))
      .addComponent(row(clearResults, clearHistory))
      .addComponent(new TitledSeparator("Tags and requirements"), 16)
      .addLabeledComponent("Scenario categories:", editor(categories), true)
      .addComponent(hint("One per line: category name => tag expression. Example: Smoke => @smoke"))
      .addLabeledComponent("Requirement mappings:", editor(requirements), true)
      .addComponent(hint("One per line: tag regex => Markdown path#id or URL template."))
      .addComponent(hint("Example: @REQ-(.*) => docs/requirements.md#$1"), 0)
      .addComponent(new TitledSeparator("Scenario semantic search"), 16)
      .addLabeledComponent("Ollama URL:", ollamaUrl)
      .addLabeledComponent("Embedding model:", embeddingModel)
      .addComponent(hint("Use a model already installed in Ollama. Rebuild the index after changing it."))
      .addComponent(explanations, 16)
      .getPanel
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
  override def isModified: Boolean = explanations.isModified || ollamaUrl.getText != settings.ollamaUrl || embeddingModel.getText != settings.embeddingModel || categories.getText != settings.tagCategories || results.isSelected != settings.resultDecorations || history.isSelected != settings.runHistory || retention.getValue.asInstanceOf[Integer].intValue() != settings.historyRetention || reuse.isSelected != settings.rankByReuse || context.isSelected != settings.rankByContext || wording.isSelected != settings.featureWording || tables.isSelected != settings.tableNavigation || requirements.getText != settings.requirementMappings
  override def apply(): Unit = {
    explanations.apply()
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
    updateHistoryControls()
  }
}
