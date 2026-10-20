package com.github.vbmacher.intellij.cucumber.scala.settings

import com.github.vbmacher.intellij.cucumber.scala.util.{OllamaCredentials, OllamaHttp}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter

import javax.swing.{JPasswordField, JTextField}
import javax.swing.event.DocumentEvent
import scala.util.control.NonFatal

private[settings] class AccessTokenSettings(project: Project, purpose: String, accessibleName: String, url: JTextField)
  extends JPasswordField(28) {
  private val credentials = new OllamaCredentials(project, purpose)
  private var savedToken = ""
  private var savedUrl = ""
  getAccessibleContext.setAccessibleName(accessibleName)
  setToolTipText("Saved tokens are masked. Clear this field and click Apply to remove the token for the URL above.")
  // A token for one server must not be carried over to another server.
  url.getDocument.addDocumentListener(new DocumentAdapter {
    override def textChanged(event: DocumentEvent): Unit = if (url.getText.trim != savedUrl) setText("")
  })

  private def value: String = {
    val password = getPassword
    try new String(password)
    finally java.util.Arrays.fill(password, '\u0000')
  }

  def isModified: Boolean = value != savedToken || url.getText.trim != savedUrl

  private def passwordSafe[A](operation: => A): A = {
    var result: Option[A] = None
    // Password Safe may block. Finish the operation before returning to settings.
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable {
      override def run(): Unit = {
        try result = Some(operation)
        catch { case NonFatal(_) => () }
      }
    }, "Access token", false, project)
    result.getOrElse(throw new ConfigurationException("Could not access the token. Check IDE Password Safe settings."))
  }

  private def validUrl: Boolean = {
    try {
      OllamaHttp.endpoint(url.getText.trim, "")
      true
    } catch { case _: java.io.IOException => false }
  }

  def apply(): Unit = if (isModified) {
    val token = value
    if (validUrl) passwordSafe(credentials.save(url.getText.trim, Option(token).filter(_.nonEmpty)))
    else if (token.nonEmpty) throw new ConfigurationException("Enter a valid Ollama URL before saving an access token.")
    savedToken = token
    savedUrl = url.getText.trim
  }

  def reset(): Unit = {
    savedUrl = url.getText.trim
    savedToken = if (validUrl) passwordSafe(credentials.token(savedUrl)).getOrElse("") else ""
    setText(savedToken)
  }
}
