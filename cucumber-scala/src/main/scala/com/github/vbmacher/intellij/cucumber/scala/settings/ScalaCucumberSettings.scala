package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
import com.intellij.openapi.components.{PersistentStateComponent, State, Storage}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

import javax.swing.{BoxLayout, JCheckBox, JComponent, JPanel}
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
  override def getState: ScalaCucumberSettings = this
  override def loadState(state: ScalaCucumberSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object ScalaCucumberSettings {
  def getInstance(project: Project): ScalaCucumberSettings = project.getService(classOf[ScalaCucumberSettings])
}

class ScalaCucumberConfigurable(project: Project) extends Configurable {
  private val history = new JCheckBox("Retain local scenario history (workspace only)")
  private val retention = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(200, 0, 10000, 50))
  private val results = new JCheckBox("Show run results in feature gutters and documentation")
  private val clearResults = new javax.swing.JButton("Clear Editor Results")
  private val clearHistory = new javax.swing.JButton("Clear Run History")
  clearResults.addActionListener(_ => RunResults.getInstance(project).clearDecorations())
  clearHistory.addActionListener(_ => RunResults.getInstance(project).clearHistory())
  private val categories = new javax.swing.JTextArea(3, 60)
  private val requirements = new javax.swing.JTextArea(5, 60)
  private val tables = new JCheckBox("Use Tab / Shift-Tab / Enter to navigate and extend Gherkin tables")
  private val reuse = new JCheckBox("Rank step completion by matching scenario count")
  private val context = new JCheckBox("Rank step completion by module and shared scenario tags")
  private val wording = new JCheckBox("Suggest wording from feature files in the current module")
  private val ollamaUrl = new javax.swing.JTextField()
  private val embeddingModel = new javax.swing.JTextField()
  private def settings = ScalaCucumberSettings.getInstance(project)
  override def getDisplayName: String = "Scala Cucumber"
  override def createComponent(): JComponent = {
    val panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    Seq(reuse, context, wording, tables, results, history, retention, clearResults, clearHistory).foreach(panel.add)
    panel.add(new javax.swing.JLabel("Scenario categories: category name => Cucumber tag expression"))
    panel.add(new javax.swing.JScrollPane(categories))
    panel.add(new javax.swing.JLabel("Requirement mappings: tag regex => relative Markdown path#id or URL template"))
    panel.add(new javax.swing.JScrollPane(requirements))
    panel.add(new javax.swing.JLabel("Scenario semantic search · Ollama URL"))
    ollamaUrl.setToolTipText("Scenario text and queries are sent here only when semantic search or index rebuild is requested.")
    panel.add(ollamaUrl)
    panel.add(new javax.swing.JLabel("Embedding model (already installed in Ollama)"))
    embeddingModel.setToolTipText("An embedding model converts text into vectors for similarity search. Changing it requires rebuilding the index.")
    panel.add(embeddingModel)
    panel
  }
  override def isModified: Boolean = ollamaUrl.getText != settings.ollamaUrl || embeddingModel.getText != settings.embeddingModel || categories.getText != settings.tagCategories || results.isSelected != settings.resultDecorations || history.isSelected != settings.runHistory || retention.getValue.asInstanceOf[Integer].intValue() != settings.historyRetention || reuse.isSelected != settings.rankByReuse || context.isSelected != settings.rankByContext || wording.isSelected != settings.featureWording || tables.isSelected != settings.tableNavigation || requirements.getText != settings.requirementMappings
  override def apply(): Unit = {
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
  }
}
