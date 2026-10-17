package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.creation.ScStepDefinitionCreator
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.{AbstractCucumberExtension, AbstractStepDefinition}
import org.jetbrains.plugins.cucumber.{BDDFrameworkType, StepDefinitionCreator}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScCucumberExtension extends AbstractCucumberExtension {

  override def isGherkin6Supported(module: Module): Boolean = true

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
    // Check before the cache so previously resolved steps cannot enable navigation during indexing.
    if (DumbService.isDumb(project)) util.Collections.emptyList()
    else CachedValuesManager.getManager(project).getCachedValue(module, () => CachedValueProvider.Result.create(
      util.Collections.unmodifiableList(findSteps(module)),
      PsiModificationTracker.getInstance(project).forLanguages(_.isKindOf(ScalaLanguage.INSTANCE)),
      ProjectRootManager.getInstance(project)
    ))
  }

  private def findSteps(module: Module): util.List[AbstractStepDefinition] = {
    val project = module.getProject
    val scope = stepSearchScope(module)

    val parameterTypes = indexedCalls(project, scope, kind = ScCucumberStepIndex.Parameters).flatMap(_.parameterType).groupBy(_._1).collect {
      case (name, registrations) if registrations.map(_._2).distinct.size == 1 => name -> registrations.head._2
    }
    indexedCalls(project, scope, kind = ScCucumberStepIndex.Steps)
      .filter(_.definition.nonEmpty)
      .map(call => new ScStepDefinition(call, parameterTypes): AbstractStepDefinition)
      .asJava
  }

  override def getStepDefinitionContainers(featureFile: GherkinFile): util.Collection[_ <: PsiFile] =
    moduleOf(featureFile).toSeq
      .flatMap(loadStepsFor(_).asScala)
      .flatMap(step => Option(step.getElement))
      .flatMap(element => Try(element.getContainingFile).toOption)
      .filter(isWritableStepLikeFile)
      .asJava
}
