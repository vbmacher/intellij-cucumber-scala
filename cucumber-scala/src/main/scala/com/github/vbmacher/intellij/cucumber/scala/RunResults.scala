package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzer, LineMarkerInfo, LineMarkerProvider}
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{PersistentStateComponent, State, Storage, StoragePathMacros}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.{PsiModificationTracker, PsiTreeUtil}
import org.jetbrains.plugins.cucumber.psi.{GherkinStep, GherkinTableRow}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util
import scala.beans.BeanProperty
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object RunResults {
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
    digest(file.getVirtualFile.getUrl + "\n" + signature(scenario) + "\n" + sameScenarios.indexOf(scenario) + "\n" +
      (if (location.target == scenario) "" else location.target.getText) + "\n" + rowOccurrence + "\n" + location.tags.sorted.mkString(" "))
  }
  def uri(value: String): String = if (value.startsWith("file:")) java.nio.file.Path.of(java.net.URI.create(value)).toUri.toString else value
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

}

class RunResults(project: Project) {
  import RunResults._
  private val pendingErrors = mutable.Map.empty[(String, String), Vector[String]]
  private val decorations = mutable.LinkedHashMap.empty[(String, String, Int, String), Decoration]
  private def settings = project.getService(classOf[ScalaCucumberSettings])
  def record(result: CucumberMessages.StepResult, context: Context): Unit = {
    synchronized {
      if (result.error.nonEmpty) {
        val key = result.run -> result.selector
        pendingErrors(key) = (pendingErrors.getOrElse(key, Vector.empty) :+ result.error).distinct
      }
      if (settings.resultDecorations) {
        decorations((result.run, uri(result.uri), result.line, result.selector)) = Decoration(result, context.stamp, context.started)
        while (decorations.size > 5000) decorations.remove(decorations.head._1)
      }
    }
    refresh()
  }
  def record(result: CucumberMessages.ScenarioResult, context: Context): Unit = {
    val error = synchronized { pendingErrors.remove(result.run -> (result.uri + ":" + result.line)).getOrElse(Vector.empty).mkString("\n") }
    record(CucumberMessages.StepResult(result.run, result.uri, result.line, result.uri + ":" + result.line, result.status, 0L, error), context)
    synchronized { pendingErrors.remove(result.run -> (result.uri + ":" + result.line)); () }
  }
  def at(element: PsiElement): Seq[Decoration] = {
    if (!settings.resultDecorations) return Seq.empty
    val file = element.getContainingFile
    val line = file.getText.take(element.getTextOffset).count(_ == '\n') + 1
    synchronized { decorations.values.filter(d => key(d.result.uri, d.result.line) == key(file.getVirtualFile.getUrl, line)).toVector.sortBy(d => (d.timestamp, d.result.run)) }
  }
  def stale(decoration: Decoration): Boolean = decoration.stamp != PsiModificationTracker.getInstance(project).getModificationCount
  def describe(element: PsiElement): String = at(element).map { decoration =>
    val r = decoration.result
    s"${r.status} · ${r.durationMillis} ms · run ${r.run} · ${r.selector}" + (if (stale(decoration)) " · STALE (source changed)" else "") +
      (if (r.error.nonEmpty) "\n" + r.error else "")
  }.mkString("\n\n")
  def clearDecorations(): Unit = { synchronized { decorations.clear() }; refresh() }
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
      val results = source.getProject.getService(classOf[RunResults])
      val values = results.at(source)
      values.lastOption.map { last =>
        val icon = if (results.stale(last)) AllIcons.General.Warning else last.result.status match {
          case "PASSED" => AllIcons.RunConfigurations.TestPassed
          case "FAILED" | "AMBIGUOUS" | "UNDEFINED" => AllIcons.RunConfigurations.TestFailed
          case _ => AllIcons.RunConfigurations.TestIgnored
        }
        new LineMarkerInfo[PsiElement](element, element.getTextRange, icon,
          new com.intellij.util.Function[PsiElement, String] { override def fun(e: PsiElement): String = results.describe(source) },
          new com.intellij.codeInsight.daemon.GutterIconNavigationHandler[PsiElement] {
            override def navigate(event: java.awt.event.MouseEvent, elt: PsiElement): Unit = EditPreview.confirm(source.getProject, "Cucumber Result Details", results.describe(source))
          }, GutterIconRenderer.Alignment.RIGHT,
          new java.util.function.Supplier[String] { override def get(): String = "Cucumber execution result" })
      }
    }.orNull
  }
}
