package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.{JComponent, JScrollPane, JTextArea}

object EditPreview {
  def confirm(project: Project, title: String, source: String): Boolean = new DialogWrapper(project) {
    setTitle(title)
    init()
    override def createCenterPanel(): JComponent = {
      val text = new JTextArea(source, 24, 90)
      text.setEditable(false)
      text.setCaretPosition(0)
      new JScrollPane(text)
    }
  }.showAndGet()
}
