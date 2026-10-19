package com.github.vbmacher.intellij.cucumber.scala.explanation

import com.github.vbmacher.intellij.cucumber.scala.run.{RunResults, ScenarioSelection}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.{inReadAction, matchingSteps}
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.util.{PsiModificationTracker, PsiTreeUtil}
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._

object FailureContext {
  val failureStatuses: Set[String] = Set("FAILED", "AMBIGUOUS", "UNDEFINED")
  val maxContextLength: Int = 32000

  final case class Source(label: String, file: VirtualFile, offset: Int, line: Int, endLine: Int, text: String) {
    override def toString: String = s"$label · ${file.getName}:$line" + (if (endLine > line) s"-$endLine" else "")
    def open(project: Project): Unit = if (file.isValid) new OpenFileDescriptor(project, file, offset).navigate(true)
  }

  final case class Snapshot(result: RunResults.Decoration, stamp: Long, title: String, facts: String,
                            text: String, sources: Seq[Source]) {
    def problem(project: Project): Option[String] = inReadAction {
      if (project.isDisposed || sources.exists(!_.file.isValid)) Some("A source file was deleted. Run the scenario again before explaining it.")
      else if (stamp != PsiModificationTracker.getInstance(project).getModificationCount)
        Some("Source changed after this run or context review. Run the scenario again before explaining it.")
      else if (!RunResults.getInstance(project).contains(result))
        Some("This run result was cleared or replaced. Run the scenario again before explaining it.")
      else None
    }
  }

  private def selectorLine(value: String): Option[Int] = value.substring(value.lastIndexOf(':') + 1).toIntOption
  private def line(element: PsiElement): Int = element.getContainingFile.getText.take(ScenarioSelection.sourceOffset(element)).count(_ == '\n') + 1

  /** Called under a read action; only recorded failures in the explicit editor selection are offered. */
  def candidates(file: GherkinFile, offset: Int): Seq[RunResults.Decoration] = {
    val element = file.findElementAt(math.min(offset, math.max(0, file.getTextLength - 1)))
    val step = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinStep], false))
    val row = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinTableRow], false))
      .filter(r => Option(PsiTreeUtil.getParentOfType(r, classOf[GherkinExamplesBlock])).nonEmpty)
    val scenario = Option(PsiTreeUtil.getParentOfType(element, classOf[GherkinStepsHolder], false))
    RunResults.getInstance(file.getProject).failures(file.getVirtualFile.getUrl).filter { entry =>
      step.map(s => entry.result.line == line(s)).orElse(row.map(r => selectorLine(entry.result.selector).contains(line(r))))
        .orElse(scenario.map { s =>
          selectorLine(entry.result.selector).exists(n => n >= line(s) && n <= line(s) + s.getText.count(_ == '\n'))
        }).getOrElse(true)
    }.groupBy(d => (d.result.run, d.result.selector)).values.toSeq.flatMap { same =>
      val steps = same.filter(d => !selectorLine(d.result.selector).contains(d.result.line))
      if (steps.nonEmpty) steps else same
    }.sortBy(d => (-d.timestamp, d.result.run, d.result.selector, d.result.line))
  }

  def collect(feature: GherkinFile, result: RunResults.Decoration): Either[String, Snapshot] = {
    val project = feature.getProject
    val store = RunResults.getInstance(project)
    val stamp = PsiModificationTracker.getInstance(project).getModificationCount
    if (!store.contains(result)) Left("This run result is no longer available. Run the scenario again.")
    else if (store.stale(result)) Left("Source changed after this run. Run the scenario again before explaining it.")
    else if (!failureStatuses(result.result.status)) Left("Select a failed Cucumber result to explain.")
    else {
      if (!feature.isValid || RunResults.uri(feature.getVirtualFile.getUrl) != RunResults.uri(result.result.uri))
        Left("The feature file for this result is unavailable. Open its project and run the scenario again.")
      else {
        val locations = ScenarioSelection.locations(project, GlobalSearchScope.fileScope(feature), "").toOption.toSeq.flatten
        locations.find(l => selectorLine(result.result.selector).contains(l.line))
          .toRight("The recorded scenario or Examples row is unavailable. Run the scenario again.").map { location =>
            val step = PsiTreeUtil.findChildrenOfType(feature, classOf[GherkinStep]).asScala.find(s => line(s) == result.result.line)
            def source(label: String, element: PsiElement): Source = {
              val start = element.getTextRange.getStartOffset
              val number = element.getContainingFile.getText.take(start).count(_ == '\n') + 1
              Source(label, element.getContainingFile.getVirtualFile, start, number, number + element.getText.count(_ == '\n'), element.getText)
            }
            val scenario = source("Scenario", location.scenario)
            val backgrounds = Iterator.iterate(location.scenario.getParent)(_.getParent).takeWhile(_ != null)
              .filter(e => e.isInstanceOf[GherkinFeature] || e.isInstanceOf[GherkinRule]).toSeq.reverse
              .flatMap(_.getChildren.collect { case s: GherkinScenario if s.isBackground => source("Background", s) })
            val definitions = step.toSeq.flatMap(matchingSteps).flatMap(d => Option(d.getElement))
              .map(target => source("Static Scala match", target.getParent)).distinct.sortBy(s => (s.file.getPath, s.offset))
            val matchStatus = if (step.isEmpty) "The failure was not recorded on a Gherkin step (for example, a hook failure). No step binding is claimed."
              else if (definitions.isEmpty) "No static Scala definition resolves for the failing step."
              else if (definitions.size > 1) s"Ambiguous: ${definitions.size} static Scala matches. None is selected as the executed binding."
              else "One static Scala match; this is source resolution, not proof of the runtime binding."
            val frames = "([A-Za-z0-9_$]+\\.scala):(\\d+)".r.findAllMatchIn(result.result.error).toSeq.flatMap { frame =>
              ProgressManager.checkCanceled()
              FilenameIndex.getVirtualFilesByName(frame.group(1), GlobalSearchScope.projectScope(project)).asScala.toSeq
                .flatMap(v => Option(PsiManager.getInstance(project).findFile(v))).flatMap { psi =>
                  val number = frame.group(2).toIntOption.getOrElse(0)
                  val lines = psi.getText.split("\n", -1)
                  if (number <= 0 || number > lines.length) None
                  else Some(Source("Error location (local candidate)", psi.getVirtualFile,
                    lines.take(number - 1).map(_.length + 1).sum, number, number, lines(number - 1)))
                }
            }.distinct.sortBy(s => (s.file.getPath, s.line))
            val failing = step.map(source("Failing step", _)).getOrElse(source("Selected scenario / example", location.target))
            val facts = s"Recorded ${result.result.status} · run ${result.result.run}\n" +
              s"${java.time.Instant.ofEpochMilli(result.timestamp)} · ${result.result.selector}\n" + matchStatus
            def section(value: Source): String = s"${value.label}: ${value.file.getPath}:${value.line}-${value.endLine}\n${value.text}"
            val error = Option(result.result.error).filter(_.nonEmpty).getOrElse("No error details were supplied by the runtime.")
            val reviewedError = if (error.length > 4000) error.take(4000) + "\n[Remaining error text omitted.]" else error
            val text = facts + "\n\nRecorded error:\n" + reviewedError + "\n\n" + (backgrounds :+ scenario).map(section).mkString("\n\n") +
              (if (location.target != location.scenario) "\n\n" + section(source("Selected Examples row", location.target)) else "") +
              "\n\n" + section(failing) + "\n\n" + matchStatus + "\n\n" + definitions.map(section).mkString("\n\n") +
              (if (frames.nonEmpty) "\n\nLocal error-location candidates:\n" + frames.map(section).mkString("\n\n") else "")
            val reviewed = if (text.length > maxContextLength) text.take(maxContextLength - 100) +
              "\n[Remaining source context omitted. Review the excerpt before sending.]" else text
            Snapshot(result, stamp, location.scenario.getScenarioName, facts, reviewed, (Seq(failing, scenario) ++ definitions ++ frames).distinct)
          }
      }
    }
  }
}
