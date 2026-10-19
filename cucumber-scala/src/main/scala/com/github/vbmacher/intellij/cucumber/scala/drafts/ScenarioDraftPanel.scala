package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.{JBLabel, JBScrollPane, JBTextArea}
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

import java.awt.{BorderLayout, Dimension, FlowLayout}
import java.util.concurrent.atomic.AtomicLong
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}

class ScenarioDraftPanel(project: Project, context: DraftContext.Snapshot, inserted: () => Unit = () => (),
                         providerFactory: (Project, String, String) => ScenarioDraftProvider = (p, url, model) =>
                           new OllamaScenarioDraftProvider(url, model, () => new OllamaCredentials(p, "scenario drafts").token(url)))
  extends JPanel(new BorderLayout(0, JBUI.scale(8))) with Disposable {
  private val settings = ScalaCucumberSettings.getInstance(project)
  private val url = settings.draftUrl
  private val model = settings.draftModel
  private val review = new JBTextArea(context.text, 10, 80)
  private val preview = new EditorTextField(EditorFactory.getInstance().createDocument(""), project, GherkinFileType.INSTANCE, true, false)
  private val details = new JBTextArea(5, 80)
  private val choices = new JPanel()
  private val generate = new JButton("Generate drafts")
  private val cancel = new JButton("Cancel request")
  private val insert = new JButton("Insert selected")
  private val status = new JBLabel("Review or redact the context, then click Generate drafts. Nothing has been sent.").setAllowAutoWrapping(true)
  private val generation = new AtomicLong()
  @volatile private var disposed = false
  private var busy = false
  private var candidates = Seq.empty[(ScenarioDraft, JCheckBox)]

  private def row(components: JComponent*): JPanel = {
    val panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
    components.foreach(panel.add)
    panel
  }
  private def section(title: String, component: JComponent): JPanel = {
    val panel = new JPanel(new BorderLayout(0, JBUI.scale(4)))
    panel.add(new JBLabel(title), BorderLayout.NORTH)
    panel.add(component, BorderLayout.CENTER)
    panel
  }
  review.getAccessibleContext.setAccessibleName("Context to send")
  review.setLineWrap(true)
  review.setWrapStyleWord(true)
  preview.getAccessibleContext.setAccessibleName("Proposed Gherkin")
  preview.setPreferredSize(new Dimension(JBUI.scale(780), JBUI.scale(180)))
  details.getAccessibleContext.setAccessibleName("Draft validation")
  details.setEditable(false)
  details.setLineWrap(true)
  details.setWrapStyleWord(true)
  choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS))
  val top = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  top.add(new JBLabel(s"Send to: Ollama · $url · $model").setAllowAutoWrapping(true), BorderLayout.NORTH)
  top.add(section("Context to send (editable)", new JBScrollPane(review)), BorderLayout.CENTER)
  top.add(row(generate, cancel), BorderLayout.SOUTH)
  val bottom = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  private val choiceScroll = new JBScrollPane(choices)
  choiceScroll.setPreferredSize(new Dimension(JBUI.scale(780), JBUI.scale(110)))
  bottom.add(choiceScroll, BorderLayout.NORTH)
  bottom.add(section("Proposed Gherkin — Not executed", preview), BorderLayout.CENTER)
  bottom.add(new JBScrollPane(details), BorderLayout.SOUTH)
  val split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom)
  split.setResizeWeight(0.45)
  add(split, BorderLayout.CENTER)
  val footer = new JPanel(new BorderLayout())
  footer.add(status, BorderLayout.CENTER)
  footer.add(row(insert), BorderLayout.SOUTH)
  add(footer, BorderLayout.SOUTH)
  setBorder(JBUI.Borders.empty(8))
  setPreferredSize(new Dimension(JBUI.scale(850), JBUI.scale(780)))

  private def problem: Option[String] =
    if (disposed) Some("This review window is closed.")
    else if (!settings.scenarioDrafts || settings.draftUrl != url || settings.draftModel != model)
      Some("Edge-case draft settings changed or were disabled. Close this window and review a new request.")
    else context.problem
  private def controls(): Unit = {
    generate.setEnabled(!busy)
    cancel.setEnabled(busy)
    review.setEditable(!busy)
    insert.setEnabled(!busy && candidates.exists(_._2.isSelected))
  }
  private def clear(): Unit = {
    candidates = Seq.empty
    choices.removeAll()
    choices.revalidate()
    choices.repaint()
    preview.setText("")
    details.setText("")
    controls()
  }
  private def stop(): Unit = { generation.incrementAndGet(); busy = false; controls() }
  review.getDocument.addDocumentListener(new DocumentListener {
    private def changed(): Unit = if (candidates.nonEmpty) {
      clear()
      status.setText("Context changed. Generate new drafts before selecting proposals.")
    }
    override def insertUpdate(event: DocumentEvent): Unit = changed()
    override def removeUpdate(event: DocumentEvent): Unit = changed()
    override def changedUpdate(event: DocumentEvent): Unit = changed()
  })
  cancel.addActionListener(_ => {
    stop()
    clear()
    status.setText("Cancelled. No drafts were accepted; source files were not changed.")
  })
  generate.addActionListener(_ => start())
  insert.addActionListener(_ => problem match {
    case Some(message) => status.setText(message)
    case None => context.insert(candidates.collect { case (draft, check) if check.isSelected => draft }) match {
      case Left(message) => status.setText(message)
      case Right(_) => stop(); clear(); status.setText("Selected drafts inserted. Not executed."); inserted()
    }
  })

  private def start(): Unit = {
    val text = review.getText
    val invalid = problem.orElse(if (text.trim.isEmpty) Some("Enter specification context before generating drafts.")
      else if (text.length > DraftContext.maxContextLength) Some(s"Reduce the context to ${DraftContext.maxContextLength} characters or fewer before sending.") else None)
    invalid match {
      case Some(message) => status.setText(message)
      case None =>
        stop()
        clear()
        val id = generation.get()
        busy = true
        controls()
        status.setText("Requesting drafts, then validating steps (waiting for indexing if needed)…")
        new Task.Backgroundable(project, "Suggest Cucumber edge cases", true) {
          private var answers = Seq.empty[(ScenarioDraft, DraftContext.Validation)]
          override def run(indicator: ProgressIndicator): Unit = {
            def check(): Unit = {
              indicator.checkCanceled()
              if (disposed || generation.get() != id) throw new ProcessCanceledException()
            }
            check()
            val drafts = providerFactory(project, url, model).suggest(text, () => check())
            check()
            answers = ReadAction.nonBlocking[Seq[(ScenarioDraft, DraftContext.Validation)]](() => {
              check()
              problem.foreach(message => throw new java.io.IOException(message))
              drafts.map { draft => check(); draft -> context.validate(Seq(draft)) }
            }).inSmartMode(project).expireWith(project).expireWhen(() => disposed || generation.get() != id).executeSynchronously()
            check()
          }
          override def onSuccess(): Unit = if (!disposed && generation.get() == id) {
            busy = false
            problem match {
              case Some(message) => status.setText(message)
              case None =>
                candidates = answers.map { case (draft, validation) =>
                  val check = new JCheckBox(draft.title)
                  check.putClientProperty("html.disable", java.lang.Boolean.TRUE)
                  check.setEnabled(validation.errors.isEmpty)
                  check.addActionListener(_ => controls())
                  val inspect = new JButton("Inspect")
                  def show(): Unit = {
                    preview.setText(context.formatted(draft))
                    details.setText(draft.reason + "\n\n" + validation.summary)
                    details.setCaretPosition(0)
                  }
                  inspect.addActionListener(_ => show())
                  choices.add(row(check, inspect))
                  draft -> check
                }
                answers.headOption.foreach { case (draft, validation) =>
                  preview.setText(context.formatted(draft))
                  details.setText(draft.reason + "\n\n" + validation.summary)
                }
                choices.revalidate()
                choices.repaint()
                status.setText("Review the Gherkin and select drafts to insert. Unresolved or ambiguous steps need attention. Not executed.")
            }
            controls()
          }
          override def onCancel(): Unit = if (!disposed && generation.get() == id) {
            stop(); clear(); status.setText("Cancelled. Source files were not changed.")
          }
          override def onThrowable(error: Throwable): Unit = if (!disposed && generation.get() == id) {
            stop(); clear()
            status.setText("Drafts unavailable: " + Option(error.getMessage).getOrElse("check the configured server and chat model."))
          }
        }.queue()
    }
  }
  controls()
  override def dispose(): Unit = { disposed = true; generation.incrementAndGet() }
}

class ScenarioDraftDialog(project: Project, context: DraftContext.Snapshot) extends DialogWrapper(project) {
  private val panel = new ScenarioDraftPanel(project, context, () => close(DialogWrapper.OK_EXIT_CODE))
  Disposer.register(getDisposable, panel)
  setTitle("Suggest edge cases")
  setCancelButtonText("Close")
  init()
  override def createCenterPanel(): JComponent = panel
  override def createActions(): Array[Action] = Array(getCancelAction)
}
