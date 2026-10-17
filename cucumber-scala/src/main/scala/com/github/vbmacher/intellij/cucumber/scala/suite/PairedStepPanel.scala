package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{CaretEvent, CaretListener}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, SmartPointerManager, SmartPsiElementPointer}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.plugins.cucumber.psi.GherkinStep

import java.awt.BorderLayout
import javax.swing._

class PairedStepPanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private final case class Candidate(label: String, source: String, pointer: SmartPsiElementPointer[PsiElement]) {
    override def toString: String = label
  }
  private val follow = new JCheckBox("Follow feature caret", false)
  private val pin = new JCheckBox("Pin")
  private val candidates = new JComboBox[Candidate]()
  private val open = new JButton("Go to definition")
  private val controls = new JPanel()
  controls.add(follow); controls.add(pin); controls.add(candidates); controls.add(open)
  private val factory = EditorFactory.getInstance()
  private val viewer = factory.createViewer(factory.createDocument("Enable Follow to inspect the selected Gherkin step."), project)
  add(controls, BorderLayout.NORTH)
  add(viewer.getComponent, BorderLayout.CENTER)
  private val queue = new MergingUpdateQueue("Scala Cucumber paired view", 200, true, this, this)
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[Seq[Candidate]]] = None
  candidates.addActionListener(_ => {
    val value = Option(candidates.getSelectedItem).collect { case c: Candidate => c.source }.getOrElse("Undefined step: no exact Scala definition.")
    com.intellij.openapi.application.ApplicationManager.getApplication.runWriteAction(new Runnable {
      override def run(): Unit = viewer.getDocument.setText(value)
    })
  })
  open.addActionListener(_ => Option(candidates.getSelectedItem).collect { case c: Candidate => c }.flatMap(c => Option(c.pointer.getElement)).foreach { target =>
    new OpenFileDescriptor(project, target.getContainingFile.getVirtualFile, target.getTextOffset).navigate(true)
  })
  factory.getEventMulticaster.addCaretListener(new CaretListener {
    override def caretPositionChanged(event: CaretEvent): Unit = {
      if (follow.isSelected && !pin.isSelected && event.getEditor != viewer) {
        val editor = event.getEditor
        val manager = PsiDocumentManager.getInstance(project)
        Option(manager.getPsiFile(editor.getDocument)).filter(_.isInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile]).foreach { file =>
          queue.queue(new Update("selection") {
            override def run(): Unit = {
              pending.foreach(_.cancel())
              val offset = editor.getCaretModel.getOffset
              pending = Some(ReadAction.nonBlocking[Seq[Candidate]](() => {
                Option(file.findElementAt(offset)).flatMap(e => Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinStep], false))).toSeq.flatMap { step =>
                  matchingSteps(step).flatMap(d => Option(d.getElement)).map { target =>
                    Candidate(target.getContainingFile.getName + ":" + (target.getContainingFile.getText.take(target.getTextOffset).count(_ == '\n') + 1),
                      target.getParent.getText, SmartPointerManager.createPointer(target))
                  }
                }
              }).inSmartMode(project).expireWith(PairedStepPanel.this).finishOnUiThread(ModalityState.defaultModalityState(), values => {
                candidates.removeAllItems()
                values.foreach(candidates.addItem)
                if (values.isEmpty) candidates.setSelectedItem(null)
              }).submit(AppExecutorUtil.getAppExecutorService))
            }
          })
        }
      }
    }
  }, this)
  override def dispose(): Unit = {
    pending.foreach(_.cancel())
    factory.releaseEditor(viewer)
  }
}
