package com.github.vbmacher.intellij.cucumber.scala.suite

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
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

  def scenarios(project: Project, scope: GlobalSearchScope): Seq[GherkinStepsHolder] =
    FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).asScala.toSeq.sortBy(_.getPath).flatMap { file =>
      ProgressManager.checkCanceled()
      Option(PsiManager.getInstance(project).findFile(file)).toSeq.flatMap { psi =>
        PsiTreeUtil.findChildrenOfType(psi, classOf[GherkinStepsHolder]).asScala.toSeq
          .filterNot { case s: GherkinScenario => s.isBackground; case _ => false }
      }
    }

  def entries(project: Project, scope: GlobalSearchScope): Seq[Entry] = {
    val manager = PsiManager.getInstance(project)
    FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).asScala.toSeq.sortBy(_.getPath).flatMap { file =>
      ProgressManager.checkCanceled()
      Option(manager.findFile(file)).toSeq.flatMap { psi =>
        CachedValuesManager.getCachedValue(psi, () => CachedValueProvider.Result.create(
          PsiTreeUtil.findChildrenOfType(psi, classOf[GherkinStep]).asScala.toSeq.map { step =>
            ProgressManager.checkCanceled()
            val scenarios = step.getStepHolder match {
              case s: GherkinScenario if s.isBackground =>
                PsiTreeUtil.findChildrenOfType(s.getParent, classOf[GherkinStepsHolder]).asScala.toSeq
                  .filterNot { case b: GherkinScenario => b.isBackground; case _ => false }
              case holder => Seq(holder)
            }
            Entry(step, scenarios, matchingSteps(step))
          }, psi, PsiModificationTracker.getInstance(project).forLanguages(_.isKindOf(ScalaLanguage.INSTANCE)),
          ProjectRootManager.getInstance(project)))
      }
    }
  }

  def usages(entries: Seq[Entry], definition: PsiElement): Seq[GherkinStepsHolder] =
    entries.filter(_.definitions.exists(_.getElement == definition)).flatMap(_.scenarios).distinct
}
