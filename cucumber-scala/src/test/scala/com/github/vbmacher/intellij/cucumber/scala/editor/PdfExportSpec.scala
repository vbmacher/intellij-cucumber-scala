package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.publish.JcefPdfExporter
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.jcef.JBCefApp
import org.junit.{Assume, Test}
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import scala.util.Try

@RunWith(classOf[JUnit4])
class PdfExportSpec extends ScCucumberSpecBase {
  private def requireJcef(): Unit = {
    Registry.get("ide.browser.jcef.headless.enabled").setValue(true, getTestRootDisposable)
    Registry.get("ide.browser.jcef.testMode.enabled").setValue(true, getTestRootDisposable)
    Assume.assumeTrue("Run this integration fixture with the bundled JCEF-capable JetBrains Runtime", JBCefApp.isSupported())
  }
  @Test
  def testActualPdfRendererAndCancellationPreserveDestination(): Unit = {
    requireJcef()
    val destination = Files.createTempFile("cucumber-render-test-", ".pdf")
    try {
      val outcome = new AtomicReference[Try[Unit]]()
      JcefPdfExporter.render(destination, "<!doctype html><html><body><h1>Local specification</h1><p>Alice &amp; Bob</p></body></html>", outcome.set)
      PlatformTestUtil.waitWithEventsDispatching("JCEF PDF export", () => outcome.get() != null, 60000)
      outcome.get().get
      new String(Files.readAllBytes(destination).take(5), java.nio.charset.StandardCharsets.US_ASCII) shouldBe "%PDF-"
      val original = Files.readAllBytes(destination).toSeq
      outcome.set(null)
      val cancel = JcefPdfExporter.render(destination, "<html><body>Cancelled replacement</body></html>", outcome.set)
      cancel()
      PlatformTestUtil.waitWithEventsDispatching("JCEF cancellation", () => outcome.get() != null, 10000)
      outcome.get().isFailure shouldBe true
      Files.readAllBytes(destination).toSeq shouldBe original
    } finally Files.deleteIfExists(destination)
  }
}
