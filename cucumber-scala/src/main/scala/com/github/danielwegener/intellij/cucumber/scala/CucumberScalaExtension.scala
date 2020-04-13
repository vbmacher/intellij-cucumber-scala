package com.github.danielwegener.intellij.cucumber.scala

import java.util.{Collection => JavaCollection}

import com.github.danielwegener.intellij.cucumber.scala.CucumberScalaExtension.JavaList
import com.github.danielwegener.intellij.cucumber.scala.steps.ScalaStepDefinition
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition}
import org.jetbrains.plugins.cucumber.{BDDFrameworkType, StepDefinitionCreator}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors

import scala.annotation.tailrec
import scala.collection.JavaConverters
import scala.util.Try

object CucumberScalaExtension {
  val LOG: Logger = Logger.getInstance(classOf[CucumberScalaExtension])

  type JavaList[T] = java.util.List[T]
}

class CucumberScalaExtension extends AbstractCucumberExtension {
  private final val CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT = "cucumber.api.scala.ScalaDsl"

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

  override def loadStepsFor(featureFile: PsiFile, module: Module): JavaList[AbstractStepDefinition] = {
    val project: Project = featureFile.getProject

    val stepDefinitions = for {
      glueCodeClass <- findGlueCodeClass(module, project)
      scConstructorBody <- glueCodeClass.extendsBlock.templateBody.toSeq
      outerMethodCall <- scConstructorBody.getChildren.collect { case mc: ScMethodCall => mc }
    } yield ScalaStepDefinition(outerMethodCall)

    JavaConverters.seqAsJavaList(stepDefinitions)
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): JavaCollection[_ <: PsiFile] = {
    val project: Project = featureFile.getProject
    val maybeModule = Option(ModuleUtilCore.findModuleForPsiElement(featureFile))

    val stepFiles = for {
      module <- maybeModule.toSeq
      glueCodeClass <- findGlueCodeClass(module, project)
      containingFile <- Try(glueCodeClass.getContainingFile).toOption
    } yield containingFile

    JavaConverters.seqAsJavaList(stepFiles)
  }


  private def findGlueCodeClass(module: Module, project: Project) = {
    val dependencies = module.getModuleWithDependenciesAndLibrariesScope(true)
    val psiFacade = JavaPsiFacade.getInstance(project)

    for {
      cucumberDslClass <- psiFacade.findClasses(CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT, dependencies).toSeq
      scalaDslInheritingClass@(some: ScClass) <- psi.stubs.util.ScalaInheritors.withStableScalaInheritors(cucumberDslClass)
      glueCodeClass <- classAndItsInheritors(scalaDslInheritingClass)
    } yield glueCodeClass
  }

  private def classAndItsInheritors(parent: ScTypeDefinition): Iterable[ScTypeDefinition] = {

    @tailrec
    def rec(queue: Seq[ScTypeDefinition], accumulator: Set[ScTypeDefinition]): Set[ScTypeDefinition] = {
      queue match {
        case Seq() => accumulator
        case queueHead +: queueTail =>

          val newChildren = ScalaInheritors.findInheritorObjects(queueHead).collect {
            case sc: ScClass => sc
            case sct: ScTrait => sct
          }

          val childrenToExplore = newChildren
            .map(_.asInstanceOf[ScTypeDefinition])
            .diff(accumulator)
            .toSeq

          rec(queueTail ++ childrenToExplore, accumulator + queueHead)
      }
    }

    rec(Seq(parent), Set.empty)
  }
}
