package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, ModalityState, ReadAction}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.{CaretEvent, CaretListener}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerEvent, FileEditorManagerListener, OpenFileDescriptor}
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, SmartPointerManager, SmartPsiElementPointer}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.{FormBuilder, JBUI}
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep}

import java.awt.{BorderLayout, CardLayout, FlowLayout}
import javax.swing._

class PairedStepPanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private final case class Candidate(label: String, source: String, pointer: SmartPsiElementPointer[PsiElement]) {
    override def toString: String = label
  }
  private final case class Selection(step: String, definitions: Seq[Candidate])

  private val pin = new JCheckBox("Pin preview")
  pin.setToolTipText("Keep this step and definition visible while you move elsewhere. Uncheck to follow the feature editor again.")
  private val candidates = new JComboBox[Candidate]()
  candidates.getAccessibleContext.setAccessibleName("Matching Scala definitions")
  private val definitionLabel = new JLabel("Definition:")
  definitionLabel.setLabelFor(candidates)
  private val sourceLocation = new JBLabel().setAllowAutoWrapping(true).setCopyable(true)
  private val definitionCards = new CardLayout()
  private val definitions = new JPanel(definitionCards)
  definitions.add(sourceLocation, "single")
  definitions.add(candidates, "multiple")
  private val definitionRow = new JPanel(new BorderLayout(JBUI.scale(6), 0))
  definitionRow.add(definitionLabel, BorderLayout.WEST)
  definitionRow.add(definitions, BorderLayout.CENTER)
  private val open = new JButton("Open definition")
  private val stepLabel = new JBLabel("No feature step selected").setAllowAutoWrapping(true).setCopyable(true)
  private val status = new JBLabel("Open a .feature file and place the caret on a step.").setAllowAutoWrapping(true).setCopyable(true)
  private val controls = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(6), 0))
  controls.add(pin)
  controls.add(open)
  private val header = FormBuilder.createFormBuilder().setVerticalGap(6)
    .addComponent(new JBLabel("Read-only Scala definition for the step at the feature caret.").setAllowAutoWrapping(true).setCopyable(true), 0)
    .addComponent(controls)
    .addComponent(stepLabel)
    .addComponent(status)
    .addComponent(definitionRow).getPanel
  header.setBorder(JBUI.Borders.empty(8))

  private val factory = EditorFactory.getInstance()
  private val viewer = factory.createViewer(factory.createDocument(""), project).asInstanceOf[EditorEx]
  viewer.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project,
    FileTypeManager.getInstance().getFileTypeByExtension("scala")))
  viewer.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme)
  viewer.getSettings.setLineNumbersShown(false)
  viewer.getSettings.setCaretRowShown(false)
  private val cards = new CardLayout()
  private val body = new JPanel(cards)
  private val empty = new JBLabel("Select a Gherkin step to preview its Scala implementation.", SwingConstants.CENTER)
    .setAllowAutoWrapping(true).setCopyable(true)
  body.add(empty, "empty")
  body.add(viewer.getComponent, "code")
  add(header, BorderLayout.NORTH)
  add(body, BorderLayout.CENTER)

  private val queue = new MergingUpdateQueue("Scala Cucumber paired view", 200, true, this, this)
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[Option[Selection]]] = None
  private var revision = 0L
  candidates.addActionListener(_ => showCandidate())
  pin.addActionListener(_ => {
    cancelPending()
    if (pin.isSelected) status.setText("Pinned. Uncheck Pin preview to follow the feature editor.")
    else refreshSelectedEditor()
  })
  open.addActionListener(_ => selectedCandidate.flatMap(c => Option(c.pointer.getElement)).foreach { target =>
    new OpenFileDescriptor(project, target.getContainingFile.getVirtualFile, target.getTextOffset).navigate(true)
  })
  factory.getEventMulticaster.addCaretListener(new CaretListener {
    override def caretPositionChanged(event: CaretEvent): Unit = {
      val editor = event.getEditor
      if (editor != viewer && editor.getProject == project && !pin.isSelected) refresh(Some(editor))
    }
  }, this)
  project.getMessageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener {
    override def selectionChanged(event: FileEditorManagerEvent): Unit = refreshSelectedEditor()
  })
  showSelection(None)
  refreshSelectedEditor()

  private def selectedCandidate: Option[Candidate] =
    Option(candidates.getSelectedItem).collect { case candidate: Candidate => candidate }

  private def showCandidate(): Unit = {
    val candidate = selectedCandidate
    open.setEnabled(candidate.exists(c => Option(c.pointer.getElement).exists(_.isValid)))
    ApplicationManager.getApplication.runWriteAction(new Runnable {
      override def run(): Unit = viewer.getDocument.setText(candidate.map(_.source).getOrElse(""))
    })
    cards.show(body, if (candidate.isDefined) "code" else "empty")
  }

  private def showSelection(selection: Option[Selection]): Unit = {
    candidates.removeAllItems()
    val values = selection.toSeq.flatMap(_.definitions)
    values.foreach(candidates.addItem)
    stepLabel.setText(selection.map(_.step).getOrElse("No feature step selected"))
    stepLabel.setToolTipText(selection.map(_.step).orNull)
    val message = selection match {
      case None => "Open a .feature file and place the caret on a step."
      case Some(_) if values.isEmpty => "Undefined step: no matching Scala definition."
      case Some(_) if values.size == 1 => "1 matching Scala definition. Preview follows the feature caret."
      case Some(_) => s"Ambiguous step: ${values.size} matching definitions. Choose one to inspect."
    }
    status.setText(message)
    empty.setText(if (selection.isDefined) "Add a Scala definition or change the step wording to match an existing one."
      else "Select a Gherkin step to preview its Scala implementation.")
    pin.setEnabled(values.nonEmpty)
    definitionRow.setVisible(values.nonEmpty)
    sourceLocation.setText(values.headOption.map(_.label).getOrElse(""))
    definitionCards.show(definitions, if (values.size > 1) "multiple" else "single")
    showCandidate()
  }

  private def cancelPending(): Unit = {
    revision += 1
    queue.cancelAllUpdates()
    pending.foreach(_.cancel())
    pending = None
  }

  private def refreshSelectedEditor(): Unit =
    if (!pin.isSelected) refresh(Option(FileEditorManager.getInstance(project).getSelectedTextEditor))

  private def refresh(editor: Option[Editor]): Unit = {
    cancelPending()
    val request = revision
    queue.queue(new Update("selection") {
      override def run(): Unit = {
        if (request == revision && !pin.isSelected) {
          val offset = editor.map(_.getCaretModel.getOffset).getOrElse(0)
          pending = Some(ReadAction.nonBlocking[Option[Selection]](() => {
            editor.filterNot(_.isDisposed).flatMap(e => Option(PsiDocumentManager.getInstance(project).getPsiFile(e.getDocument)))
              .collect { case file: GherkinFile if file.isValid => file }
              .flatMap(file => Option(file.findElementAt(offset)))
              .flatMap(e => Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinStep], false)))
              .map { step =>
                Selection(step.getText, matchingSteps(step).flatMap(d => Option(d.getElement)).map { target =>
                  Candidate(target.getContainingFile.getName + ":" + (target.getContainingFile.getText.take(target.getTextOffset).count(_ == '\n') + 1),
                    target.getParent.getText, SmartPointerManager.createPointer(target))
                })
              }
          }).inSmartMode(project).withDocumentsCommitted(project).expireWith(PairedStepPanel.this)
            .finishOnUiThread(ModalityState.defaultModalityState(), selection => {
              if (request == revision && !pin.isSelected) showSelection(selection)
            }).submit(AppExecutorUtil.getAppExecutorService))
        }
      }
    })
  }

  override def dispose(): Unit = {
    cancelPending()
    factory.releaseEditor(viewer)
  }
}
