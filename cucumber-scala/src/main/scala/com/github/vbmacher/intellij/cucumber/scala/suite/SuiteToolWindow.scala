package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.github.vbmacher.intellij.cucumber.scala.completion.ScStepTemplates
import com.github.vbmacher.intellij.cucumber.scala.run.{RunHistoryPanel, ScCucumberConfigurationType, ScCucumberRunConfiguration}
import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory, ToolWindowManager}
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.{JBLabel, JBList, JBScrollPane, JBTextField}
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.{FormBuilder, JBUI, UIUtil}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}

import java.awt.{BorderLayout, Component, Dimension, FlowLayout}
import java.awt.event.{ActionEvent, MouseAdapter, MouseEvent}
import scala.jdk.CollectionConverters._
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeCellRenderer, DefaultTreeModel, TreeCellRenderer, TreeSelectionModel}

class SuiteToolWindowFactory extends ToolWindowFactory {
  override def createToolWindowContent(project: Project, window: ToolWindow): Unit = {
    val panel = new SuitePanel(project)
    val content = ContentFactory.getInstance().createContent(panel, "Suite", false)
    content.setDisposer(panel)
    window.getContentManager.addContent(content)
    val history = new RunHistoryPanel(project)
    val historyContent = ContentFactory.getInstance().createContent(history, "Run History", false)
    historyContent.setDisposer(history)
    window.getContentManager.addContent(historyContent)
    val paired = new PairedStepPanel(project)
    val pairedContent = ContentFactory.getInstance().createContent(paired, "Step Implementation", false)
    pairedContent.setDisposer(paired)
    window.getContentManager.addContent(pairedContent)
    val search = new ScenarioSearchPanel(project)
    val searchContent = ContentFactory.getInstance().createContent(search, "Scenario Search", false)
    searchContent.setDisposer(search)
    window.getContentManager.addContent(searchContent)
  }
}

class ShowSuiteAction extends AnAction("Scala Cucumber Suite") {
  override def actionPerformed(event: AnActionEvent): Unit =
    Option(event.getProject).flatMap(p => Option(ToolWindowManager.getInstance(p).getToolWindow("Scala Cucumber"))).foreach(_.activate(null))
}

class SuitePanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private val viewSelector = new JComboBox[SuiteView](SuiteView.values.toArray)
  private def selectedView: SuiteView = viewSelector.getItemAt(viewSelector.getSelectedIndex)
  private val kind = new JComboBox[String](Array("Any step kind", "Given", "When", "Then"))
  private val byModule = new JCheckBox("Module", true)
  private val byTag = new JCheckBox("Tag")
  private val byCategory = new JCheckBox("Category")
  private val tagFilter = new JBTextField()
  private val filter = new JBTextField()
  private val summary = new JBLabel().setAllowAutoWrapping(true).setCopyable(true)
  private val explanation = new JBLabel().setAllowAutoWrapping(true).setCopyable(true)
  private val resultsTitle = new JLabel()
  private val root = new DefaultMutableTreeNode("Suite")
  private val treeModel = new DefaultTreeModel(root)
  private val resultsTree = new Tree(treeModel) {
    override def getScrollableTracksViewportWidth: Boolean = true
  }
  private val relatedModel = new DefaultListModel[SuiteAnalysis.Source]()
  private val relatedList = sourceList(relatedModel)
  private val relatedTitle = new JLabel("Related sources")
  private val relatedPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  private var report: Option[SuiteAnalysis.Report] = None
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[SuiteAnalysis.Report]] = None
  private val queue = new MergingUpdateQueue("Scala Cucumber suite", 400, true, this, this)
  private var scanState = "Scanning project…"
  private val refresh = new JButton("Refresh")
  private val cancel = new JButton("Cancel")
  private val open = new JButton("Source")
  private val preview = new JButton("Preview")
  private val run = new JButton("Run")
  private val insert = new JButton("Insert")
  private val openRelated = new JButton("Open related source")
  private def labeled(text: String, component: JComponent): JPanel = {
    val panel = new JPanel(new BorderLayout(JBUI.scale(6), 0))
    val label = new JLabel(text)
    label.setLabelFor(component)
    panel.add(label, BorderLayout.WEST)
    panel.add(component, BorderLayout.CENTER)
    panel
  }
  Seq(viewSelector, kind).foreach(c => c.setMinimumSize(new Dimension(0, c.getPreferredSize.height)))
  private val viewRow = new JPanel(new BorderLayout(JBUI.scale(6), 0))
  private val scanButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0))
  scanButtons.add(refresh)
  scanButtons.add(cancel)
  viewRow.add(labeled("View:", viewSelector), BorderLayout.CENTER)
  viewRow.add(scanButtons, BorderLayout.EAST)
  private val groupingOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
  groupingOptions.add(new JLabel("Group by:"))
  Seq(byModule, byTag, byCategory).foreach(groupingOptions.add)
  byModule.setToolTipText("Group by sbt project, combining its main and test source sets. Reload sbt if a project is unresolved. Other builds use their IDE modules.")
  byTag.setToolTipText("Group by each scenario tag. A scenario can appear under several tags.")
  byCategory.setToolTipText("Group using the tag categories in Scala Cucumber settings.")
  groupingOptions.setToolTipText("Select several groups to nest Module → Tag → Category.")
  private val scenarioOptions = labeled("Tags:", tagFilter)
  private val definitionOptions = labeled("Step kind:", kind)
  private val header = FormBuilder.createFormBuilder().setVerticalGap(6)
    .addComponent(viewRow, 0)
    .addComponent(labeled("Filter:", filter))
    .addComponent(groupingOptions)
    .addComponent(scenarioOptions)
    .addComponent(definitionOptions)
    .addComponent(explanation).getPanel
  header.setBorder(JBUI.Borders.emptyBottom(8))
  setBorder(JBUI.Borders.empty(8))
  add(header, BorderLayout.NORTH)
  private val resultsPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)))
  resultsPanel.add(resultsTitle, BorderLayout.NORTH)
  resultsPanel.add(new JBScrollPane(resultsTree), BorderLayout.CENTER)
  private val actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
  Seq(open, preview, insert, run).foreach(actions.add)
  open.setToolTipText("Go to the selected source (Enter or double-click)")
  preview.setToolTipText("Preview the selected source")
  insert.setToolTipText("Insert the selected definition into the active feature editor")
  run.setToolTipText("Run selected scenarios or groups from one module")
  resultsPanel.add(actions, BorderLayout.SOUTH)
  relatedPanel.add(relatedTitle, BorderLayout.NORTH)
  relatedPanel.add(new JBScrollPane(relatedList), BorderLayout.CENTER)
  relatedPanel.add(openRelated, BorderLayout.SOUTH)
  relatedPanel.setBorder(JBUI.Borders.emptyTop(8))
  private val split = new OnePixelSplitter(true, 0.65f)
  split.setFirstComponent(resultsPanel)
  split.setSecondComponent(relatedPanel)
  add(split, BorderLayout.CENTER)
  summary.setBorder(JBUI.Borders.emptyTop(6))
  add(summary, BorderLayout.SOUTH)
  filter.getEmptyText.setText("Filter names, modules or file paths")
  filter.getAccessibleContext.setAccessibleName("Suite text filter")
  refresh.addActionListener(_ => scan())
  viewSelector.addActionListener(_ => display())
  kind.addActionListener(_ => display())
  Seq(byModule, byTag, byCategory).foreach(_.addActionListener(_ => display()))
  tagFilter.getEmptyText.setText("@smoke and not @ignored")
  tagFilter.getAccessibleContext.setAccessibleName("Scenario tag expression")
  tagFilter.setToolTipText("Cucumber tag expression for scenarios")
  tagFilter.getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = display()
    override def removeUpdate(e: DocumentEvent): Unit = display()
    override def changedUpdate(e: DocumentEvent): Unit = display()
  })
  filter.getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(event: DocumentEvent): Unit = display()
    override def removeUpdate(event: DocumentEvent): Unit = display()
    override def changedUpdate(event: DocumentEvent): Unit = display()
  })
  cancel.addActionListener(_ => {
    pending.foreach(_.cancel()); pending = None
    scanState = if (report.isEmpty) "Cancelled. Refresh to scan the project." else "Update cancelled; results may be outdated. Refresh to scan again."
    cancel.setVisible(false)
    display()
  })
  open.addActionListener(_ => selected.foreach(f => navigate(f.source)))
  openRelated.addActionListener(_ => Option(relatedList.getSelectedValue).foreach(navigate))
  resultsTree.addTreeSelectionListener(_ => showRelated())
  relatedList.addListSelectionListener(_ => openRelated.setEnabled(relatedList.getSelectedIndex >= 0))
  resultsTree.setRootVisible(false)
  resultsTree.setShowsRootHandles(true)
  resultsTree.setRowHeight(0)
  resultsTree.getSelectionModel.setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
  resultsTree.getAccessibleContext.setAccessibleName("Suite results")
  resultsTree.setCellRenderer(new TreeCellRenderer {
    private val groupRenderer = new DefaultTreeCellRenderer()
    groupRenderer.setOpenIcon(null)
    groupRenderer.setClosedIcon(null)
    override def getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
                                              leaf: Boolean, row: Int, focused: Boolean): Component = {
      value.asInstanceOf[DefaultMutableTreeNode].getUserObject match {
        case finding: SuiteAnalysis.Finding =>
          relatedList.getCellRenderer.getListCellRendererComponent(relatedList, finding.source, row, selected, focused)
        case _ => groupRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused)
      }
    }
  })
  ToolTipManager.sharedInstance().registerComponent(resultsTree)
  resultsTree.getInputMap.put(KeyStroke.getKeyStroke("ENTER"), "openSource")
  resultsTree.getActionMap.put("openSource", new AbstractAction {
    override def actionPerformed(event: ActionEvent): Unit = {
      if (selected.nonEmpty) open.doClick()
      else Option(resultsTree.getLeadSelectionPath).foreach { path =>
        if (resultsTree.isExpanded(path)) resultsTree.collapsePath(path) else resultsTree.expandPath(path)
      }
    }
  })
  resultsTree.addMouseListener(new MouseAdapter {
    override def mouseClicked(event: MouseEvent): Unit = {
      if (event.getButton == MouseEvent.BUTTON1 && event.getClickCount == 2 &&
        resultsTree.getPathForLocation(event.getX, event.getY) != null && selected.nonEmpty) open.doClick()
    }
  })
  installNavigation(relatedList, openRelated)
  preview.addActionListener(_ => selected.foreach { finding =>
    inReadAction(Option(finding.source.target).filter(_.isValid).map(_.getText))
      .foreach(text => Dialogs.confirm(project, finding.source.toString, text))
  })
  insert.addActionListener(_ => selected.filter(_.view == SuiteView.Definitions).foreach { finding =>
    val target = finding.source.target
    inReadAction(Option(target).filter(_.isValid).flatMap(t => ScCucumber.asCall(t.getParent)).flatMap(_.pattern)).foreach { expression =>
      val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor
      if (editor != null) {
        val document = editor.getDocument
        val source = inReadAction(com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(document))
        if (source.isInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile])
          com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, "Insert Scala step", null, () => {
            val step = Option(com.intellij.psi.util.PsiTreeUtil.getParentOfType(source.findElementAt(math.max(0, editor.getCaretModel.getOffset - 1)), classOf[org.jetbrains.plugins.cucumber.psi.GherkinStep], false))
            val start = step.map(s => s.getTextOffset + s.getText.indexOf(s.getName)).getOrElse(editor.getCaretModel.getOffset)
            val tail = step.map(s => start + s.getName.length).getOrElse(start)
            val prefix = step.map(_.getName).getOrElse("")
            val keyword = if (step.isEmpty) "Given " else ""
            if (keyword.nonEmpty) { document.insertString(start, keyword); editor.getCaretModel.moveToOffset(start + keyword.length) }
            ScStepTemplates.insert(editor, start + keyword.length, tail + keyword.length, target, expression,
              SuiteCatalog.entries(project, GlobalSearchScope.projectScope(project)).filter(_.definitions.exists(_.getElement == target)).map(_.step.getSubstitutedName), prefix)
          })
      }
    }
  })
  run.addActionListener(_ => {
    val settings = inReadAction {
      val targets = selectedFindings.map(_.source.target)
        .collect { case scenario: org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder if scenario.isValid => scenario }.distinct
      val selectedModules = targets.flatMap(s => moduleOf(s)).distinct
      if (targets.nonEmpty && selectedModules.size == 1) {
        val factory = new ScCucumberConfigurationType().getConfigurationFactories.head
        val settings = com.intellij.execution.RunManager.getInstance(project).createConfiguration("Scala Cucumber selection", factory)
        val configuration = settings.getConfiguration.asInstanceOf[ScCucumberRunConfiguration]
        configuration.setModule(selectedModules.head)
        configuration.featureUrl = targets.head.getContainingFile.getVirtualFile.getUrl
        configuration.selectedLocations = targets.map(s => s.getContainingFile.getVirtualFile.getPath + ":" + (s.getContainingFile.getText.take(Scenarios.sourceOffset(s)).count(_ == '\n') + 1))
        configuration.tags = tagFilter.getText
        Some(settings)
      } else None
    }
    settings match {
      case Some(value) => com.intellij.execution.ProgramRunnerUtil.executeConfiguration(value, com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance())
      case None => com.intellij.openapi.ui.Messages.showInfoMessage(project, "Select scenarios from one module to run together.", "Scala Cucumber Selection")
    }
  })
  PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter {
    // File-level events (create, delete, move) have no file; edits elsewhere cannot change the suite.
    private def changed(event: PsiTreeChangeEvent): Unit = Option(event.getFile) match {
      case Some(_: org.jetbrains.plugins.cucumber.psi.GherkinFile | _: org.jetbrains.plugins.scala.lang.psi.api.ScalaFile) | None => schedule()
      case _ =>
    }
    override def childrenChanged(event: PsiTreeChangeEvent): Unit = changed(event)
    override def childAdded(event: PsiTreeChangeEvent): Unit = changed(event)
    override def childRemoved(event: PsiTreeChangeEvent): Unit = changed(event)
    override def childReplaced(event: PsiTreeChangeEvent): Unit = changed(event)
    override def childMoved(event: PsiTreeChangeEvent): Unit = changed(event)
    override def propertyChanged(event: PsiTreeChangeEvent): Unit = changed(event)
  }, this)
  display()
  scan()

  private def sourceList(items: DefaultListModel[SuiteAnalysis.Source]): JBList[SuiteAnalysis.Source] = {
    val list = new JBList[SuiteAnalysis.Source](items) {
      override def getScrollableTracksViewportWidth: Boolean = true
    }
    list.setCellRenderer(new ListCellRenderer[SuiteAnalysis.Source] {
      private val title = new JLabel()
      private val location = new JBLabel(UIUtil.ComponentStyle.SMALL)
      private val cell = new JPanel(new BorderLayout(0, JBUI.scale(3))) {
        override def validate(): Unit = doLayout()
      }
      cell.setBorder(JBUI.Borders.empty(6, 8))
      cell.add(title, BorderLayout.NORTH)
      cell.add(location, BorderLayout.SOUTH)
      override def getListCellRendererComponent(list: JList[_ <: SuiteAnalysis.Source], source: SuiteAnalysis.Source,
                                               index: Int, selected: Boolean, focused: Boolean): Component = {
        title.setText(source.label)
        location.setText(source.location)
        cell.setToolTipText(source.toString)
        cell.setBackground(if (selected) list.getSelectionBackground else list.getBackground)
        title.setForeground(if (selected) list.getSelectionForeground else list.getForeground)
        location.setForeground(if (selected) list.getSelectionForeground else UIUtil.getContextHelpForeground)
        cell
      }
    })
    list
  }
  private def installNavigation(list: JList[_], button: JButton): Unit = {
    list.getInputMap.put(KeyStroke.getKeyStroke("ENTER"), "openSource")
    list.getActionMap.put("openSource", new AbstractAction {
      override def actionPerformed(event: ActionEvent): Unit = button.doClick()
    })
    list.addMouseListener(new MouseAdapter {
      override def mouseClicked(event: MouseEvent): Unit = {
        val index = list.getSelectedIndex
        if (event.getButton == MouseEvent.BUTTON1 && event.getClickCount == 2 && index >= 0 &&
          list.getCellBounds(index, index).contains(event.getPoint)) button.doClick()
      }
    })
  }
  private def navigate(source: SuiteAnalysis.Source): Unit = {
    val descriptor = inReadAction {
      Option(source.target).filter(_.isValid).map(t => new OpenFileDescriptor(project, t.getContainingFile.getVirtualFile, Scenarios.sourceOffset(t)))
    }
    descriptor match {
      case Some(value) => value.navigate(true)
      case None => summary.setText("Source changed or was deleted. Refresh to update the suite.")
    }
  }
  private def showRelated(): Unit = {
    relatedModel.clear()
    selected.foreach(_.related.foreach(relatedModel.addElement))
    val title = selectedView.relatedTitle
    relatedTitle.setText(s"$title · ${relatedModel.size}")
    relatedList.getEmptyText.setText(s"No ${title.toLowerCase(java.util.Locale.ROOT)} in this project")
    relatedPanel.setVisible(selected.nonEmpty && selectedView != SuiteView.Undefined)
    open.setEnabled(selected.nonEmpty)
    preview.setEnabled(selected.nonEmpty)
    insert.setVisible(selectedView == SuiteView.Definitions)
    insert.setEnabled(selected.nonEmpty)
    run.setVisible(selectedView == SuiteView.Scenarios)
    val selection = selectedFindings
    run.setEnabled(selection.nonEmpty && selection.forall(_.runtimeModuleName.nonEmpty) && selection.map(_.runtimeModuleName).distinct.size == 1)
    run.setToolTipText(if (run.isEnabled) s"Run ${selection.size} selected scenarios" else "Select scenarios or groups from one runnable module")
    openRelated.setEnabled(false)
    split.revalidate()
  }
  private def selected: Option[SuiteAnalysis.Finding] =
    Option(resultsTree.getLastSelectedPathComponent).collect { case node: DefaultMutableTreeNode => node.getUserObject }
      .collect { case finding: SuiteAnalysis.Finding => finding }
  private def selectedFindings: Seq[SuiteAnalysis.Finding] =
    Option(resultsTree.getSelectionPaths).toSeq.flatten.toSeq.flatMap { path =>
      path.getLastPathComponent.asInstanceOf[DefaultMutableTreeNode].depthFirstEnumeration().asScala
        .collect { case node: DefaultMutableTreeNode => node.getUserObject }
        .collect { case finding: SuiteAnalysis.Finding => finding }
    }.distinctBy(_.source.target)
  private def schedule(): Unit = queue.queue(new Update("scan") { override def run(): Unit = scan(keepResults = true) })
  // Automatic rescans keep the current results and selection visible until the new report arrives.
  private def scan(keepResults: Boolean = false): Unit = {
    pending.foreach(_.cancel())
    pending = None
    cancel.setVisible(true)
    if (keepResults && report.nonEmpty) {
      scanState = "Updating…"
      summary.setText(scanState)
    } else {
      report = None
      scanState = "Scanning project…"
      display()
    }
    val selectedModules = inReadAction(ModuleManager.getInstance(project).getModules.toSeq)
    val searchScope = GlobalSearchScope.projectScope(project)
    pending = Some(ReadAction.nonBlocking[SuiteAnalysis.Report](() => SuiteAnalysis.scan(project, selectedModules, searchScope))
      .inSmartMode(project).expireWith(this).finishOnUiThread(ModalityState.defaultModalityState(), value => {
        report = Some(value)
        pending = None
        cancel.setVisible(false)
        scanState = "Project · " + value.summary
        display()
      }).submit(AppExecutorUtil.getAppExecutorService))
  }
  private def display(): Unit = {
    val view = selectedView
    val hasFeatureTags = view.hasFeatureTags
    byTag.setVisible(hasFeatureTags)
    byCategory.setVisible(hasFeatureTags)
    scenarioOptions.setVisible(view == SuiteView.Scenarios)
    definitionOptions.setVisible(view == SuiteView.Definitions)
    explanation.setText(view.description)
    val query = filter.getText.toLowerCase(java.util.Locale.ROOT)
    val parsedTags = scala.util.Try(io.cucumber.tagexpressions.TagExpressionParser.parse(tagFilter.getText)).toOption
    val baseRows = report.toSeq.flatMap(_.findings).filter(f => f.view == view &&
      (f.source.toString + " " + f.moduleGroup.name).toLowerCase(java.util.Locale.ROOT).contains(query))
      .filter(f => kind.getSelectedIndex == 0 || view != SuiteView.Definitions || f.keyword.exists(_.equalsIgnoreCase(kind.getSelectedItem.toString)))
      .filter(f => view != SuiteView.Scenarios || parsedTags.exists(expression => f.tagSets.exists(tags => expression.evaluate(tags.asJava))))
    val categories = ScalaCucumberSettings.getInstance(project).tagCategories.linesIterator.flatMap { line =>
      val parts = line.split("=>", 2)
      if (parts.length == 2) scala.util.Try(io.cucumber.tagexpressions.TagExpressionParser.parse(parts(1).trim)).toOption.map(parts(0).trim -> _) else None
    }.toSeq
    val levels = Seq(
      Option.when(byModule.isSelected)((f: SuiteAnalysis.Finding) => Seq(f.moduleGroup.id -> f.moduleGroup.name)),
      Option.when(byTag.isSelected && hasFeatureTags)((f: SuiteAnalysis.Finding) => {
        val tags = f.tagSets.flatten.distinct.sorted
        (if (tags.nonEmpty) tags else Seq("Untagged")).map(t => t -> t)
      }),
      Option.when(byCategory.isSelected && hasFeatureTags)((f: SuiteAnalysis.Finding) => {
        val names = categories.collect { case (name, expression) if f.tagSets.exists(tags => expression.evaluate(tags.asJava)) => name }.distinct
        (if (names.nonEmpty) names else Seq("Uncategorized")).map(n => n -> n)
      })
    ).flatten
    def populate(parent: DefaultMutableTreeNode, findings: Seq[SuiteAnalysis.Finding], remaining: Seq[SuiteAnalysis.Finding => Seq[(String, String)]]): Unit = {
      remaining.headOption match {
        case Some(groups) =>
          findings.flatMap(f => groups(f).map(_ -> f)).groupBy(_._1).toSeq.sortBy(_._1.swap).foreach { case ((_, name), members) =>
            val items = members.map(_._2).distinctBy(_.source.target)
            val node = new DefaultMutableTreeNode(s"$name · ${items.size}")
            parent.add(node)
            populate(node, items, remaining.drop(1))
          }
        case None => findings.sortBy(f => (f.source.label, f.source.location)).foreach(f => parent.add(new DefaultMutableTreeNode(f, false)))
      }
    }
    val previousSelection = Option(resultsTree.getSelectionPaths).toSeq.flatten.map(_.getLastPathComponent)
      .collect { case node: DefaultMutableTreeNode => node.getUserObject }
      .collect { case finding: SuiteAnalysis.Finding => finding.source.target }.toSet
    root.removeAllChildren()
    populate(root, baseRows, levels)
    treeModel.reload()
    var row = 0
    while (row < resultsTree.getRowCount) { resultsTree.expandRow(row); row += 1 }
    val restored = root.depthFirstEnumeration().asScala.collect { case node: DefaultMutableTreeNode => node }
      .filter(_.getUserObject match { case finding: SuiteAnalysis.Finding => previousSelection(finding.source.target); case _ => false })
      .map(node => new javax.swing.tree.TreePath(node.getPath.map(_.asInstanceOf[AnyRef]))).toArray
    if (restored.nonEmpty) resultsTree.setSelectionPaths(restored)
    summary.setText(if (view == SuiteView.Scenarios && parsedTags.isEmpty) "Invalid tag expression. Correct the Tags field to show scenarios." else scanState)
    resultsTitle.setText(s"$view · ${baseRows.size}")
    resultsTree.getEmptyText.setText(if (report.isEmpty) scanState else if (view == SuiteView.Scenarios && parsedTags.isEmpty) "Invalid tag expression"
      else if (query.nonEmpty || (view == SuiteView.Scenarios && tagFilter.getText.nonEmpty) || (view == SuiteView.Definitions && kind.getSelectedIndex != 0)) "No results match these filters"
      else s"No ${view.label.toLowerCase(java.util.Locale.ROOT)} in this project")
    showRelated()
    revalidate()
  }
  override def dispose(): Unit = pending.foreach(_.cancel())
}
