package com.github.vbmacher.intellij.cucumber.scala.publish

import com.intellij.ide.ui.{LafManager, LafManagerListener}
import com.intellij.openapi.application.{ApplicationManager, ModalityState, ReadAction}
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinFileType}

import java.beans.PropertyChangeListener
import javax.swing.{JComponent, JEditorPane, JScrollPane}
import javax.swing.event.HyperlinkEvent

class FeaturePreviewProvider extends FileEditorProvider with DumbAware {
  override def accept(project: Project, file: VirtualFile): Boolean = file.getFileType == GherkinFileType.INSTANCE
  override def createEditor(project: Project, file: VirtualFile): FileEditor = {
    val editor = TextEditorProvider.getInstance().createEditor(project, file).asInstanceOf[TextEditor]
    new TextEditorWithPreview(editor, new FeaturePreviewEditor(project, file), "Feature", TextEditorWithPreview.Layout.SHOW_EDITOR)
  }
  override def getEditorTypeId: String = "ScalaCucumberFeaturePreview"
  override def getPolicy: FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class FeaturePreviewEditor(project: Project, file: VirtualFile) extends UserDataHolderBase with FileEditor {
  private val html = new JEditorPane("text/html", "Loading local feature preview…")
  html.setEditable(false)
  private val panel = new JScrollPane(html)
  private val queue = new MergingUpdateQueue("Scala Cucumber feature preview", 300, true, panel, this)
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[String]] = None
  private var disposed = false
  html.addHyperlinkListener(event => {
    if (event.getEventType == HyperlinkEvent.EventType.ACTIVATED && event.getDescription.startsWith("source:"))
      event.getDescription.stripPrefix("source:").toIntOption.foreach(offset => new OpenFileDescriptor(project, file, offset).navigate(true))
  })
  Option(FileDocumentManager.getInstance().getDocument(file)).foreach(_.addDocumentListener(new DocumentListener {
    override def documentChanged(event: DocumentEvent): Unit = refresh()
  }, this))
  ApplicationManager.getApplication.getMessageBus.connect(this).subscribe[LafManagerListener](LafManagerListener.TOPIC, (_: LafManager) => refresh())
  refresh()
  private def refresh(): Unit = queue.queue(new Update("render") {
    override def run(): Unit = {
      pending.foreach(_.cancel())
      pending = Some(ReadAction.nonBlocking[String](() => {
        PsiManager.getInstance(project).findFile(file) match {
          case feature: GherkinFile => FeatureHtml.render(feature, dark = UIUtil.isUnderDarcula)
          case _ => "<html><body>Feature source is unavailable.</body></html>"
        }
      }).expireWith(FeaturePreviewEditor.this).finishOnUiThread(ModalityState.defaultModalityState(), source => {
        val position = panel.getVerticalScrollBar.getValue
        html.setText(source)
        panel.getVerticalScrollBar.setValue(position)
      }).submit(AppExecutorUtil.getAppExecutorService))
    }
  })
  override def getComponent: JComponent = panel
  override def getPreferredFocusedComponent: JComponent = html
  override def getName: String = "Preview"
  override def getFile: VirtualFile = file
  override def setState(state: FileEditorState): Unit = ()
  override def isModified: Boolean = false
  override def isValid: Boolean = !disposed && file.isValid
  override def addPropertyChangeListener(listener: PropertyChangeListener): Unit = ()
  override def removePropertyChangeListener(listener: PropertyChangeListener): Unit = ()
  override def dispose(): Unit = { disposed = true; pending.foreach(_.cancel()) }
}
