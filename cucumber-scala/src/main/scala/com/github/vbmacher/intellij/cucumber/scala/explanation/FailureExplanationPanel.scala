package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.{JBLabel, JBScrollPane, JBTextArea}
import com.intellij.util.ui.{FormBuilder, JBUI}

import java.awt.{BorderLayout, Dimension, FlowLayout}
import java.util.concurrent.atomic.AtomicLong
import javax.swing.{JButton, JComboBox, JComponent, JPanel, JSplitPane}

class FailureExplanationPanel(project: Project, context: FailureContext.Snapshot,
                              providerFactory: (Project, String, String) => FailureExplainer = (p, url, model) =>
                                new OllamaFailureExplainer(url, model, () => FailureExplanationCredentials.token(p, url)))
  extends JPanel(new BorderLayout(0, JBUI.scale(8))) with Disposable {
  private val settings = ScalaCucumberSettings.getInstance(project)
  private val url = settings.explanationUrl
  private val model = settings.explanationModel
  private val review = new JBTextArea(context.text, 14, 80)
  private val explanation = new JBTextArea(10, 80)
  private val explain = new JButton("Explain")
  private val cancel = new JButton("Cancel request")
  private val status = new JBLabel("Review or redact the context below, then click Explain. Nothing has been sent.").setAllowAutoWrapping(true)
  private val sources = new JComboBox[FailureContext.Source](context.sources.toArray)
  private val open = new JButton("Open source")
  private val generation = new AtomicLong()
  @volatile private var disposed = false
  private var busy = false

  private def row(components: JComponent*): JPanel = {
    val panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
    components.foreach(panel.add)
    panel
  }
  review.getAccessibleContext.setAccessibleName("Context to send")
  review.setLineWrap(true)
  review.setWrapStyleWord(true)
  review.setCaretPosition(0)
  explanation.getAccessibleContext.setAccessibleName("Generated explanation")
  explanation.setEditable(false)
  explanation.setLineWrap(true)
  explanation.setWrapStyleWord(true)
  explanation.getEmptyText.setText("No generated explanation")
  val facts = new JBTextArea(context.facts, 3, 80)
  facts.setEditable(false)
  facts.setLineWrap(true)
  facts.setWrapStyleWord(true)
  val header = FormBuilder.createFormBuilder()
    .addComponent(new JBLabel("Recorded failure facts"))
    .addComponent(new JBScrollPane(facts))
    .addComponent(new JBLabel(s"Send to: Ollama · $url · $model").setAllowAutoWrapping(true))
    .addComponent(row(explain, cancel))
    .addComponent(status)
    .getPanel
  add(header, BorderLayout.NORTH)
  def section(title: String, text: JBTextArea): JPanel = {
    val panel = new JPanel(new BorderLayout(0, JBUI.scale(4)))
    panel.add(new JBLabel(title), BorderLayout.NORTH)
    panel.add(new JBScrollPane(text), BorderLayout.CENTER)
    panel
  }
  val split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
    section("Context to send (editable; remove anything you do not want to send)", review),
    section("Generated explanation — suggestions, not a verified cause", explanation))
  split.setResizeWeight(0.6)
  add(split, BorderLayout.CENTER)
  val navigation = new JPanel(new BorderLayout(JBUI.scale(6), 0))
  navigation.add(sources, BorderLayout.CENTER)
  navigation.add(open, BorderLayout.EAST)
  add(navigation, BorderLayout.SOUTH)
  setBorder(JBUI.Borders.empty(8))
  setPreferredSize(new Dimension(JBUI.scale(850), JBUI.scale(650)))

  private def providerChanged: Boolean = !settings.failureExplanations || settings.explanationUrl != url || settings.explanationModel != model
  private def problem: Option[String] =
    if (providerChanged) Some("Failure explanation settings changed or were disabled. Close this window and review a new request.")
    else context.problem(project)
  private def controls(): Unit = {
    explain.setEnabled(!busy)
    cancel.setEnabled(busy)
    review.setEditable(!busy)
  }
  private def stop(): Unit = {
    generation.incrementAndGet()
    busy = false
    controls()
  }
  cancel.addActionListener(_ => {
    stop()
    explanation.setText("")
    status.setText("Cancelled. No explanation was accepted; your source files were not changed.")
  })
  open.addActionListener(_ => problem match {
    case Some(message) => status.setText(message)
    case None => Option(sources.getSelectedItem).collect { case source: FailureContext.Source => source }.foreach(_.open(project))
  })
  explain.addActionListener(_ => start())
  private def start(): Unit = {
    val text = review.getText
    val invalid = problem.orElse(if (text.trim.isEmpty) Some("Select some context to send.")
      else if (text.length > FailureContext.maxContextLength) Some(s"Reduce the context to ${FailureContext.maxContextLength} characters or fewer before sending.") else None)
    invalid match {
      case Some(message) => status.setText(message)
      case None =>
        stop()
        val id = generation.get()
        busy = true
        controls()
        explanation.setText("")
        status.setText("Requesting an explanation… This can take several minutes. Cancel request stops waiting for the model.")
        new Task.Backgroundable(project, "Explain Cucumber failure", true) {
          private var answer = ""
          override def run(indicator: ProgressIndicator): Unit = {
            def check(): Unit = {
              indicator.checkCanceled()
              if (disposed || generation.get() != id) throw new ProcessCanceledException()
            }
            check()
            problem.foreach(message => throw new java.io.IOException(message))
            answer = providerFactory(project, url, model).explain(text, () => check())
            check()
            problem.foreach(message => throw new java.io.IOException(message))
          }
          override def onSuccess(): Unit = if (!disposed && generation.get() == id) {
            busy = false
            controls()
            problem match {
              case Some(message) => status.setText(message)
              case None =>
                explanation.setText(answer)
                explanation.setCaretPosition(0)
                status.setText("Generated explanation ready. Treat it as a suggestion and verify it against the recorded facts and sources.")
            }
          }
          override def onCancel(): Unit = if (!disposed && generation.get() == id) {
            stop()
            status.setText("Cancelled. Nothing was changed in your source files.")
          }
          override def onThrowable(error: Throwable): Unit = if (!disposed && generation.get() == id) {
            stop()
            status.setText("Explanation unavailable: " + Option(error.getMessage).getOrElse("check the configured server and chat model."))
          }
        }.queue()
    }
  }
  controls()
  override def dispose(): Unit = { disposed = true; generation.incrementAndGet() }
}

class FailureExplanationDialog(project: Project, context: FailureContext.Snapshot) extends DialogWrapper(project) {
  private val panel = new FailureExplanationPanel(project, context)
  com.intellij.openapi.util.Disposer.register(getDisposable, panel)
  setTitle("Explain failure: " + context.title)
  setCancelButtonText("Close")
  init()
  override def createCenterPanel(): JComponent = panel
  override def createActions(): Array[javax.swing.Action] = Array(getCancelAction)
}
