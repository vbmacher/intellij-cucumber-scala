package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory, ToolWindowManager}
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.{JBList, JBScrollPane, JBTextField}
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}

import java.awt.BorderLayout
import scala.jdk.CollectionConverters._
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}

class SuiteToolWindowFactory extends ToolWindowFactory {
  override def createToolWindowContent(project: Project, window: ToolWindow): Unit = {
    val panel = new SuitePanel(project)
    val content = ContentFactory.getInstance().createContent(panel, "Suite", false)
    content.setDisposer(panel)
    window.getContentManager.addContent(content)
    val paired = new PairedStepPanel(project)
    val pairedContent = ContentFactory.getInstance().createContent(paired, "Implementation", false)
    pairedContent.setDisposer(paired)
    window.getContentManager.addContent(pairedContent)
  }
}

class ShowSuiteAction extends AnAction("Scala Cucumber Suite") {
  override def actionPerformed(event: AnActionEvent): Unit =
    Option(event.getProject).flatMap(p => Option(ToolWindowManager.getInstance(p).getToolWindow("Scala Cucumber"))).foreach(_.activate(null))
}

class SuitePanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private val modules = ModuleManager.getInstance(project).getModules.sortBy(_.getName)
  private val scope = new JComboBox[String]((Array("Project contents") ++ modules.map(_.getName)))
  private val category = new JComboBox[String](Array("Steps", "Unused"))
  private val kind = new JComboBox[String](Array("Any step kind", "Given", "When", "Then"))
  private val filter = new JBTextField()
  private val summary = new JLabel("Choose a scope and refresh. Static matches do not measure execution or business coverage.")
  private val model = new DefaultListModel[String]()
  private val resultsList = new JBList[String](model)
  private var rows = Seq.empty[SuiteAnalysis.Finding]
  private var report: Option[SuiteAnalysis.Report] = None
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[SuiteAnalysis.Report]] = None
  private val queue = new MergingUpdateQueue("Scala Cucumber suite", 400, true, this, this)
  private val buttons = new JPanel()
  private val refresh = new JButton("Refresh")
  private val cancel = new JButton("Cancel")
  private val open = new JButton("Go to source")
  private val preview = new JButton("Preview")
  private val insert = new JButton("Insert step")
  buttons.add(scope); buttons.add(category); buttons.add(kind); buttons.add(filter); buttons.add(refresh); buttons.add(cancel); buttons.add(open); buttons.add(preview); buttons.add(insert)
  add(buttons, BorderLayout.NORTH)
  add(new JBScrollPane(resultsList), BorderLayout.CENTER)
  add(summary, BorderLayout.SOUTH)
  filter.setColumns(18)
  refresh.addActionListener(_ => scan())
  scope.addActionListener(_ => scan())
  category.addActionListener(_ => display())
  kind.addActionListener(_ => display())
  filter.getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(event: DocumentEvent): Unit = display()
    override def removeUpdate(event: DocumentEvent): Unit = display()
    override def changedUpdate(event: DocumentEvent): Unit = display()
  })
  cancel.addActionListener(_ => { pending.foreach(_.cancel()); summary.setText("Cancelled · results are incomplete; refresh to obtain a complete report.") })
  open.addActionListener(_ => selected.foreach { finding =>
    val target = finding.target
    if (target.isValid) new OpenFileDescriptor(project, target.getContainingFile.getVirtualFile, target.getTextOffset).navigate(true)
  })
  preview.addActionListener(_ => selected.foreach { finding =>
    if (finding.target.isValid) EditPreview.confirm(project, finding.label, finding.target.getText)
  })
  insert.addActionListener(_ => selected.filter(_.category == "Steps").foreach { finding =>
    ScCucumber.asCall(finding.target.getParent).flatMap(_.pattern).foreach { expression =>
      val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor
      if (editor != null) {
        val document = editor.getDocument
        val source = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(document)
        if (source.isInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile])
          com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, "Insert Scala step", null, () => {
            val step = Option(com.intellij.psi.util.PsiTreeUtil.getParentOfType(source.findElementAt(math.max(0, editor.getCaretModel.getOffset - 1)), classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep], false))
            val start = step.map(s => s.getTextOffset + s.getText.indexOf(s.getName)).getOrElse(editor.getCaretModel.getOffset)
            val tail = step.map(s => start + s.getName.length).getOrElse(start)
            val prefix = step.map(_.getName).getOrElse("")
            val keyword = if (step.isEmpty) "Given " else ""
            if (keyword.nonEmpty) { document.insertString(start, keyword); editor.getCaretModel.moveToOffset(start + keyword.length) }
            ScStepTemplates.insert(editor, start + keyword.length, tail + keyword.length, finding.target, expression,
              SuiteCatalog.entries(project, GlobalSearchScope.projectScope(project)).filter(_.definitions.exists(_.getElement == finding.target)).map(_.step.getSubstitutedName), prefix)
          })
      }
    }
  })
  PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter {
    override def childrenChanged(event: PsiTreeChangeEvent): Unit = schedule()
    override def childAdded(event: PsiTreeChangeEvent): Unit = schedule()
    override def childRemoved(event: PsiTreeChangeEvent): Unit = schedule()
    override def childReplaced(event: PsiTreeChangeEvent): Unit = schedule()
    override def propertyChanged(event: PsiTreeChangeEvent): Unit = schedule()
  }, this)
  scan()

  private def selected: Option[SuiteAnalysis.Finding] = rows.lift(resultsList.getSelectedIndex)
  private def schedule(): Unit = queue.queue(new Update("scan") { override def run(): Unit = scan() })
  private def scan(): Unit = {
    pending.foreach(_.cancel())
    summary.setText("Scanning selected scope…")
    val selectedModules = if (scope.getSelectedIndex == 0) modules.toSeq else modules.lift(scope.getSelectedIndex - 1).toSeq
    val searchScope = if (scope.getSelectedIndex == 0) GlobalSearchScope.projectScope(project)
      else selectedModules.head.getModuleWithDependenciesAndLibrariesScope(true)
    pending = Some(ReadAction.nonBlocking[SuiteAnalysis.Report](() => SuiteAnalysis.scan(project, selectedModules, searchScope))
      .inSmartMode(project).expireWith(this).finishOnUiThread(ModalityState.defaultModalityState(), value => {
        report = Some(value)
        summary.setText(value.summary + " · complete static analysis; external consumers unknown")
        display()
      }).submit(AppExecutorUtil.getAppExecutorService))
  }
  private def display(): Unit = {
    val query = filter.getText.toLowerCase(java.util.Locale.ROOT)
    rows = report.toSeq.flatMap(_.findings).filter(f => f.category == category.getSelectedItem && f.label.toLowerCase(java.util.Locale.ROOT).contains(query))
      .filter(f => kind.getSelectedIndex == 0 || f.category != "Steps" || asCall(f.target.getParent).flatMap(_.keyword).exists(k =>
        Option(org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider.getKeywordProvider(true).getBaseKeyword("en", k)).exists(_.equalsIgnoreCase(kind.getSelectedItem.toString))))
    model.clear()
    rows.foreach(f => model.addElement(f.label))
    if (rows.isEmpty) model.addElement("No findings in this category and scope.")
  }
  override def dispose(): Unit = pending.foreach(_.cancel())
}
