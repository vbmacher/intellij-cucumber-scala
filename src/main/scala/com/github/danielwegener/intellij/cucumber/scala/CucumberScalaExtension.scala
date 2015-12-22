package com.github.danielwegener.intellij.cucumber.scala

import java.util.{ Collections, Collection => JavaCollection, Set => JavaSet }

import com.github.danielwegener.intellij.cucumber.scala.steps.ScalaStepDefinition
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{ Module, ModuleUtilCore }
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.cucumber.{ BDDFrameworkType, StepDefinitionCreator }
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{ AbstractCucumberExtension, AbstractStepDefinition }
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ ScTrait, ScTypeDefinition, ScClass }

import scala.annotation.tailrec
import scala.collection.JavaConversions
import scala.util.Try

object CucumberScalaExtension {
  val LOG = Logger.getInstance(classOf[CucumberScalaExtension])
}

class CucumberScalaExtension extends AbstractCucumberExtension {

  import com.github.danielwegener.intellij.cucumber.scala.CucumberScalaExtension._

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
  override val getStepFileType: BDDFrameworkType = new BDDFrameworkType(ScalaFileType.SCALA_FILE_TYPE)

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
      cucumberDslClass <- JavaPsiFacade.getInstance(project).findClasses(CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT, dependenciesScope)
      scalaDslInheritingClass <- psi.stubs.util.ScalaStubsUtil.getClassInheritors(cucumberDslClass, dependenciesScope).collect { case sc: ScClass => sc; case sct: ScTrait => sct }
      glueCodeClass <- classAndItsInheritors(scalaDslInheritingClass, dependenciesScope)
      scConstructorBody <- glueCodeClass.extendsBlock.templateBody.toSeq
      outerMethodCall <- scConstructorBody.children.collect { case mc: ScMethodCall => mc }
    } yield new ScalaStepDefinition(outerMethodCall)

    JavaConversions.seqAsJavaList(stepDefs)

  }

  def classAndItsInheritors(parentOfHierarchy: ScTypeDefinition, scope: GlobalSearchScope): Iterable[ScTypeDefinition] = {

    @tailrec
    def rec(queue: List[ScTypeDefinition], akku: Set[ScTypeDefinition]): Set[ScTypeDefinition] = {
      queue match {
        case Nil => akku
        case a =>
          val newChildren = psi.stubs.util.ScalaStubsUtil.getClassInheritors(a.head, scope)
            .collect { case sc: ScClass => sc; case sct: ScTrait => sct }
            .filterNot(akku.contains)
          rec(a.tail ::: newChildren.toList, akku + a.head)

      }

    }
    rec(List(parentOfHierarchy), Set.empty)
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): JavaCollection[_ <: PsiFile] = {
    val project: Project = featureFile.getProject
    val maybeModule = Option(ModuleUtilCore.findModuleForPsiElement(featureFile))

    val stepDefs = for {
      module <- maybeModule.toSeq
      searchScope = module.getModuleContentScope
      globalSearchScope = module.getModuleWithDependenciesAndLibrariesScope(true)
      cucumberDslClass <- JavaPsiFacade.getInstance(project).findClasses(CUCUMBER_RUNTIME_SCALA_STEP_DEF_TRAIT, globalSearchScope).toSeq
      scalaDslInheritingClass @ (some: ScClass) <- psi.stubs.util.ScalaStubsUtil.getClassInheritors(cucumberDslClass, searchScope)
      glueCodeClass <- classAndItsInheritors(scalaDslInheritingClass, searchScope)
      containingFile <- Try(glueCodeClass.getContainingFile).toOption
    } yield containingFile

    JavaConversions.seqAsJavaList(stepDefs)
  }

}
