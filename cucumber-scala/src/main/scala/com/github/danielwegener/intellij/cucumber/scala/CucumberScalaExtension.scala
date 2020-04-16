package com.github.danielwegener.intellij.cucumber.scala

import java.util.{Collection => JavaCollection}

import com.github.danielwegener.intellij.cucumber.scala.ScalaUtils.glueCodeClasses
import com.github.danielwegener.intellij.cucumber.scala.steps.ScalaStepDefinition
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
import scala.util.Try

class CucumberScalaExtension extends AbstractCucumberExtension {

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
  override def getStepDefinitionCreator: StepDefinitionCreator = {
    throw new UnsupportedOperationException("You cannot automatically create Steps yet.")
  }

  override def loadStepsFor(featureFile: PsiFile, module: Module): java.util.List[AbstractStepDefinition] = {
    val project: Project = featureFile.getProject

    val stepDefinitions = for {
      glueCodeClass <- glueCodeClasses(module, project)
      scConstructorBody <- glueCodeClass.extendsBlock.templateBody.toSeq
      outerMethodCall <- scConstructorBody.getChildren.collect { case mc: ScMethodCall => mc }

      if ScalaUtils.isStepDefinition(outerMethodCall)
    } yield ScalaStepDefinition(outerMethodCall)

    JavaConverters.seqAsJavaList(stepDefinitions)
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): JavaCollection[_ <: PsiFile] = {
    val project: Project = featureFile.getProject
    val maybeModule = Option(ModuleUtilCore.findModuleForPsiElement(featureFile))

    val stepFiles = for {
      module <- maybeModule.toSeq
      glueCodeClass <- glueCodeClasses(module, project)
      containingFile <- Try(glueCodeClass.getContainingFile).toOption
    } yield containingFile

    JavaConverters.seqAsJavaList(stepFiles)
  }
}
