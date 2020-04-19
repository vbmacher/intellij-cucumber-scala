package com.github.danielwegener.intellij.cucumber.scala

import java.util.{Collection => JavaCollection}

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil.getStepDefinitionClasses
import com.github.danielwegener.intellij.cucumber.scala.steps.{ScStepDefinition, ScStepDefinitionCreator}
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition}
import org.jetbrains.plugins.cucumber.{BDDFrameworkType, StepDefinitionCreator}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

import scala.collection.JavaConverters
import collection.JavaConverters._
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
    val project: Project = featureFile.getProject
    val searchScope = Option(featureFile)
      .map(_.getResolveScope)
      .getOrElse(module.getModuleWithDependenciesAndLibrariesScope(true))

    val stepDefinitions = for {
      glueCodeClass <- getStepDefinitionClasses(searchScope, project)
      scConstructorBody <- glueCodeClass.extendsBlock.templateBody.toSeq
      outerMethodCall <- scConstructorBody.getChildren.collect { case mc: ScMethodCall => mc }

      if ScCucumberUtil.isStepDefinition(outerMethodCall)
    } yield ScStepDefinition(outerMethodCall)

    JavaConverters.seqAsJavaList(stepDefinitions)
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

    JavaConverters.seqAsJavaList(stepFiles)
  }
}
