package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScCucumber, ScCucumberExtension}
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

import scala.jdk.CollectionConverters._

object SuiteAnalysis {
  final case class ModuleGroup(id: String, name: String)
  def moduleGroup(module: Module): ModuleGroup = {
    val properties = ExternalSystemModulePropertyManager.getInstance(module)
    if (properties.getExternalSystemId != "SBT") ModuleGroup(module.getName, module.getName)
    else {
      val rootPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
      val importedId = ExternalSystemApiUtil.getExternalProjectId(module)
      val structure = Option(rootPath).flatMap(path => Option(ProjectDataManager.getInstance()
        .getExternalProjectData(module.getProject, new ProjectSystemId("SBT"), path)))
        .flatMap(info => Option(info.getExternalProjectStructure))
      var owner = Option.empty[ModuleData]
      structure.foreach(_.visit(node => node.getData match {
        case data: ModuleData if data.getId == importedId =>
          owner = if (properties.getExternalModuleType == "sbtSourceSet") Option(node.getParent)
            .flatMap(parent => Option(parent.getParent(classOf[ModuleData]))).map(_.getData)
          else Some(data)
        case _ =>
      }))
      owner.map(data => ModuleGroup(rootPath + ":" + data.getId, data.getExternalName))
        .getOrElse(ModuleGroup(rootPath + ":" + ExternalSystemApiUtil.getExternalProjectPath(module), "Unresolved sbt project"))
    }
  }

  final case class Source(label: String, location: String, target: PsiElement) {
    override def toString: String = s"$label · $location"
  }
  // Capture display/filter metadata under the scan's read action. Swing listeners never traverse PSI.
  final case class Finding(view: SuiteView, source: Source, keyword: Option[String], moduleGroup: ModuleGroup, runtimeModuleName: String,
                           tagSets: Seq[Seq[String]], related: Seq[Source]) {
    override def toString: String = source.label
  }
  final case class Report(features: Int, scenarios: Int, examples: Int, stepUsages: Int, definitions: Int, findings: Seq[Finding]) {
    def summary: String = s"$features features · $scenarios scenarios (outlines count once) · $examples example rows · $stepUsages step usages · $definitions definitions"
  }

  def scan(project: Project, modules: Seq[Module], scope: GlobalSearchScope): Report = {
    val entries = SuiteCatalog.entries(project, scope)
    val definitions = modules.flatMap(m => new ScCucumberExtension().loadStepsFor(m).asScala).distinctBy(_.getElement)
    val files = com.intellij.psi.search.FileTypeIndex.getFiles(org.jetbrains.plugins.cucumber.psi.GherkinFileType.INSTANCE, scope).asScala.toSeq
      .flatMap(file => Option(com.intellij.psi.PsiManager.getInstance(project).findFile(file)))
    val scenarios = Scenarios.declarations(project, scope)
    val findings = Seq.newBuilder[Finding]
    val moduleGroups = modules.map(m => m -> moduleGroup(m)).toMap
    def source(label: String, target: PsiElement): Source = {
      val file = target.getContainingFile
      val path = Option(project.getBasePath).fold(file.getVirtualFile.getPath)(base => file.getVirtualFile.getPath.stripPrefix(base + "/"))
      val line = Option(com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file))
        .map(_.getLineNumber(Scenarios.sourceOffset(target)) + 1)
        .getOrElse(file.getText.take(Scenarios.sourceOffset(target)).count(_ == '\n') + 1)
      Source(label, s"$path:$line", target)
    }
    def finding(view: SuiteView, label: String, target: PsiElement, related: Seq[Source] = Seq.empty): Finding = {
      val keyword = ScCucumber.asCall(target.getParent).flatMap(_.keyword).flatMap(k =>
        Option(JsonGherkinKeywordProvider.getKeywordProvider(true).getBaseKeyword("en", k)))
      val tags = target match {
        case scenario: GherkinStepsHolder => Scenarios.tagSets(scenario)
        case _ => Seq(Scenarios.inheritedTags(target))
      }
      val module = ScCucumber.moduleOf(target)
      val group = module.map(m => moduleGroups.getOrElse(m, moduleGroup(m))).getOrElse(ModuleGroup("", "No module"))
      Finding(view, source(label, target), keyword, group, module.map(_.getName).getOrElse(""), tags, related)
    }
    entries.foreach { entry =>
      ProgressManager.checkCanceled()
      val context = entry.scenarios.map(_.getScenarioName).mkString(", ")
      val label = entry.step.getKeyword.getText.trim + " " + entry.step.getName + s" · $context"
      if (entry.definitions.isEmpty) findings += finding(SuiteView.Undefined, label, entry.step)
      if (entry.definitions.size > 1) {
        findings += finding(SuiteView.Ambiguous, s"$label · ${entry.definitions.size} matching definitions", entry.step,
          entry.definitions.map(d => source(d.getExpression, d.getElement)))
      }
    }
    definitions.foreach { definition =>
      ProgressManager.checkCanceled()
      val target = definition.getElement
      if (target != null) {
        val usages = SuiteCatalog.usages(entries, target)
        val label = definition.getExpression + s" · ${usages.size} matching scenarios"
        findings += finding(SuiteView.Definitions, label, target, usages.map(s => source(s.getScenarioName, s)))
        val count = usages.size
        if (count == 0 && scope.contains(target.getContainingFile.getVirtualFile))
          findings += finding(SuiteView.Unused, definition.getExpression, target)
      }
    }
    definitions.groupBy(_.getExpression).filter(_._2.size > 1).toSeq.sortBy(_._1).foreach { case (pattern, same) =>
      same.foreach(d => findings += finding(SuiteView.Repeated, pattern, d.getElement,
        same.filterNot(_ == d).map(other => source(other.getExpression, other.getElement))))
    }
    val entriesByScenario = entries.flatMap(e => e.scenarios.map(_ -> e)).groupMap(_._1)(_._2)
    scenarios.foreach { scenario =>
      val tags = Scenarios.tags(scenario)
      findings += finding(SuiteView.Scenarios, scenario.getScenarioName + (if (tags.isEmpty) "" else " · " + tags.mkString(" ")), scenario,
        entriesByScenario.getOrElse(scenario, Seq.empty).map(e => source(e.step.getKeyword.getText.trim + " " + e.step.getName +
          (if (e.definitions.isEmpty) " · undefined" else if (e.definitions.size > 1) s" · ${e.definitions.size} matching definitions" else ""), e.step)))
    }
    // Compare parsed step wording and arguments in order. Titles, tags, comments and spacing outside steps are ignored.
    scenarios.groupBy(s => s.getSteps.toSeq.map(step => (step.getName, Option(step.getTable).map(_.getText.trim), Option(step.getPystring).map(_.getText.trim))))
      .filter { case (body, same) => body.nonEmpty && same.size > 1 }.values.foreach { same =>
        same.foreach(s => findings += finding(SuiteView.Duplicates, s.getScenarioName, s,
          same.filterNot(_ == s).map(other => source(other.getScenarioName, other))))
      }
    val exampleRows = scenarios.map(s => Scenarios.locations(s).count(_.target != s)).sum
    Report(files.size, scenarios.size, exampleRows, entries.size, definitions.size, findings.result())
  }
}
