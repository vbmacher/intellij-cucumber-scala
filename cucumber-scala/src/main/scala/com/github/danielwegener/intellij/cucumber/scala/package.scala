package com.github.danielwegener.intellij.cucumber

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NullableComputable;

package object scala {

  def inReadAction[T](body: => T): T = ApplicationManager.getApplication match {
    case application if application.isReadAccessAllowed => body
    case application => application.runReadAction(new NullableComputable[T] {
      override def compute(): T = body
    })
  }
}
