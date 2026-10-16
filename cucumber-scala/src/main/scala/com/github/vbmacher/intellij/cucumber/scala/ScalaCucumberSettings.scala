package com.github.vbmacher.intellij.cucumber.scala

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
  override def getState: ScalaCucumberSettings = this
  override def loadState(state: ScalaCucumberSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

class ScalaCucumberConfigurable(project: Project) extends Configurable {
  private val results = new JCheckBox("Show run results in feature gutters and documentation")
  private val clearResults = new javax.swing.JButton("Clear Editor Results")
  clearResults.addActionListener(_ => project.getService(classOf[RunResults]).clearDecorations())
  private val categories = new javax.swing.JTextArea(3, 60)
  private val tables = new JCheckBox("Use Tab / Shift-Tab / Enter to navigate and extend Gherkin tables")
  private val reuse = new JCheckBox("Rank step completion by matching scenario count")
  private val context = new JCheckBox("Rank step completion by module and shared scenario tags")
  private val wording = new JCheckBox("Suggest wording from feature files in the current module")
  private def settings = project.getService(classOf[ScalaCucumberSettings])
  override def getDisplayName: String = "Scala Cucumber"
  override def createComponent(): JComponent = {
    val panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    Seq(reuse, context, wording, tables, results, clearResults).foreach(panel.add)
    panel.add(new javax.swing.JLabel("Scenario categories: category name => Cucumber tag expression"))
    panel.add(new javax.swing.JScrollPane(categories))
    panel
  }
  override def isModified: Boolean = categories.getText != settings.tagCategories || results.isSelected != settings.resultDecorations || reuse.isSelected != settings.rankByReuse || context.isSelected != settings.rankByContext || wording.isSelected != settings.featureWording || tables.isSelected != settings.tableNavigation
  override def apply(): Unit = {
    settings.resultDecorations = results.isSelected
    settings.tagCategories = categories.getText
    settings.tableNavigation = tables.isSelected
    settings.rankByReuse = reuse.isSelected
    settings.rankByContext = context.isSelected
    settings.featureWording = wording.isSelected
  }
  override def reset(): Unit = {
    results.setSelected(settings.resultDecorations)
    categories.setText(settings.tagCategories)
    tables.setSelected(settings.tableNavigation)
    reuse.setSelected(settings.rankByReuse)
    context.setSelected(settings.rankByContext)
    wording.setSelected(settings.featureWording)
  }
}
