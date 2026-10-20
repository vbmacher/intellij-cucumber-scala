package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker, PsiTreeUtil}
import org.jetbrains.plugins.cucumber.psi._
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.ScalaLanguage

import scala.jdk.CollectionConverters._

object SuiteCatalog {
  final case class Entry(step: GherkinStep, scenarios: Seq[GherkinStepsHolder], definitions: Seq[AbstractStepDefinition])

  def entries(project: Project, scope: GlobalSearchScope): Seq[Entry] = {
    val manager = PsiManager.getInstance(project)
    FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).asScala.toSeq.sortBy(_.getPath).flatMap { file =>
      ProgressManager.checkCanceled()
      Option(manager.findFile(file)).toSeq.flatMap { psi =>
        CachedValuesManager.getCachedValue(psi, () => CachedValueProvider.Result.create(
          PsiTreeUtil.findChildrenOfType(psi, classOf[GherkinStep]).asScala.toSeq.map { step =>
            ProgressManager.checkCanceled()
            Entry(step, Scenarios.forStep(step), matchingSteps(step))
          }, psi, PsiModificationTracker.getInstance(project).forLanguages(_.isKindOf(ScalaLanguage.INSTANCE)),
          ProjectRootManager.getInstance(project)))
      }
    }
  }

  def usages(entries: Seq[Entry], definition: PsiElement): Seq[GherkinStepsHolder] =
    entries.filter(_.definitions.exists(_.getElement == definition)).flatMap(_.scenarios).distinct
}
