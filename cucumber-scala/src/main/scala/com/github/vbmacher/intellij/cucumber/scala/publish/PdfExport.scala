package com.github.vbmacher.intellij.cucumber.scala.publish

import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.{JBCefApp, JBCefBrowser}
import org.cef.browser.{CefBrowser, CefFrame}
import org.cef.handler.{CefLoadHandler, CefLoadHandlerAdapter}
import org.cef.misc.CefPdfPrintSettings

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.concurrent.{CancellationException, ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.{JComponent, JLabel}
import scala.util.{Failure, Success, Try}

trait FeaturePdfExporter {
  def write(project: Project, destination: Path, html: String): Unit
}

class JcefPdfExporter extends FeaturePdfExporter {
  override def write(project: Project, destination: Path, html: String): Unit = {
    if (!JBCefApp.isSupported()) {
      Messages.showErrorDialog(project, "PDF export requires a JCEF-capable JetBrains Runtime. Export HTML or switch the IDE runtime.", "Export Features")
      return
    }
    var cancel: () => Unit = () => ()
    val dialog = new DialogWrapper(project) {
      setTitle("Exporting PDF")
      init()
      override def createActions(): Array[javax.swing.Action] = Array(getCancelAction)
      override def createCenterPanel(): JComponent = new JLabel("Rendering the local specification. Cancel preserves the destination.")
      override def doCancelAction(): Unit = { cancel(); super.doCancelAction() }
    }
    cancel = JcefPdfExporter.render(destination, html, result => {
      dialog.close(0)
      result.failed.toOption.filterNot(_.isInstanceOf[CancellationException]).foreach(e => Messages.showErrorDialog(project, e.getMessage, "Export Features"))
    })
    dialog.show()
  }
}

object JcefPdfExporter {
  /** UI-thread entry point shared by the export dialog and the JCEF integration fixture. */
  private[scala] def render(destination: Path, html: String, completed: Try[Unit] => Unit): () => Unit = {
    val temporary = Files.createTempFile(destination.toAbsolutePath.getParent, ".cucumber-export-", ".pdf")
    val browser = JBCefBrowser.createBuilder().setOffScreenRendering(true).build()
    val finished = new AtomicBoolean(false)
    val printing = new AtomicBoolean(false)
    var timeout: ScheduledFuture[_] = null
    def finish(outcome: Try[Unit]): Unit = ApplicationManager.getApplication.invokeLater(() => {
      if (finished.compareAndSet(false, true)) {
        Option(timeout).foreach(_.cancel(false))
        val result = outcome.flatMap { _ => Try {
          if (Files.size(temporary) < 5) throw new java.io.IOException("The PDF renderer produced an empty file.")
          Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          ()
        } }
        Disposer.dispose(browser)
        Files.deleteIfExists(temporary)
        completed(result)
      } else {
        // A renderer callback may arrive after cancellation and recreate its temporary file.
        Files.deleteIfExists(temporary)
        ()
      }
    }, ModalityState.any())
    val sourceUrl = "http://localhost/scala-cucumber-export"
    // loadHTML is served through the IDE's local file-scheme handler; its URL retains the requested origin in a fragment.
    def ownDocument(url: String): Boolean = url == sourceUrl || url.endsWith("#url=" + sourceUrl)
    browser.getJBCefClient.addLoadHandler(new CefLoadHandlerAdapter {
      override def onLoadError(cef: CefBrowser, frame: CefFrame, code: CefLoadHandler.ErrorCode, text: String, url: String): Unit =
        if (frame.isMain && ownDocument(url)) finish(Failure(new java.io.IOException("Could not render the local specification: " + text)))
      override def onLoadEnd(cef: CefBrowser, frame: CefFrame, status: Int): Unit = {
        if (frame.isMain && ownDocument(cef.getURL) && !finished.get() && printing.compareAndSet(false, true)) {
          val settings = new CefPdfPrintSettings()
          settings.print_background = true
          cef.printToPDF(temporary.toString, settings, (_: String, success: Boolean) =>
            finish(if (success) Success(()) else Failure(new java.io.IOException("The IDE PDF renderer could not complete the export."))))
        }
      }
    }, browser.getCefBrowser)
    timeout = com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService.schedule(new Runnable {
      override def run(): Unit = finish(Failure(new java.io.IOException("PDF rendering timed out; the destination was preserved.")))
    }, 60, TimeUnit.SECONDS)
    browser.createImmediately()
    browser.loadHTML(html, sourceUrl)
    () => finish(Failure(new CancellationException("PDF export cancelled")))
  }
}
