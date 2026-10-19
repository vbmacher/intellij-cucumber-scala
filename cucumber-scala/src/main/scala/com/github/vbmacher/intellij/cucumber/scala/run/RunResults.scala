package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.settings.ScalaCucumberSettings
import com.github.vbmacher.intellij.cucumber.scala.util.Dialogs
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, LineMarkerInfo, LineMarkerProvider}
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{PersistentStateComponent, State, Storage, StoragePathMacros}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.{PsiModificationTracker, PsiTreeUtil}
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.cucumber.psi.{GherkinStep, GherkinTableRow}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util
import scala.beans.BeanProperty
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ScenarioHistoryEntry {
  @BeanProperty var identity: String = ""
  @BeanProperty var run: String = ""
  @BeanProperty var uri: String = ""
  @BeanProperty var line: Int = 0
  @BeanProperty var name: String = ""
  @BeanProperty var revision: String = ""
  @BeanProperty var environment: String = ""
  @BeanProperty var timestamp: Long = 0L
  @BeanProperty var attempt: Int = 0
  @BeanProperty var status: String = ""
  @BeanProperty var error: String = ""
  @BeanProperty var retry: Boolean = false
}
class ScenarioHistoryState {
  @BeanProperty var entries: util.ArrayList[ScenarioHistoryEntry] = new util.ArrayList[ScenarioHistoryEntry]()
}

object RunResults {
  def getInstance(project: Project): RunResults = project.getService(classOf[RunResults])

  trait HistoryListener {
    def historyChanged(): Unit
  }
  val HistoryChanged: Topic[HistoryListener] = Topic.create("Scala Cucumber run history", classOf[HistoryListener])

  final case class Context(stamp: Long, revision: String, environment: String, identities: Map[String, String], started: Long)
  final case class Decoration(result: CucumberMessages.StepResult, stamp: Long, timestamp: Long)
  def digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  def identity(location: ScenarioSelection.Location): String = {
    def signature(scenario: org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder): String =
      Option(PsiTreeUtil.getParentOfType(scenario, classOf[org.jetbrains.plugins.cucumber.psi.GherkinRule])).map(_.getRuleName).getOrElse("") + "\n" +
        scenario.getScenarioName + "\n" + scenario.getSteps.map(_.getText).mkString("\n")
    val scenario = location.scenario
    val file = scenario.getContainingFile
    val sameScenarios = PsiTreeUtil.findChildrenOfType(file, classOf[org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder]).asScala.toSeq
      .filter(s => signature(s) == signature(scenario))
    val rowOccurrence = scenario match {
      case outline: org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline if location.target != scenario =>
        outline.getExamplesBlocks.asScala.toSeq.flatMap(block => Option(block.getTable).toSeq.flatMap(_.getDataRows.asScala))
          .filter(_.getText == location.target.getText).indexOf(location.target)
      case _ => 0
    }
    val rowSignature = if (location.target == scenario) "" else {
      val header = Option(PsiTreeUtil.getParentOfType(location.target, classOf[org.jetbrains.plugins.cucumber.psi.GherkinTable]))
        .flatMap(table => Option(table.getHeaderRow)).map(_.getText).getOrElse("")
      header + "\n" + location.target.getText
    }
    digest(file.getVirtualFile.getUrl + "\n" + signature(scenario) + "\n" + sameScenarios.indexOf(scenario) + "\n" +
      rowSignature + "\n" + rowOccurrence + "\n" + location.tags.sorted.mkString(" "))
  }
  /** Normalizes a percent-encoded Cucumber URI. */
  def uri(value: String): String =
    if (value.startsWith("file:")) scala.util.Try(java.nio.file.Path.of(java.net.URI.create(value)).toUri.toString).getOrElse(value) else value
  /** Normalizes an IntelliJ VFS URL, which is not percent-encoded, to the form produced by [[uri]]. */
  def fileUri(url: String): String =
    if (url.startsWith(com.intellij.openapi.vfs.StandardFileSystems.FILE_PROTOCOL_PREFIX))
      scala.util.Try(java.nio.file.Path.of(com.intellij.openapi.vfs.VfsUtilCore.urlToPath(url)).toUri.toString).getOrElse(url)
    else url
  def key(uri: String, line: Int): String = RunResults.uri(uri) + ":" + line
  def revision(project: Project): String = {
    // Read only the repository metadata; no external command or credentials enter history.
    val root = Option(project.getBasePath).map(java.nio.file.Path.of(_))
    root.flatMap { path =>
      val git = path.resolve(".git")
      val directory = if (java.nio.file.Files.isDirectory(git)) Some(git)
        else scala.util.Try(java.nio.file.Files.readString(git).stripPrefix("gitdir:").trim).toOption.map(path.resolve(_).normalize())
      directory.flatMap { dir => scala.util.Try {
        val head = java.nio.file.Files.readString(dir.resolve("HEAD")).trim
        if (!head.startsWith("ref: ")) head
        else {
          val ref = head.stripPrefix("ref: ")
          scala.util.Try(java.nio.file.Files.readString(dir.resolve(ref)).trim).getOrElse {
            java.nio.file.Files.readAllLines(dir.resolve("packed-refs")).asScala.find(_.endsWith(" " + ref)).map(_.takeWhile(_ != ' ')).getOrElse("unknown")
          }
        }
      }.toOption }
    }.getOrElse("unknown")
  }
  def summary(entries: Seq[ScenarioHistoryEntry]): String = {
    val firstAttempts = entries.filter(_.attempt == 0)
    val counts = firstAttempts.groupBy(_.status).toSeq.sortBy(_._1).map { case (status, same) => s"$status: ${same.size}" }.mkString(" · ")
    val outcomes = firstAttempts.filter(e => Set("PASSED", "FAILED").contains(e.status)).map(_.status).distinct
    s"${firstAttempts.size} initial attempts · $counts · ${entries.count(_.attempt > 0)} retries" +
      (if (outcomes.size > 1) "\nSignal: inconsistent observed outcomes; investigate (no root cause inferred)." else "")
  }
}

@State(name = "ScalaCucumberRunHistory", storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE)))
class RunResults(project: Project) extends PersistentStateComponent[ScenarioHistoryState] {
  import RunResults._
  private var state = new ScenarioHistoryState
  private val pendingErrors = mutable.Map.empty[(String, String), Vector[String]]
  private val decorations = mutable.LinkedHashMap.empty[(String, String, Int, String), Decoration]
  private def settings = ScalaCucumberSettings.getInstance(project)
  override def getState: ScenarioHistoryState = synchronized {
    val snapshot = new ScenarioHistoryState
    snapshot.entries = new util.ArrayList[ScenarioHistoryEntry](state.entries)
    snapshot
  }
  override def loadState(value: ScenarioHistoryState): Unit = synchronized { state = value; trim() }
  def history: Seq[ScenarioHistoryEntry] = synchronized { state.entries.asScala.toVector }
  private def trim(): Unit = while (state.entries.size() > math.max(0, settings.historyRetention)) state.entries.remove(0)
  def record(result: CucumberMessages.StepResult, context: Context): Unit = {
    synchronized {
      if ((settings.runHistory || settings.failureExplanations) && result.error.nonEmpty) {
        val key = result.run -> result.selector
        pendingErrors(key) = (pendingErrors.getOrElse(key, Vector.empty) :+ result.error).distinct
      }
      if (settings.resultDecorations || settings.failureExplanations) {
        decorations((result.run, uri(result.uri), result.line, result.selector)) = Decoration(result, context.stamp, context.started)
        while (decorations.size > 5000) decorations.remove(decorations.head._1)
      }
    }
    refresh()
  }
  def record(result: CucumberMessages.ScenarioResult, context: Context): Unit = {
    val error = synchronized { pendingErrors.remove(result.run -> (result.uri + ":" + result.line)).getOrElse(Vector.empty).mkString("\n") }
    record(CucumberMessages.StepResult(result.run, result.uri, result.line, result.uri + ":" + result.line, result.status, 0L, error), context)
    synchronized {
      pendingErrors.remove(result.run -> (result.uri + ":" + result.line))
      if (settings.runHistory) {
      val entry = new ScenarioHistoryEntry
      entry.identity = context.identities.getOrElse(key(result.uri, result.line), digest(key(result.uri, result.line) + result.name))
      entry.run = result.run; entry.uri = result.uri; entry.line = result.line; entry.name = result.name
      entry.status = result.status; entry.attempt = result.attempt; entry.retry = result.willRetry
      entry.error = error
      entry.revision = context.revision; entry.environment = context.environment; entry.timestamp = System.currentTimeMillis()
      state.entries.add(entry)
      trim()
      }
    }
    refreshHistory()
  }
  def at(element: PsiElement): Seq[Decoration] = {
    if (!settings.resultDecorations) return Seq.empty
    val file = element.getContainingFile
    val line = file.getText.take(element.getTextOffset).count(_ == '\n') + 1
    val expected = key(fileUri(file.getVirtualFile.getUrl), line)
    synchronized { decorations.values.filter(d => key(d.result.uri, d.result.line) == expected).toVector.sortBy(d => (d.timestamp, d.result.run)) }
  }
  def stale(decoration: Decoration): Boolean = decoration.stamp != PsiModificationTracker.getInstance(project).getModificationCount
  def failures(featureUrl: String): Seq[Decoration] = {
    val expected = fileUri(featureUrl)
    synchronized { decorations.values.filter(d => uri(d.result.uri) == expected && Set("FAILED", "AMBIGUOUS", "UNDEFINED")(d.result.status)).toVector }
  }
  def contains(decoration: Decoration): Boolean = synchronized {
    val r = decoration.result
    decorations.get((r.run, uri(r.uri), r.line, r.selector)).contains(decoration)
  }
  def describe(element: PsiElement): String = at(element).map { decoration =>
    val r = decoration.result
    s"${r.status} · ${r.durationMillis} ms · run ${r.run} · ${r.selector}" + (if (stale(decoration)) " · STALE (source changed)" else "") +
      (if (r.error.nonEmpty) "\n" + r.error else "")
  }.mkString("\n\n")
  def clearDecorations(): Unit = { synchronized { decorations.clear() }; refresh() }
  def clearHistory(): Unit = {
    synchronized { state.entries.clear(); pendingErrors.clear() }
    refreshHistory()
  }
  def retentionChanged(): Unit = {
    synchronized { trim() }
    refreshHistory()
  }
  def refreshHistory(): Unit = ApplicationManager.getApplication.invokeLater(() => {
    if (!project.isDisposed) project.getMessageBus.syncPublisher(HistoryChanged).historyChanged()
  })
  private def refresh(): Unit = ApplicationManager.getApplication.invokeLater(() => {
    if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart()
  })
}

class FeatureResultLineMarker extends LineMarkerProvider {
  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_] = {
    val step = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinStep], false)).filter(_.getFirstChild == element)
    val row = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinTableRow], false)).filter(r => r.getFirstChild == element && r.getParent.getParent.isInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock])
    val target = step.orElse(row)
    target.flatMap { source =>
      val results = RunResults.getInstance(source.getProject)
      val values = results.at(source)
      // Outline and Background steps run once per row or scenario; show the worst outcome of the latest run.
      val latest = values.lastOption.toSeq.flatMap(last => values.filter(_.result.run == last.result.run))
      latest.minByOption(d => d.result.status match {
        case "FAILED" | "AMBIGUOUS" | "UNDEFINED" => 0
        case "PASSED" => 2
        case _ => 1
      }).map { last =>
        val icon = if (results.stale(last)) AllIcons.General.Warning else last.result.status match {
          case "PASSED" => AllIcons.RunConfigurations.TestPassed
          case "FAILED" | "AMBIGUOUS" | "UNDEFINED" => AllIcons.RunConfigurations.TestFailed
          case _ => AllIcons.RunConfigurations.TestIgnored
        }
        new LineMarkerInfo[PsiElement](element, element.getTextRange, icon,
          new com.intellij.util.Function[PsiElement, String] { override def fun(e: PsiElement): String = results.describe(source) },
          new com.intellij.codeInsight.daemon.GutterIconNavigationHandler[PsiElement] {
            override def navigate(event: java.awt.event.MouseEvent, elt: PsiElement): Unit = Dialogs.confirm(source.getProject, "Cucumber Result Details", results.describe(source))
          }, GutterIconRenderer.Alignment.RIGHT,
          new java.util.function.Supplier[String] { override def get(): String = "Cucumber execution result" })
      }
    }.orNull
  }
}
