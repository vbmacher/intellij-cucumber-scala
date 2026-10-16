package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.{JBList, JBScrollPane}
import com.intellij.util.concurrency.AppExecutorUtil

import java.awt.BorderLayout
import javax.swing._

class RunHistoryPanel(project: Project) extends JPanel(new BorderLayout()) with Disposable {
  private val results = project.getService(classOf[RunResults])
  private val model = new DefaultListModel[String]()
  private val historyList = new JBList[String](model)
  private val detail = new JTextArea()
  private val refresh = new JButton("Refresh")
  private val clear = new JButton("Clear History")
  private val open = new JButton("Open Current Scenario")
  private val toolbar = new JPanel()
  private var entries = Seq.empty[(Seq[ScenarioHistoryEntry], Option[ScenarioSelection.Location])]
  private var pending: Option[org.jetbrains.concurrency.CancellablePromise[Seq[(Seq[ScenarioHistoryEntry], Option[ScenarioSelection.Location])]]] = None
  toolbar.add(refresh); toolbar.add(clear); toolbar.add(open)
  add(toolbar, BorderLayout.NORTH)
  detail.setEditable(false)
  add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(historyList), new JBScrollPane(detail)), BorderLayout.CENTER)
  historyList.addListSelectionListener(_ => entries.lift(historyList.getSelectedIndex).foreach { case (same, current) =>
    detail.setText(RunResults.summary(same) + "\n" + (if (current.isEmpty) "Source renamed, changed or deleted: retained history is not assigned to a new scenario.\n" else "") +
      same.sortBy(_.timestamp).map(e => s"${java.time.Instant.ofEpochMilli(e.timestamp)} · ${e.status} · attempt ${e.attempt} · retry scheduled: ${e.retry} · run ${e.run}\n${e.error}").mkString("\n"))
  })
  refresh.addActionListener(_ => reload())
  clear.addActionListener(_ => { results.clearHistory(); reload() })
  open.addActionListener(_ => entries.lift(historyList.getSelectedIndex).flatMap(_._2).foreach { location =>
    val target = location.target
    if (target.isValid) new OpenFileDescriptor(project, target.getContainingFile.getVirtualFile, ScenarioSelection.sourceOffset(target)).navigate(true)
  })
  private def reload(): Unit = {
    pending.foreach(_.cancel())
    val snapshot = results.history
    pending = Some(ReadAction.nonBlocking[Seq[(Seq[ScenarioHistoryEntry], Option[ScenarioSelection.Location])]](() => {
      val current = ScenarioSelection.locations(project, GlobalSearchScope.projectScope(project), "").toOption.toSeq.flatten
        .map(location => RunResults.identity(location) -> location).toMap
      snapshot.groupBy(e => (e.identity, e.revision, e.environment)).toSeq.sortBy(_._2.map(_.timestamp).max).reverse
        .map { case ((identity, _, _), same) => same -> current.get(identity) }
    }).inSmartMode(project).expireWith(this).finishOnUiThread(ModalityState.defaultModalityState(), values => {
      entries = values
      model.clear()
      values.foreach { case (same, current) =>
        val entry = same.head
        model.addElement(entry.name + " · revision " + entry.revision + " · " + entry.environment +
          (if (current.isEmpty) " · SOURCE CHANGED/DELETED" else "") + " · " + same.count(_.attempt == 0) + " initial attempts")
      }
      if (values.isEmpty) model.addElement("No retained history. Enable it in Settings → Scala Cucumber, then run scenarios.")
    }).submit(AppExecutorUtil.getAppExecutorService))
  }
  reload()
  override def dispose(): Unit = pending.foreach(_.cancel())
}
