package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker, PsiTreeUtil}
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi._

object StepUsage {
  // Cached on the definition, invalidated by feature and Scala edits and module/root changes.
  // Counts include ambiguous matches and count an outline once, irrespective of its row count.
  def scenarios(element: PsiElement): Seq[GherkinStepsHolder] =
    CachedValuesManager.getCachedValue(element, () => CachedValueProvider.Result.create(
      findScenarios(element, moduleOf(element)
        .map(_.getModuleWithDependenciesAndLibrariesScope(true)).getOrElse(GlobalSearchScope.projectScope(element.getProject))),
      PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(element.getProject)))

  def findScenarios(element: PsiElement, scope: GlobalSearchScope): Seq[GherkinStepsHolder] = {
    val result = Seq.newBuilder[GherkinStepsHolder]
    for (call <- asCall(element.getParent); pattern <- call.pattern) {
      CucumberUtil.findGherkinReferencesToElement(element, pattern, new Processor[com.intellij.psi.PsiReference] {
        override def process(reference: com.intellij.psi.PsiReference): Boolean = {
          ProgressManager.checkCanceled()
          Option(PsiTreeUtil.getParentOfType(reference.getElement, classOf[GherkinStep], false)).foreach { step =>
            result ++= Scenarios.forStep(step)
          }
          true
        }
      }, scope)
    }
    result.result().distinct.sortBy(s => (s.getContainingFile.getVirtualFile.getPath, s.getTextOffset))
  }
}
