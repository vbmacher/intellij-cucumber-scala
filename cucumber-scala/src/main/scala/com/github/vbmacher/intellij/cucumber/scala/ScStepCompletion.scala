package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiModifierListOwner}
import com.intellij.psi.util.PsiTreeUtil
import scala.jdk.CollectionConverters._
import org.jetbrains.plugins.cucumber.psi.{GherkinFile, GherkinStep}

class ScStepCompletion extends CompletionContributor {
  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    if (!parameters.getOriginalFile.isInstanceOf[GherkinFile]) return
    val project = parameters.getOriginalFile.getProject
    val module = ModuleUtilCore.findModuleForPsiElement(parameters.getOriginalFile)
    if (module == null || com.intellij.openapi.project.DumbService.isDumb(project)) return
    val originalText = parameters.getOriginalFile.getText
    val deterministic = new ScCucumberExtension().loadStepsFor(module).asScala.toSeq.filter(_.getElement != null)
      .sortBy(d => (d.getElement.getContainingFile.getVirtualFile.getPath, d.getElement.getTextOffset)).zipWithIndex.map { case (d, index) => d.getElement -> index }.toMap
    val settings = project.getService(classOf[ScalaCucumberSettings])
    val entries = SuiteCatalog.entries(project, module.getModuleWithDependenciesAndLibrariesScope(true))
    val step = Option(PsiTreeUtil.getParentOfType(parameters.getPosition, classOf[GherkinStep], false))
    val tags = step.toSeq.flatMap(StepUsage.tags).toSet
    result.runRemainingContributors(parameters, (completion: CompletionResult) => {
      val lookup = completion.getLookupElement
      val element = lookup.getPsiElement
      val call = Option(element).flatMap(e => asCall(e.getParent).orElse(asCall(e))).filter(_.definition.nonEmpty)
      call match {
        case Some(c) =>
          val usages = SuiteCatalog.usages(entries, c.getFirstChild)
          val sourceModule = ModuleUtilCore.findModuleForPsiElement(c)
          val source = c.getContainingFile.getName + Option(sourceModule).map(m => " · " + m.getName).getOrElse("")
          val usedTags = usages.flatMap(StepUsage.tags).distinct.sorted
          val sharedTags = usedTags.toSet.intersect(tags).size
          val score = (if (settings.rankByReuse) usages.size.toDouble else 0.0) -
            deterministic.getOrElse(c.getFirstChild, 0).toDouble / (deterministic.size + 1) / 10000
          val decorated = new LookupElementDecorator[LookupElement](lookup) {
            override def renderElement(presentation: LookupElementPresentation): Unit = {
              super.renderElement(presentation)
              presentation.setTailText(s" · ${usages.size} matching scenarios", true)
            }
          }
          result.addElement(PrioritizedLookupElement.withPriority(decorated, score))
        case _ => result.passResult(completion)
      }
    })
  }
}
