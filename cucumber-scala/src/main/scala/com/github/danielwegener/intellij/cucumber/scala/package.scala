package com.github.danielwegener.intellij.cucumber

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

package object scala {

  def inReadAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(body)
  }

  def inWriteAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isWriteAccessAllowed => body
    case application => application.runWriteAction(body)
  }

  def invokeAndWait[T](body: => T): Unit = {
    ApplicationManager.getApplication.invokeAndWait(() => body)
  }

  def isUnitTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  private[this] implicit def toComputable[T](action: => T): Computable[T] = () => action
}
