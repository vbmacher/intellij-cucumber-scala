package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.{JBList, JBScrollPane, JBTextField}
import org.jetbrains.plugins.cucumber.psi.GherkinFile

import java.awt.{BorderLayout, GridLayout}
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
  private val rebuild = new JButton("Rebuild semantic index")
  private val delete = new JButton("Delete semantic index")
  private val cancel = new JButton("Cancel")
  private val settings = new JButton("Settings…")
  private val providerLabel = new JLabel()
  private val status = new JLabel("Exact text needs no model. Build an index to enable semantic search.")
  private val model = new DefaultListModel[ScenarioSearch.Hit]()
  private val results = new JBList[ScenarioSearch.Hit](model)
  private val snippet = new JTextArea()
  private val open = new JButton("Go to source")
  private var index: Option[ScenarioSearch.Index] = None
  private val generation = new AtomicLong()
  @volatile private var disposed = false
  private val controls = new JPanel(new GridLayout(0, 2, 4, 4))
  Seq(scope, mode, query, search, rebuild, delete, cancel, open).foreach(controls.add)
  private val header = new JPanel(new BorderLayout())
  header.add(controls, BorderLayout.NORTH)
  private val providerControls = new JPanel(new BorderLayout())
  providerControls.add(providerLabel, BorderLayout.CENTER)
  providerControls.add(settings, BorderLayout.EAST)
  header.add(providerControls, BorderLayout.SOUTH)
  add(header, BorderLayout.NORTH)
  snippet.setEditable(false)
  snippet.setLineWrap(true)
  snippet.setWrapStyleWord(true)
  add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(results), new JBScrollPane(snippet)), BorderLayout.CENTER)
  private val footer = new JPanel(new BorderLayout())
  footer.add(status, BorderLayout.NORTH)
  footer.add(new JLabel("Similarity suggests related wording; it proves neither step binding nor coverage."), BorderLayout.SOUTH)
  add(footer, BorderLayout.SOUTH)
  query.setToolTipText("Search scenario titles and bodies. Semantic mode returns up to 50 related scenarios.")
  rebuild.setToolTipText("Send scenarios in the selected scope to the configured Ollama model. Vectors are kept in memory only.")
  search.addActionListener(_ => start(build = false))
  query.addActionListener(_ => start(build = false))
  rebuild.addActionListener(_ => start(build = true))
  delete.addActionListener(_ => invalidate("Semantic index deleted from memory."))
  cancel.addActionListener(_ => { stop(); clearResults(); status.setText("Cancelled. Search or rebuild to try again.") })
  scope.addActionListener(_ => invalidate("Scope changed. Rebuild the semantic index for this scope."))
  mode.addActionListener(_ => { stop(); clearResults(); status.setText("Choose a query and click Search.") })
  query.getDocument.addDocumentListener(new javax.swing.event.DocumentListener {
    private def changed(): Unit = { stop(); clearResults(); status.setText("Query changed. Click Search.") }
    override def insertUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
    override def removeUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
    override def changedUpdate(e: javax.swing.event.DocumentEvent): Unit = changed()
  })
  settings.addActionListener(_ => {
    val previous = provider.id
    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Scala Cucumber")
    updateProvider()
    if (previous != provider.id) invalidate("Provider or model changed. Rebuild the semantic index.")
  })
  results.addListSelectionListener(_ => {
    snippet.setText(Option(results.getSelectedValue).map(_.document.text).getOrElse(""))
    snippet.setCaretPosition(0)
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
        invalidate("Feature text changed. Search again; semantic mode requires an index rebuild.")
    }
  }, this)
  PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter {
    private def changed(event: PsiTreeChangeEvent): Unit = {
      val files = Seq(event.getFile, event.getChild, event.getOldChild, event.getNewChild).filter(_ != null)
      if (files.isEmpty || files.exists(e => e.isInstanceOf[GherkinFile] || Option(e.getContainingFile).exists(_.isInstanceOf[GherkinFile])))
        invalidate("Feature sources changed. Search again; semantic mode requires an index rebuild.")
    }
    override def childrenChanged(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childAdded(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childRemoved(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childMoved(e: PsiTreeChangeEvent): Unit = changed(e)
    override def childReplaced(e: PsiTreeChangeEvent): Unit = changed(e)
    override def propertyChanged(e: PsiTreeChangeEvent): Unit = changed(e)
  }, this)
  updateProvider()

  private def provider: ScenarioSearch.Embeddings = {
    val config = ScalaCucumberSettings.getInstance(project)
    providerFactory(config.ollamaUrl, config.embeddingModel)
  }
  private def updateProvider(): Unit = providerLabel.setText(provider.id + " · index: selected scope, memory only")
  private def clearResults(): Unit = { model.clear(); snippet.setText("") }
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
    if (!build && semantic && previousIndex.isEmpty) status.setText("Build the semantic index first, or choose Exact text.")
    else {
      if (build) index = None
      status.setText(if (build) "Building semantic index…" else "Searching…")
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
          else if (semantic) hits = previousIndex.toSeq.flatMap(ScenarioSearch.semantic(documents, text, embedding, _, () => check()))
          else hits = ScenarioSearch.exact(documents, text, () => check())
          check()
        }
        override def onSuccess(): Unit = if (!disposed && generation.get() == id) {
          built.foreach(value => index = Some(value))
          hits.foreach(model.addElement)
          if (!model.isEmpty) results.setSelectedIndex(0)
          status.setText(built.map(i => s"Semantic index ready · ${i.vectors.size} distinct scenario texts · ${scope.getSelectedItem}")
            .getOrElse(s"${hits.size} scenarios · ${mode.getSelectedItem} · ${scope.getSelectedItem}"))
        }
        override def onCancel(): Unit = if (!disposed && generation.get() == id) {
          status.setText("Cancelled. Search or rebuild to try again.")
        }
        override def onThrowable(error: Throwable): Unit = if (!disposed && generation.get() == id) {
          status.setText("Search unavailable: " + Option(error.getMessage).getOrElse("check Ollama settings") + " Exact text remains available.")
        }
      }.queue()
    }
  }
  override def dispose(): Unit = { disposed = true; stop(); index = None }
}
