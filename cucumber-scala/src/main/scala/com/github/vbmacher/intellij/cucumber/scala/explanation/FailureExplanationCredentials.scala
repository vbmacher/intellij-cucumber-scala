package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.util.OllamaHttp
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project

object FailureExplanationCredentials {
  private[explanation] def attributes(project: Project, url: String): CredentialAttributes =
    new CredentialAttributes("Scala Cucumber failure explanation · " + project.getLocationHash + " · " + OllamaHttp.endpoint(url, ""))

  // Password Safe is blocking: callers run these operations in background tasks.
  def token(project: Project, url: String): Option[String] =
    Option(PasswordSafe.getInstance().getPassword(attributes(project, url))).filter(_.nonEmpty)

  def save(project: Project, url: String, token: Option[String]): Unit =
    PasswordSafe.getInstance().setPassword(attributes(project, url), token.filter(_.nonEmpty).orNull)
}
