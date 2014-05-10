package com.github.danielwegener.intellij.cucumber.scala

import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi._
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.util.{Collection => JavaCollection, Set => JavaSet, Collections}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition, NotIndexedCucumberExtension}
import com.intellij.openapi.module.{ModuleUtilCore, Module}
import scala.collection.convert.Wrappers.MutableSetWrapper
import scala.collection.JavaConversions._
import scala.collection.JavaConversions
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScBlock, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScInterpolatedStringLiteral}
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import com.github.danielwegener.intellij.cucumber.scala.steps.ScalaStepDefinition
import scala.util.Try

object CucumberScalaExtension {
  val LOG = Logger.getInstance(classOf[CucumberScalaExtension])
}

class CucumberScalaExtension extends AbstractCucumberExtension {

  import CucumberScalaExtension._

  val CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT = "cucumber.api.scala.ScalaDsl"

  override def isStepLikeFile(@NotNull child: PsiElement, @NotNull parent: PsiElement): Boolean = {
    child.isInstanceOf[ScalaFile]
  }

  override def isWritableStepLikeFile(@NotNull child: PsiElement, @NotNull parent: PsiElement): Boolean = {

    child match {
      case scalaFile: ScalaFile if Option(scalaFile.getContainingFile).map(_.getVirtualFile).exists(_.isWritable) => true
      case _ => false
    }
  }

  @NotNull
  override def getStepFileType: FileType = ScalaFileType.SCALA_FILE_TYPE

  @NotNull
  override def getStepDefinitionCreator: StepDefinitionCreator = throw new UnsupportedOperationException("You cannot automatically create Steps yet.")

  @NotNull override def getGlues(@NotNull file: GherkinFile, jGluesFromOtherFiles: JavaSet[String]): JavaCollection[String] = {
    // never called? wtf
    LOG.debug("GET GLUES CALLED")
    Collections.emptyList()
  }


  override def loadStepsFor(featureFile: PsiFile, module: Module): java.util.List[AbstractStepDefinition] = {

    val dependenciesScope: GlobalSearchScope = module.getModuleWithDependenciesAndLibrariesScope(true)
    val project: Project = featureFile.getProject

    val stepDefs = for {
      cucumberDslClass <- JavaPsiFacade.getInstance(project).findClasses(CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT, dependenciesScope).toSeq
      glueCodeClass@(some:ScClass) <- psi.stubs.util.ScalaStubsUtil.getClassInheritors(cucumberDslClass, dependenciesScope)
      scConstructorBody <- glueCodeClass.extendsBlock.templateBody.toSeq
      outerMethodCall@(some:ScMethodCall) <- scConstructorBody.children

      stepDefinition = new ScalaStepDefinition(outerMethodCall)
    } yield stepDefinition

    JavaConversions.seqAsJavaList(stepDefs)

  }


  override def getStepDefinitionContainers(featureFile: GherkinFile): JavaCollection[_ <: PsiFile] = {
    val project: Project = featureFile.getProject
    val maybeModule = Option(ModuleUtilCore.findModuleForPsiElement(featureFile))

    val stepDefs = for {
      module <- maybeModule.toSeq
      searchScope = module.getModuleContentScope
      globalSearchScope = module.getModuleWithDependenciesAndLibrariesScope(true)
      cucumberDslClass <- JavaPsiFacade.getInstance(project).findClasses(CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT, globalSearchScope).toSeq
      glueCodeClass@(some: ScClass) <- psi.stubs.util.ScalaStubsUtil.getClassInheritors(cucumberDslClass, searchScope)
      containingFile <- Try( glueCodeClass.getContainingFile ).toOption
    } yield containingFile

    JavaConversions.seqAsJavaList(stepDefs)
  }

}
