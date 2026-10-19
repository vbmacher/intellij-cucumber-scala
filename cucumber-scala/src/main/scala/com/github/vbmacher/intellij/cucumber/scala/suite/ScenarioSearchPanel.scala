package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorFontType}
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.{JBLabel, JBList, JBScrollPane, JBTextArea, JBTextField}
import com.intellij.util.ui.{FormBuilder, JBUI, UIUtil}
import org.jetbrains.plugins.cucumber.psi.GherkinFile

import java.awt.{BorderLayout, Component, Dimension, FlowLayout, GridLayout}
import java.awt.event.{ActionEvent, MouseAdapter, MouseEvent}
import java.util.concurrent.atomic.AtomicLong
import javax.swing._

class ScenarioSearchPanel(project: Project,
                          providerFactory: (String, String) => ScenarioSearch.Embeddings = (url, model) => new OllamaEmbeddings(url, model))
  extends JPanel(new BorderLayout()) with Disposable {
  private val modules = ModuleManager.getInstance(project).getModules.sortBy(_.getName)
  private val scope = new JComboBox[String](Array("Project contents") ++ modules.map(_.getName))
  private val mode = new JComboBox[String](Array("Exact text", "Semantic"))
  private val query = new JBTextField(24)
  private val search = new JButton("Search")
  private val rebuild = new JButton("Rebuild index")
  private val delete = new JButton("Delete index")
  private val cancel = new JButton("Cancel")
  private val settings = new JButton("Settings…")
  private val providerLabel = new JBLabel(UIUtil.ComponentStyle.SMALL)
  private val status = new JBLabel("Enter text, or leave empty to list all scenarios.").setCopyable(true).setAllowAutoWrapping(true)
  private val modeHint = new JBLabel(UIUtil.ComponentStyle.SMALL).setAllowAutoWrapping(true)
  private val indexToggle = new JToggleButton("Semantic index")
  private val indexState = new JBLabel(UIUtil.ComponentStyle.SMALL)
  private val model = new DefaultListModel[ScenarioSearch.Hit]()
  private val results = new JBList[ScenarioSearch.Hit](model) {
    override def getScrollableTracksViewportWidth: Boolean = true
  }
  private val snippet = new JBTextArea()
  private val resultsTitle = new JLabel("Results")
  private val open = new JButton("Go to source")
  private var index: Option[ScenarioSearch.Index] = None
  private val generation = new AtomicLong()
  private var busy = false
  @volatile private var disposed = false
  setBorder(JBUI.Borders.empty(8))

  private def row(components: JComponent*): JPanel = {
    val panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
    components.foreach(panel.add)
    panel
  }
  private def labeled(text: String, component: JComponent): JPanel = {
    val panel = new JPanel(new BorderLayout(JBUI.scale(6), 0))
    val label = new JBLabel(text)
    label.setLabelFor(component)
    panel.add(label, BorderLayout.WEST)
    panel.add(component, BorderLayout.CENTER)
    panel
  }
  private val queryRow = new JPanel(new BorderLayout(JBUI.scale(6), 0))
  queryRow.add(query, BorderLayout.CENTER)
  queryRow.add(row(search, cancel), BorderLayout.EAST)
  private val filters = new JPanel(new GridLayout(1, 2, JBUI.scale(8), 0))
  Seq(scope, mode).foreach(combo => combo.setMinimumSize(new Dimension(0, combo.getPreferredSize.height)))
  filters.add(labeled("Scope:", scope))
  filters.add(labeled("Match:", mode))
  private val indexOptions = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  indexOptions.setBorder(JBUI.Borders.empty(0, 6, 4, 0))
  indexOptions.add(providerLabel, BorderLayout.NORTH)
  indexOptions.add(row(rebuild, delete, settings), BorderLayout.CENTER)
  private val header = FormBuilder.createFormBuilder().setVerticalGap(8)
    .addComponent(queryRow, 0)
    .addComponent(filters)
    .addComponent(modeHint)
    .addComponent(row(indexToggle, indexState))
    .addComponent(indexOptions)
    .getPanel
  header.setBorder(JBUI.Borders.emptyBottom(8))
  add(header, BorderLayout.NORTH)
  snippet.setEditable(false)
  snippet.setLineWrap(true)
  snippet.setWrapStyleWord(true)
  snippet.setFont(EditorColorsManager.getInstance().getGlobalScheme.getFont(EditorFontType.PLAIN))
  snippet.getAccessibleContext.setAccessibleName("Scenario preview")
  snippet.setBorder(JBUI.Borders.empty(8))
  snippet.getEmptyText.setText("Select a result to preview its scenario")
  results.getEmptyText.setText("Search to find scenarios")
  results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  resultsTitle.setLabelFor(results)
  results.setCellRenderer(new ListCellRenderer[ScenarioSearch.Hit] {
    private val title = new JLabel()
    private val location = new JBLabel(UIUtil.ComponentStyle.SMALL)
    private val cell = new JPanel(new BorderLayout(0, JBUI.scale(3))) {
      // CellRendererPane needs an explicit layout pass for the two labels.
      override def validate(): Unit = doLayout()
    }
    cell.setBorder(JBUI.Borders.empty(6, 8))
    title.setFont(title.getFont.deriveFont(java.awt.Font.BOLD))
    cell.add(title, BorderLayout.NORTH)
    cell.add(location, BorderLayout.SOUTH)
    override def getListCellRendererComponent(list: JList[_ <: ScenarioSearch.Hit], hit: ScenarioSearch.Hit,
                                             position: Int, selected: Boolean, focused: Boolean): Component = {
      title.setText(hit.document.title)
      val path = Option(project.getBasePath).fold(hit.document.path)(base => hit.document.path.stripPrefix(base + "/"))
      location.setText(s"$path:${hit.document.line}" + hit.similarity.map(s => f" · similarity ${s * 100}%.0f%%").getOrElse(""))
      cell.setBackground(if (selected) list.getSelectionBackground else list.getBackground)
      title.setForeground(if (selected) list.getSelectionForeground else list.getForeground)
      location.setForeground(if (selected) list.getSelectionForeground else UIUtil.getContextHelpForeground)
      cell
    }
  })
  private val resultPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  resultPanel.add(resultsTitle, BorderLayout.NORTH)
  resultPanel.add(new JBScrollPane(results), BorderLayout.CENTER)
  private val previewHeader = new JPanel(new BorderLayout())
  previewHeader.add(new JLabel("Scenario preview"), BorderLayout.CENTER)
  previewHeader.add(open, BorderLayout.EAST)
  private val previewPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  previewPanel.setBorder(JBUI.Borders.emptyTop(8))
  previewPanel.add(previewHeader, BorderLayout.NORTH)
  previewPanel.add(new JBScrollPane(snippet), BorderLayout.CENTER)
  previewPanel.setVisible(false)
  private val split = new OnePixelSplitter(true, 0.55f)
  split.setFirstComponent(resultPanel)
  split.setSecondComponent(previewPanel)
  add(split, BorderLayout.CENTER)
  status.setBorder(JBUI.Borders.emptyTop(6))
  add(status, BorderLayout.SOUTH)
  query.getEmptyText.setText("Search scenario titles and steps")
  query.getAccessibleContext.setAccessibleName("Scenario query")
  query.setToolTipText("Search scenario titles and bodies. Leave empty to list all scenarios. Semantic mode returns up to 50 related scenarios.")
  rebuild.setToolTipText("Send scenarios in the selected scope to the configured Ollama model. Vectors are kept in memory only.")
  delete.setToolTipText("Delete the in-memory index for the selected scope.")
  modeHint.setToolTipText("Semantic similarity suggests related wording; it proves neither step binding nor coverage.")
  indexToggle.setToolTipText("Show index actions and the configured Ollama provider.")
  indexToggle.setIcon(AllIcons.General.ArrowRight)
  indexToggle.setSelectedIcon(AllIcons.General.ArrowDown)
  indexToggle.addActionListener(_ => updateIndexOptions())
  search.addActionListener(_ => start(build = false))
  query.addActionListener(_ => start(build = false))
  rebuild.addActionListener(_ => start(build = true))
  delete.addActionListener(_ => invalidate("Semantic index deleted from memory."))
  cancel.addActionListener(_ => {
    stop()
    clearResults()
    results.getEmptyText.setText("Search cancelled")
    status.setText("Cancelled. Search or rebuild to try again.")
  })
  scope.addActionListener(_ => invalidate("Scope changed. The semantic index will rebuild on the next search."))
  mode.addActionListener(_ => {
    stop()
    clearResults()
    updateMode()
    status.setText(if (mode.getSelectedIndex == 1 && index.isEmpty) "Enter a query; the semantic index builds automatically when needed." else "Enter a query and press Enter, or click Search.")
  })
  query.getDocument.addDocumentListener(new javax.swing.event.DocumentListener {
    private def changed(): Unit = { stop(); clearResults(); status.setText("Press Enter or click Search.") }
    override def insertUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
    override def removeUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
    override def changedUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
  })
  settings.addActionListener(_ => {
    val previous = provider.id
    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Scala Cucumber")
    updateProvider()
    if (previous != provider.id) invalidate("Provider or model changed. The semantic index will rebuild on the next search.")
  })
  results.addListSelectionListener(_ => {
    snippet.setText(Option(results.getSelectedValue).map(_.document.text).getOrElse(""))
    snippet.setCaretPosition(0)
    open.setEnabled(results.getSelectedValue != null)
    previewPanel.setVisible(results.getSelectedValue != null)
    split.revalidate()
  })
  results.getInputMap.put(KeyStroke.getKeyStroke("ENTER"), "openSource")
  results.getActionMap.put("openSource", new AbstractAction {
    override def actionPerformed(event: ActionEvent): Unit = open.doClick()
  })
  results.addMouseListener(new MouseAdapter {
    override def mouseClicked(event: MouseEvent): Unit = {
      val selected = results.getSelectedIndex
      if (event.getButton == MouseEvent.BUTTON1 && event.getClickCount == 2 && selected >= 0 &&
        results.getCellBounds(selected, selected).contains(event.getPoint)) open.doClick()
    }
  })
  open.addActionListener(_ => Option(results.getSelectedValue).foreach { hit =>
    hit.document.navigation(project) match {
      case Some(target) => target.navigate(true)
      case None => invalidate("Source changed or was deleted. Search again.")
    }
  })
  // Invalidate before document commits too: never leave navigable stale search results while editing.
  EditorFactory.getInstance().getEventMulticaster.addDocumentListener(new DocumentListener {
    override def documentChanged(event: DocumentEvent): Unit = {
      if (Option(PsiDocumentManager.getInstance(project).getPsiFile(event.getDocument)).exists(_.isInstanceOf[GherkinFile]))
        invalidate("Feature text changed. Search again; the semantic index will rebuild automatically.")
    }
  }, this)
  PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter {
    private def changed(event: PsiTreeChangeEvent): Unit = {
      val files = Seq(event.getFile, event.getChild, event.getOldChild, event.getNewChild).filter(_ != null)
      if (files.isEmpty || files.exists(e => e.isInstanceOf[GherkinFile] || Option(e.getContainingFile).exists(_.isInstanceOf[GherkinFile])))
        invalidate("Feature sources changed. Search again; the semantic index will rebuild automatically.")
    }
    override def childrenChanged(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childAdded(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childRemoved(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childMoved(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childReplaced(e: PsiTreeChangeEvent): Unit = changed(e)
    override def propertyChanged(e: PsiTreeChangeEvent): Unit = changed(e)
  }, this)
  updateProvider()
  updateMode()
  updateControls()

  private def provider: ScenarioSearch.Embeddings = {
    val config = ScalaCucumberSettings.getInstance(project)
    providerFactory(config.ollamaUrl, config.embeddingModel)
  }
  private def updateProvider(): Unit = {
    providerLabel.setText("Ollama · " + ScalaCucumberSettings.getInstance(project).embeddingModel)
    providerLabel.setToolTipText(provider.id)
  }
  private def updateIndexOptions(): Unit = { indexOptions.setVisible(indexToggle.isSelected); revalidate(); repaint() }
  private def updateMode(): Unit = {
    val semantic = mode.getSelectedIndex == 1
    modeHint.setText(if (semantic) "Related wording · results do not verify step bindings." else "Exact text · no model or index required.")
    indexToggle.setSelected(semantic)
    updateIndexOptions()
  }
  private def updateControls(): Unit = {
    search.setEnabled(!busy)
    rebuild.setEnabled(!busy)
    cancel.setVisible(busy)
    delete.setEnabled(busy || index.nonEmpty)
    open.setEnabled(results.getSelectedValue != null)
    indexState.setText(if (index.nonEmpty) "Ready · memory only" else "Not built")
    revalidate()
  }
  private def clearResults(): Unit = {
    model.clear()
    snippet.setText("")
    resultsTitle.setText("Results")
    results.getEmptyText.setText("Search to find scenarios")
    busy = false
    updateControls()
  }
  private def stop(): Unit = { generation.incrementAndGet() }
  private def invalidate(message: String): Unit = {
    stop()
    val application = ApplicationManager.getApplication
    if (application.isDispatchThread) {
      index = None
      clearResults()
      status.setText(message)
    } else application.invokeLater(() => if (!disposed) invalidate(message))
  }

  def findSimilar(text: String): Unit = {
    mode.setSelectedItem("Semantic")
    query.setText(text)
    start(build = false)
    query.requestFocusInWindow()
  }

  private def start(build: Boolean): Unit = {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    stop()
    clearResults()
    updateProvider()
    val id = generation.get()
    val semantic = mode.getSelectedIndex == 1
    val text = query.getText
    val selectedScope = if (scope.getSelectedIndex == 0) GlobalSearchScope.projectScope(project)
      else modules(scope.getSelectedIndex - 1).getModuleContentScope
    val embedding = provider
    val previousIndex = index
    if (build) index = None
    busy = true
    updateControls()
    results.getEmptyText.setText(if (build || (semantic && previousIndex.isEmpty)) "Building semantic index…" else "Searching scenarios…")
    status.setText(if (build || (semantic && previousIndex.isEmpty)) "Building semantic index…" else "Searching…")
    new Task.Backgroundable(project, "Scala Cucumber scenario search", true) {
      private var hits = Seq.empty[ScenarioSearch.Hit]
      private var built: Option[ScenarioSearch.Index] = None
      override def run(indicator: ProgressIndicator): Unit = {
        def check(): Unit = {
          indicator.checkCanceled()
          if (disposed || generation.get() != id) throw new ProcessCanceledException()
        }
        check()
        val documents = ReadAction.nonBlocking[Seq[ScenarioSearch.Document]](() => ScenarioSearch.documents(project, selectedScope))
          .inSmartMode(project).expireWith(ScenarioSearchPanel.this).expireWhen(() => disposed || generation.get() != id).executeSynchronously()
        check()
        // No read lock or UI thread is held while waiting for Ollama.
        if (build) built = Some(ScenarioSearch.build(documents, embedding, () => check()))
        else if (semantic) {
          val current = previousIndex.filter(value => value.provider == embedding.id && documents.map(_.text).toSet == value.vectors.keySet)
            .getOrElse {
              val value = ScenarioSearch.build(documents, embedding, () => check())
              built = Some(value)
              value
            }
          hits = ScenarioSearch.semantic(documents, text, embedding, current, () => check())
        }
        else hits = ScenarioSearch.exact(documents, text, () => check())
        check()
      }
      override def onSuccess(): Unit = if (!disposed && generation.get() == id) {
        built.foreach(value => index = Some(value))
        hits.foreach(model.addElement)
        if (!model.isEmpty) results.setSelectedIndex(0)
        busy = false
        updateControls()
        resultsTitle.setText(if (build) "Results" else s"Results · ${hits.size}")
        results.getEmptyText.setText(if (build) "Index ready. Enter a query to find related scenarios." else "No scenarios match this search")
        status.setText(Option.when(build)(s"Semantic index ready · ${scope.getSelectedItem}")
          .getOrElse(s"${hits.size} ${if (hits.size == 1) "scenario" else "scenarios"} · ${mode.getSelectedItem} · ${scope.getSelectedItem}"))
      }
      override def onCancel(): Unit = if (!disposed && generation.get() == id) {
        busy = false
        updateControls()
        results.getEmptyText.setText("Search cancelled")
        status.setText("Cancelled. Search or rebuild to try again.")
      }
      override def onThrowable(error: Throwable): Unit = if (!disposed && generation.get() == id) {
        busy = false
        updateControls()
        results.getEmptyText.setText("Search unavailable")
        status.setText("Search unavailable: " + Option(error.getMessage).getOrElse("check Ollama settings") + " Exact text remains available.")
      }
    }.queue()
  }

  override def dispose(): Unit = { disposed = true; stop(); index = None }
}
