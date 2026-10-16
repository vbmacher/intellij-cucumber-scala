package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.{GherkinScenarioOutline, GherkinStepsHolder}

import scala.jdk.CollectionConverters._

object SuiteAnalysis {
  final case class Finding(category: String, label: String, target: PsiElement)
  final case class Report(features: Int, scenarios: Int, examples: Int, stepUsages: Int, definitions: Int, findings: Seq[Finding]) {
    def summary: String = s"$features features · $scenarios scenarios (outlines count once) · $examples example rows · $stepUsages step usages · $definitions definitions"
  }

  def scan(project: Project, modules: Seq[Module], scope: GlobalSearchScope): Report = {
    val entries = SuiteCatalog.entries(project, scope)
    val definitions = modules.flatMap(m => new ScCucumberExtension().loadStepsFor(m).asScala).distinctBy(_.getElement)
    val files = com.intellij.psi.search.FileTypeIndex.getFiles(org.jetbrains.plugins.cucumber.psi.GherkinFileType.INSTANCE, scope).asScala.toSeq
      .flatMap(file => Option(com.intellij.psi.PsiManager.getInstance(project).findFile(file)))
    val scenarios = files.flatMap(file => com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, classOf[GherkinStepsHolder]).asScala)
      .filterNot { case s: org.jetbrains.plugins.cucumber.psi.GherkinScenario => s.isBackground; case _ => false }
    val findings = Seq.newBuilder[Finding]
    definitions.foreach { definition =>
      ProgressManager.checkCanceled()
      val target = definition.getElement
      if (target != null) {
        val count = SuiteCatalog.usages(entries, target).size
        val label = definition.getExpression + " · " + target.getContainingFile.getName + s" · $count matching scenarios"
        findings += Finding("Steps", label, target)
        if (count == 0 && scope.contains(target.getContainingFile.getVirtualFile))
          findings += Finding("Unused", label + " (external consumers unknown)", target)
      }
    }
    val exampleRows = scenarios.collect { case outline: GherkinScenarioOutline =>
      outline.getExamplesBlocks.asScala.flatMap(b => Option(b.getTable).toSeq.flatMap(_.getDataRows.asScala)).size
    }.sum
    Report(files.size, scenarios.size, exampleRows, entries.size, definitions.size, findings.result())
  }
}
