package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker, PsiTreeUtil}
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._

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
            step.getStepHolder match {
              case background: GherkinScenario if background.isBackground =>
                // A Rule background applies within that Rule; a Feature background applies to all its scenarios.
                Option(background.getParent).foreach { parent =>
                  result ++= PsiTreeUtil.findChildrenOfType(parent, classOf[GherkinStepsHolder]).asScala
                    .filterNot { case s: GherkinScenario => s.isBackground; case _ => false }
                }
              case holder => result += holder
            }
          }
          true
        }
      }, scope)
    }
    result.result().distinct.sortBy(s => (s.getContainingFile.getVirtualFile.getPath, s.getTextOffset))
  }

  def tags(element: PsiElement): Seq[String] = {
    def trivia(e: PsiElement): Boolean = e.isInstanceOf[com.intellij.psi.PsiWhiteSpace] || e.isInstanceOf[com.intellij.psi.PsiComment]
    def taggable(e: PsiElement): Boolean = e.isInstanceOf[GherkinFile] || e.isInstanceOf[GherkinFeature] ||
      e.isInstanceOf[GherkinRule] || e.isInstanceOf[GherkinStepsHolder] || e.isInstanceOf[GherkinExamplesBlock]
    Iterator.iterate(element)(_.getParent).takeWhile(_ != null).filter(taggable).flatMap { owner =>
      val leading = owner.getChildren.takeWhile(e => trivia(e) || e.isInstanceOf[GherkinTag])
        .collect { case tag: GherkinTag => tag.getText }
      // Examples tags are siblings before the block in the Gherkin PSI, not its children.
      val preceding = Iterator.iterate(owner.getPrevSibling)(e => if (e == null) null else e.getPrevSibling)
        .takeWhile(e => e != null && (trivia(e) || e.isInstanceOf[GherkinTag]))
        .collect { case tag: GherkinTag => tag.getText }.toSeq
      leading ++ preceding
    }.toSeq.distinct.sorted
  }
}
