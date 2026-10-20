package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.github.vbmacher.intellij.cucumber.scala.settings.{ScalaCucumberConfigurable, ScalaCucumberSettings}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.{JBLabel, JBScrollPane}
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil

import java.awt.{BorderLayout, FlowLayout}
import java.awt.event.HierarchyEvent
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import javax.swing._
import javax.swing.table.DefaultTableModel

class RunHistoryPanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private type Group = (Seq[ScenarioHistoryEntry], Option[Scenarios.Location])
  private val results = RunResults.getInstance(project)
  private def settings = ScalaCucumberSettings.getInstance(project)
  private val model = new DefaultTableModel(Array[Object]("Scenario", "Latest result", "Attempts", "Last run", "Revision", "Environment"), 0) {
    override def isCellEditable(row: Int, column: Int): Boolean = false
  }
  private val table = new JBTable(model)
  private val status = new JBLabel()
  private val enableHistory = new JButton("Enable History")
  private val refresh = new JButton(AllIcons.Actions.Refresh)
  private val clear = new JButton(AllIcons.Actions.GC)
  private val configure = new JButton(AllIcons.General.Settings)
  private val attempts = new JButton("Attempts")
  private val open = new JButton("Open Scenario")
  private val toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4))
  private val notice = new JPanel(new BorderLayout())
  private var entries = Seq.empty[Group]
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[Seq[Group]]] = None
  private var disposed = false
  private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

  Seq(refresh -> "Refresh", clear -> "Clear History", configure -> "History Settings").foreach { case (button, name) =>
    button.setToolTipText(name)
    button.getAccessibleContext.setAccessibleName(name)
  }
  Seq(refresh, attempts, open, clear, configure).foreach(toolbar.add)
  notice.add(status, BorderLayout.CENTER)
  notice.add(enableHistory, BorderLayout.EAST)
  private val header = new JPanel(new BorderLayout())
  header.add(toolbar, BorderLayout.NORTH)
  header.add(notice, BorderLayout.SOUTH)
  add(header, BorderLayout.NORTH)
  add(new JBScrollPane(table), BorderLayout.CENTER)
  table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
  table.getEmptyText.setText("No retained runs")
  table.getSelectionModel.addListSelectionListener(_ => updateActions())
  Seq(220, 100, 130, 160, 100, 260).zipWithIndex.foreach { case (width, column) =>
    table.getColumnModel.getColumn(column).setPreferredWidth(width)
  }

  refresh.addActionListener(_ => reload())
  clear.addActionListener(_ => results.clearHistory())
  configure.addActionListener(_ => {
    ShowSettingsUtil.getInstance.showSettingsDialog(project, classOf[ScalaCucumberConfigurable])
    reload()
  })
  enableHistory.addActionListener(_ => {
    settings.runHistory = true
    results.refreshHistory()
  })
  attempts.addActionListener(_ => selected.foreach { case (same, current) =>
    val latest = same.reverse.maxBy(_.timestamp)
    val detail = new JTextArea(
      RunResults.summary(same) + s"\nRevision: ${latest.revision}\nEnvironment: ${latest.environment}\n" +
        (if (current.isEmpty) "Source renamed, changed or deleted: retained history is not assigned to a new scenario.\n" else "") +
        same.sortBy(_.timestamp).reverse.map(e => s"\n${time.format(Instant.ofEpochMilli(e.timestamp))} · ${e.status} · attempt ${e.attempt} · retry scheduled: ${e.retry} · run ${e.run}\n${e.error}").mkString("\n"),
      20, 80)
    detail.setEditable(false)
    detail.setCaretPosition(0)
    new DialogWrapper(project) {
      setTitle(latest.name + " — Run Attempts")
      setModal(false)
      setOKButtonText("Close")
      init()
      override def createActions(): Array[Action] = Array(getOKAction)
      override def createCenterPanel(): JComponent = new JBScrollPane(detail)
      override def getPreferredFocusedComponent: JComponent = detail
    }.show()
  })
  open.addActionListener(_ => selected.flatMap(_._2).foreach { location =>
    val descriptor = ScCucumber.inReadAction {
      val target = location.target
      Option.when(target.isValid)(new OpenFileDescriptor(project, target.getContainingFile.getVirtualFile, Scenarios.sourceOffset(target)))
    }
    descriptor.foreach(_.navigate(true))
  })
  project.getMessageBus.connect(this).subscribe(RunResults.HistoryChanged, new RunResults.HistoryListener {
    override def historyChanged(): Unit = reload()
  })
  addHierarchyListener(event => {
    if ((event.getChangeFlags & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing) reload()
  })

  private def selected: Option[Group] = entries.lift(table.getSelectedRow)

  private def updateActions(): Unit = {
    attempts.setEnabled(selected.nonEmpty)
    open.setEnabled(ScCucumber.inReadAction(selected.flatMap(_._2).exists(_.target.isValid)))
    clear.setEnabled(results.history.nonEmpty)
  }

  private def display(values: Seq[Group]): Unit = {
    val selection = selected.map { case (same, _) => val entry = same.head; (entry.identity, entry.revision, entry.environment) }
    entries = values
    model.setRowCount(0)
    values.foreach { case (same, current) =>
      val latest = same.reverse.maxBy(_.timestamp)
      model.addRow(Array[Object](latest.name + (if (current.isEmpty) " (source changed/deleted)" else ""), latest.status,
        s"${same.count(_.attempt == 0)} initial, ${same.count(_.attempt > 0)} retries",
        time.format(Instant.ofEpochMilli(latest.timestamp)), latest.revision.take(12), latest.environment))
    }
    val index = values.indexWhere { case (same, _) =>
      val entry = same.head
      selection.contains((entry.identity, entry.revision, entry.environment))
    }
    if (index >= 0) table.setRowSelectionInterval(index, index)
    updateActions()
  }

  private def reload(): Unit = {
    if (disposed) return
    pending.foreach(_.cancel())
    pending = None
    val snapshot = results.history
    status.setText(if (!settings.runHistory) "<html>History is off. Enable it to retain future Scala Cucumber runs.</html>"
      else if (settings.historyRetention == 0) "<html>Maximum history entries is 0. Increase it in History Settings to retain new runs.</html>"
      else "<html>Scala Cucumber runs appear automatically as scenarios finish.</html>")
    enableHistory.setVisible(!settings.runHistory)
    if (snapshot.isEmpty) display(Seq.empty)
    else pending = Some(ReadAction.nonBlocking[Seq[Group]](() => {
      val current = Scenarios.locations(project, GlobalSearchScope.projectScope(project), "").toOption.toSeq.flatten
        .map(location => RunResults.identity(location) -> location).toMap
      snapshot.groupBy(e => (e.identity, e.revision, e.environment)).toSeq.sortBy(_._2.map(_.timestamp).max).reverse
        .map { case ((identity, _, _), same) => same -> current.get(identity) }
    }).inSmartMode(project).expireWith(this).coalesceBy(this).finishOnUiThread(ModalityState.defaultModalityState(), display)
      .submit(AppExecutorUtil.getAppExecutorService))
  }
  reload()
  override def dispose(): Unit = {
    disposed = true
    pending.foreach(_.cancel())
  }
}
