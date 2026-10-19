package com.github.vbmacher.intellij.cucumber.scala.util

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project

final class OllamaCredentials(project: Project, purpose: String) {
  private def attributes(url: String): CredentialAttributes =
    new CredentialAttributes(s"Scala Cucumber $purpose · ${project.getLocationHash} · ${OllamaHttp.endpoint(url, "")}")

  // Password Safe is blocking: callers run these operations in background tasks.
  def token(url: String): Option[String] =
    Option(PasswordSafe.getInstance().getPassword(attributes(url))).filter(_.nonEmpty)

  def save(url: String, token: Option[String]): Unit =
    PasswordSafe.getInstance().setPassword(attributes(url), token.filter(_.nonEmpty).orNull)
}
