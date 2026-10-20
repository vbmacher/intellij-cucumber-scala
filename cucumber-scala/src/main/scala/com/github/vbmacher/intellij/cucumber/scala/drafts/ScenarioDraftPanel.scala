package com.github.vbmacher.intellij.cucumber.scala.drafts

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.util.OllamaCredentials
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorFontType}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.{EditorTextField, OnePixelSplitter}
import com.intellij.ui.components.{JBLabel, JBList, JBScrollPane, JBTextArea}
import com.intellij.util.ui.{JBUI, UIUtil}
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

import java.awt.{BorderLayout, Component, Dimension, FlowLayout}
import java.awt.event.{ActionEvent, KeyEvent, MouseAdapter, MouseEvent}
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
  private val review = new JBTextArea(context.text, 5, 60)
  private val preview = new EditorTextField(EditorFactory.getInstance().createDocument(""), project, GherkinFileType.INSTANCE, true, false)
  private val details = new JBTextArea(6, 40)
  private val choiceModel = new DefaultListModel[String]()
  private val choices = new JBList[String](choiceModel)
  private val contextToggle = new JToggleButton("Context to send", true)
  private val contextBody = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  private val contextSplit = new OnePixelSplitter(true, 0.3f)
  private val draftCount = new JBLabel("Drafts")
  private val previewTitle = new JBLabel("Proposed Gherkin")
  private val selectionCount = new JBLabel("No drafts selected")
  private val progress = new JProgressBar()
  private val generate = new JButton("Generate drafts")
  private val cancel = new JButton("Cancel request")
  private[drafts] val insertAction: Action = new AbstractAction("Insert selected") {
    override def actionPerformed(event: ActionEvent): Unit = insertSelected()
  }
  private val status = new JBLabel("Review or redact the context, then click Generate drafts. Nothing has been sent.").setAllowAutoWrapping(true)
  private val generation = new AtomicLong()
  @volatile private var disposed = false
  private var busy = false
  private var candidates = Seq.empty[(ScenarioDraft, DraftContext.Validation)]
  private var selected = Set.empty[Int]

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
  review.setFont(EditorColorsManager.getInstance().getGlobalScheme.getFont(EditorFontType.PLAIN))
  review.setBorder(JBUI.Borders.empty(6))
  contextToggle.setIcon(AllIcons.General.ArrowRight)
  contextToggle.setSelectedIcon(AllIcons.General.ArrowDown)
  contextToggle.setToolTipText("Hide context")
  contextToggle.addActionListener(_ => {
    contextBody.setVisible(contextToggle.isSelected)
    contextToggle.setToolTipText(if (contextToggle.isSelected) "Hide context" else "Show context")
    revalidate()
  })
  preview.getAccessibleContext.setAccessibleName("Proposed Gherkin")
  preview.setPlaceholder("Generate drafts to review proposed scenarios here")
  preview.addSettingsProvider(editor => {
    val scheme = EditorColorsManager.getInstance().getGlobalScheme
    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(scheme))
    editor.getHighlighter.setColorScheme(scheme)
    editor.setBackgroundColor(scheme.getDefaultBackground)
    editor.setBorder(JBUI.Borders.empty(8))
    editor.getSettings.setLineNumbersShown(false)
    editor.getSettings.setLineMarkerAreaShown(false)
    editor.getSettings.setFoldingOutlineShown(false)
    editor.getSettings.setUseSoftWraps(true)
    editor.getSettings.setAdditionalLinesCount(0)
    editor.getSettings.setAdditionalColumnsCount(0)
  })
  details.getAccessibleContext.setAccessibleName("Draft validation")
  details.setEditable(false)
  details.setLineWrap(true)
  details.setWrapStyleWord(true)
  details.setFont(UIUtil.getLabelFont)
  details.setBorder(JBUI.Borders.empty(8))
  details.getEmptyText.setText("Each draft includes its rationale and step checks")
  choices.getAccessibleContext.setAccessibleName("Drafts to review")
  choices.getAccessibleContext.setAccessibleDescription("Use arrow keys to preview; Space toggles insertion")
  choices.getEmptyText.setText("No drafts yet")
  choices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  choices.setCellRenderer(new ListCellRenderer[String] {
    override def getListCellRendererComponent(list: JList[_ <: String], title: String, index: Int,
                                               active: Boolean, focused: Boolean): Component = {
      val validation = candidates(index)._2
      val check = new JCheckBox(title, selected.contains(index))
      check.putClientProperty("html.disable", java.lang.Boolean.TRUE)
      check.setEnabled(validation.errors.isEmpty)
      val summary = new JBLabel(if (validation.errors.nonEmpty) "Invalid Gherkin"
        else {
          val unresolved = validation.steps.count(_.startsWith("Unresolved:"))
          val ambiguous = validation.steps.count(_.startsWith("Ambiguous:"))
          Seq(Option.when(unresolved > 0)(s"$unresolved unresolved"),
            Option.when(ambiguous > 0)(s"$ambiguous ambiguous")).flatten.mkString(" · ") match {
            case "" => "Step definitions found"
            case message => message
          }
        })
      val cell = new JPanel(new BorderLayout(0, JBUI.scale(2)))
      cell.getAccessibleContext.setAccessibleName(s"$title. ${summary.getText}. ${if (selected.contains(index)) "Checked" else "Unchecked"}")
      cell.setBorder(JBUI.Borders.empty(8))
      cell.setBackground(if (active) list.getSelectionBackground else list.getBackground)
      check.setOpaque(false)
      check.setForeground(if (active) list.getSelectionForeground else list.getForeground)
      summary.setForeground(if (active) list.getSelectionForeground else UIUtil.getContextHelpForeground)
      summary.setBorder(JBUI.Borders.emptyLeft(24))
      cell.add(check, BorderLayout.NORTH)
      cell.add(summary, BorderLayout.CENTER)
      cell
    }
  })
  choices.addListSelectionListener(event => if (!event.getValueIsAdjusting) showDraft())
  choices.addMouseListener(new MouseAdapter {
    override def mousePressed(event: MouseEvent): Unit = {
      val index = choices.locationToIndex(event.getPoint)
      if (SwingUtilities.isLeftMouseButton(event) && index >= 0 &&
        choices.getCellBounds(index, index).contains(event.getPoint) && event.getX < JBUI.scale(36)) toggleDraft(index)
    }
  })
  choices.getInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleDraft")
  choices.getActionMap.put("toggleDraft", new AbstractAction {
    override def actionPerformed(event: ActionEvent): Unit = toggleDraft(choices.getSelectedIndex)
  })
  val requestHeader = new JPanel(new BorderLayout())
  requestHeader.add(row(contextToggle, new JBLabel(s"Ollama · $model")), BorderLayout.CENTER)
  requestHeader.add(row(generate, cancel, progress), BorderLayout.EAST)
  contextBody.add(new JBLabel(s"Send to: $url").setAllowAutoWrapping(true), BorderLayout.NORTH)
  contextBody.add(new JBScrollPane(review), BorderLayout.CENTER)
  contextBody.setMinimumSize(new Dimension(0, JBUI.scale(100)))
  contextBody.setBorder(JBUI.Borders.emptyBottom(6))
  progress.setIndeterminate(true)
  progress.setPreferredSize(new Dimension(JBUI.scale(100), JBUI.scale(4)))
  val draftList = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  private val choiceScroll = new JBScrollPane(choices)
  draftList.add(draftCount, BorderLayout.NORTH)
  draftList.add(choiceScroll, BorderLayout.CENTER)
  val previewPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  previewPanel.add(previewTitle, BorderLayout.NORTH)
  previewPanel.add(preview, BorderLayout.CENTER)
  previewPanel.add(section("Why this case · Step checks · Not executed", new JBScrollPane(details)), BorderLayout.SOUTH)
  previewPanel.setBorder(JBUI.Borders.emptyLeft(8))
  val split = new OnePixelSplitter(false, 0.3f)
  split.setFirstComponent(draftList)
  split.setSecondComponent(previewPanel)
  draftList.setMinimumSize(new Dimension(JBUI.scale(180), 0))
  previewPanel.setMinimumSize(new Dimension(JBUI.scale(300), 0))
  split.setMinimumSize(new Dimension(0, JBUI.scale(180)))
  contextSplit.setFirstComponent(contextBody)
  contextSplit.setSecondComponent(split)
  add(requestHeader, BorderLayout.NORTH)
  add(contextSplit, BorderLayout.CENTER)
  val footer = new JPanel(new BorderLayout())
  footer.add(status, BorderLayout.CENTER)
  footer.add(selectionCount, BorderLayout.SOUTH)
  add(footer, BorderLayout.SOUTH)
  setBorder(JBUI.Borders.empty(8))
  setPreferredSize(new Dimension(JBUI.scale(980), JBUI.scale(660)))

  private def showDraft(): Unit = candidates.lift(choices.getSelectedIndex).foreach { case (draft, validation) =>
    previewTitle.setText(draft.title)
    previewTitle.putClientProperty("html.disable", java.lang.Boolean.TRUE)
    preview.setText(context.formatted(draft))
    Option(preview.getEditor).foreach(_.getCaretModel.moveToOffset(0))
    details.setText(draft.reason + "\n\n" + validation.summary)
    details.setCaretPosition(0)
  }
  private def toggleDraft(index: Int): Unit = candidates.lift(index).foreach { case (_, validation) =>
    if (!busy && validation.errors.isEmpty) {
      selected = if (selected.contains(index)) selected - index else selected + index
      choices.repaint()
      controls()
    }
  }

  private def problem: Option[String] =
    if (disposed) Some("This review window is closed.")
    else if (!settings.scenarioDrafts || settings.draftUrl != url || settings.draftModel != model)
      Some("Edge-case draft settings changed or were disabled. Close this window and review a new request.")
    else context.problem
  private def controls(): Unit = {
    generate.setEnabled(!busy)
    cancel.setEnabled(busy)
    cancel.setVisible(busy)
    progress.setVisible(busy)
    review.setEditable(!busy)
    insertAction.setEnabled(!busy && selected.nonEmpty)
    selectionCount.setText(if (selected.isEmpty) "No drafts selected" else s"${selected.size} of ${candidates.size} selected")
  }
  private def clear(): Unit = {
    candidates = Seq.empty
    selected = Set.empty
    choiceModel.clear()
    draftCount.setText("Drafts")
    previewTitle.setText("Proposed Gherkin")
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
  private def insertSelected(): Unit = problem match {
    case Some(message) => status.setText(message)
    case None => context.insert(candidates.zipWithIndex.collect { case ((draft, _), index) if selected.contains(index) => draft }) match {
      case Left(message) => status.setText(message)
      case Right(_) => stop(); clear(); status.setText("Selected drafts inserted. Not executed."); inserted()
    }
  }

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
        status.setText("Generating drafts… This can take a few minutes. Step checks wait for indexing.")
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
                candidates = answers
                answers.foreach { case (draft, _) => choiceModel.addElement(draft.title) }
                draftCount.setText(s"Drafts (${answers.size})")
                choices.setSelectedIndex(0)
                if (contextToggle.isSelected) contextToggle.doClick()
                status.setText("Click a draft to preview it; check drafts to insert. Space toggles selection. Not executed.")
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
  panel.insertAction.putValue(DialogWrapper.DEFAULT_ACTION, java.lang.Boolean.TRUE)
  init()
  override def createCenterPanel(): JComponent = panel
  override def createActions(): Array[Action] = Array(panel.insertAction, getCancelAction)
}
