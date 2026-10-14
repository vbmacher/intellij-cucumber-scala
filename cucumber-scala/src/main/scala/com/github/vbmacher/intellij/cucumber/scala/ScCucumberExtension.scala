package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{GlobalSearchScope, ProjectScope}
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker}
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition}
import org.jetbrains.plugins.cucumber.{BDDFrameworkType, StepDefinitionCreator}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScCucumberExtension extends AbstractCucumberExtension {

  override def isStepLikeFile(child: PsiElement): Boolean = child.isInstanceOf[ScalaFile]

  override def isWritableStepLikeFile(child: PsiElement): Boolean = child match {
    case file: ScalaFile => Option(file.getVirtualFile).exists(_.isWritable)
    case _ => false
  }

  override val getStepFileType: BDDFrameworkType = new BDDFrameworkType(ScalaFileType.INSTANCE)

  override def getStepDefinitionCreator: StepDefinitionCreator = new ScStepDefinitionCreator

  // Gherkin calls this for every step reference resolve, so cache it until Scala PSI or roots change
  override def loadStepsFor(module: Module): util.List[AbstractStepDefinition] = {
    val project = module.getProject
    CachedValuesManager.getManager(project).getCachedValue(module, () => CachedValueProvider.Result.create(
      util.Collections.unmodifiableList(findSteps(module)),
      PsiModificationTracker.getInstance(project).forLanguages(_.isKindOf(ScalaLanguage.INSTANCE)),
      ProjectRootManager.getInstance(project)
    ))
  }

  private def findSteps(module: Module): util.List[AbstractStepDefinition] = {
    val project = module.getProject
    val psiManager = PsiManager.getInstance(project)
    val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
      module.getModuleWithDependenciesAndLibrariesScope(true).uniteWith(ProjectScope.getLibrariesScope(project)),
      ScalaFileType.INSTANCE
    )

    val result = new util.ArrayList[AbstractStepDefinition]()
    FileBasedIndex.getInstance().processValues(ScCucumberStepIndex.Id, java.lang.Boolean.TRUE, null, (file: VirtualFile, offsets: Seq[Int]) => {
      ProgressManager.checkCanceled()
      for {
        psiFile <- Option(psiManager.findFile(file)).toSeq
        offset <- offsets
        call <- callOfIndexed(psiFile.findElementAt(offset + 1))
      } result.add(new ScStepDefinition(call))
      true
    }, scope)
    result
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): util.Collection[_ <: PsiFile] =
    Option(ModuleUtilCore.findModuleForPsiElement(featureFile)).toSeq
      .flatMap(loadStepsFor(_).asScala)
      .flatMap(step => Option(step.getElement))
      .flatMap(element => Try(element.getContainingFile).toOption)
      .filter(isWritableStepLikeFile)
      .asJava
}
