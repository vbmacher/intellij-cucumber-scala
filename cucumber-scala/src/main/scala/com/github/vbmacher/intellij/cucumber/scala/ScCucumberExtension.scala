package com.github.vbmacher.intellij.cucumber.scala

import java.util
import java.util.{Collection => JavaCollection}

import com.github.vbmacher.intellij.cucumber.scala.steps.{ScStepDefinition, ScStepDefinitionCreator}
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{GlobalSearchScope, ProjectScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition}
import org.jetbrains.plugins.cucumber.{BDDFrameworkType, StepDefinitionCreator}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.jdk.CollectionConverters._
import scala.util.Try

class ScCucumberExtension extends AbstractCucumberExtension {

  override def isStepLikeFile(@NotNull child: PsiElement, @NotNull parent: PsiElement): Boolean = {
    child.isInstanceOf[ScalaFile]
  }

  override def isWritableStepLikeFile(@NotNull child: PsiElement, @NotNull parent: PsiElement): Boolean = {
    child match {
      case scalaFile: ScalaFile =>
        Option(scalaFile.getContainingFile).map(_.getVirtualFile).exists(_.isWritable)
      case _ => false
    }
  }

  @NotNull
  override val getStepFileType: BDDFrameworkType = new BDDFrameworkType(ScalaFileType.INSTANCE)

  @NotNull
  override def getStepDefinitionCreator: StepDefinitionCreator = ScStepDefinitionCreator()

  override def loadStepsFor(featureFile: PsiFile, module: Module): java.util.List[AbstractStepDefinition] = {
    val fileBasedIndex = FileBasedIndex.getInstance()
    val project = module.getProject

    val searchScope = module
      .getModuleWithDependenciesAndLibrariesScope(true)
      .uniteWith(ProjectScope.getLibrariesScope(project))
    val scalaFiles = GlobalSearchScope.getScopeRestrictedByFileTypes(searchScope, ScalaFileType.INSTANCE)

    val result = collection.mutable.Buffer.empty[AbstractStepDefinition]

    fileBasedIndex.processValues(ScCucumberStepIndex.INDEX_ID, java.lang.Boolean.TRUE, null, {
      (file: VirtualFile, value: util.List[Integer]) => {

        ProgressManager.checkCanceled()
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile != null) {
          for (offset <- value.asScala) {
            val element = psiFile.findElementAt(offset + 1)

            val stepElement = for {
              inner <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall]))
              outer <- Option(inner.getParent)
              if outer.isInstanceOf[ScMethodCall]
            } yield outer.asInstanceOf[ScMethodCall]

            stepElement.foreach(result += ScStepDefinition(_))
          }
        }
        java.lang.Boolean.TRUE.booleanValue()
      }
    }, scalaFiles)

    result.asJava
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): JavaCollection[_ <: PsiFile] = {
    val stepFiles = for {
      module <- Option(ModuleUtilCore.findModuleForPsiElement(featureFile)).toSeq
      step <- loadStepsFor(featureFile, module).asScala
      psiElement <- Option(step.getElement).toSeq
      psiFile <- Try(psiElement.getContainingFile).toOption.toSeq
      psiDirectory <- Option(psiFile.getParent).toSeq

      if isWritableStepLikeFile(psiFile, psiDirectory)
    } yield psiFile

    stepFiles.asJava
  }
}
