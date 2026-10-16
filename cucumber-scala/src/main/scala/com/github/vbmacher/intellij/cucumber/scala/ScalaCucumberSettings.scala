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
  private val reuse = new JCheckBox("Rank step completion by matching scenario count")
  private def settings = project.getService(classOf[ScalaCucumberSettings])
  override def getDisplayName: String = "Scala Cucumber"
  override def createComponent(): JComponent = {
    val panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    Seq(reuse, results, clearResults).foreach(panel.add)
    panel
  }
  override def isModified: Boolean = results.isSelected != settings.resultDecorations || reuse.isSelected != settings.rankByReuse
  override def apply(): Unit = {
    settings.resultDecorations = results.isSelected
    settings.rankByReuse = reuse.isSelected
  }
  override def reset(): Unit = {
    results.setSelected(settings.resultDecorations)
    reuse.setSelected(settings.rankByReuse)
  }
}
