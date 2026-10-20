package com.github.vbmacher.intellij.cucumber.scala.publish

import com.intellij.ide.AppLifecycleListener
import com.intellij.util.net.HttpConfigurable

final class JcefProxyStartup extends AppLifecycleListener {
  override def appFrameCreated(commandLineArgs: java.util.List[String]): Unit = {
    // IJPL-212214: initialize proxy migration before restored editors can initialize JBCefApp$Holder.
    // JCEF in the supported 262 SDK still reads this legacy service when starting its browser.
    HttpConfigurable.getInstance()
  }
}
