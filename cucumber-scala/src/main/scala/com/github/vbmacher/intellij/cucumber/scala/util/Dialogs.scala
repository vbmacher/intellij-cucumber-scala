package com.github.vbmacher.intellij.cucumber.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

import java.awt.{BorderLayout, GridLayout}
import javax.swing.{JComponent, JLabel, JPanel, JScrollPane, JTextArea}

object Dialogs {

  /** Shows read-only `source` and returns whether the user confirmed it. */
  def confirm(project: Project, title: String, source: String): Boolean =
    show(project, title, new JScrollPane(textArea(source, editable = false)))

  /** Shows editable `source` and returns the confirmed text. */
  def edit(project: Project, title: String, source: String): Option[String] = {
    val text = textArea(source, editable = true)
    if (show(project, title, new JScrollPane(text))) Some(text.getText) else None
  }

  /** Shows an optional description above labeled `fields` and returns whether the user confirmed it. */
  def form(project: Project, title: String, description: Option[String], fields: (String, JComponent)*): Boolean = {
    val form = new JPanel(new GridLayout(0, 2))
    fields.foreach { case (label, field) => form.add(new JLabel(label)); form.add(field) }
    val panel = new JPanel(new BorderLayout(0, 12))
    description.foreach(text => panel.add(new JLabel(text), BorderLayout.NORTH))
    panel.add(form, BorderLayout.CENTER)
    show(project, title, panel)
  }

  private def textArea(source: String, editable: Boolean): JTextArea = {
    val text = new JTextArea(source, 24, 90)
    text.setEditable(editable)
    text.setCaretPosition(0)
    text
  }

  private def show(project: Project, title: String, content: JComponent): Boolean = new DialogWrapper(project) {
    setTitle(title)
    init()
    override def createCenterPanel(): JComponent = content
  }.showAndGet()
}
