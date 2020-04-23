package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util.Properties

import com.github.danielwegener.intellij.cucumber.scala.{inWriteAction, isUnitTestMode}
import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import cucumber.runtime.snippets.{CamelCaseConcatenator, FunctionNameGenerator, SnippetGenerator}
import gherkin.formatter.model.{Comment, Step}
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ScStepDefinitionCreator extends AbstractStepDefinitionCreator {

  import ScStepDefinitionCreator.LOG

  private final val containerTemplate = "Scala Step Definitions.scala"

  override def createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile = {
    val templateManager = FileTemplateManager.getInstance(directory.getProject)
    val template = templateManager.getInternalTemplate(containerTemplate)

    val defaultProperties = templateManager.getDefaultProperties
    val properties = new Properties(defaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name)

    val fileName = name + ScalaFileType.INSTANCE.getExtensionWithDot
    try {
      val element = FileTemplateUtil.createFromTemplate(template, fileName, properties, directory)
      element.getContainingFile
    } catch {
      case e: IncorrectOperationException => throw e
      case e: Exception =>
        LOG.error(e)
        null
    }
  }

  override def validateNewStepDefinitionFileName(project: Project, name: String): Boolean = {
    name.nonEmpty && ScalaNamesValidator.isIdentifier(name)
  }

  override def getDefaultStepFileName(gherkinStep: GherkinStep): String = "StepDefinitions"

  override def getDefaultStepDefinitionFolder(step: GherkinStep): PsiDirectory = {
    val featureFile = step.getContainingFile

    val javaSourceRoots = ProjectRootManager.getInstance(featureFile.getProject)
      .getModuleSourceRoots(Set(JavaSourceRootType.TEST_SOURCE).asJava)
      .asScala

    javaSourceRoots.headOption.map(featureFile.getManager.findDirectory).orNull
  }

  override def createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean = {
    Try {
      if (step == null || file == null || !file.isInstanceOf[ScalaFile]) return false

      val project = file.getProject
      val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
      assert(editor != null)

      closeActiveTemplateBuilders(file)

      val element = PsiTreeUtil.getChildOfType(file, classOf[PsiClass]) match {
        case clazz: ScTypeDefinition => clazz.extendsBlock.templateBody.map(templateBody => {
          templateBody.addBefore(createMethodCall(step, templateBody), templateBody.getLastChild).asInstanceOf[ScMethodCall]
        })

        case _ => null
      }

      element.map(e => CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(e))

      inWriteAction {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }

      if (!isUnitTestMode && withTemplate) {
        // TODO
      }
      true
    } match {
      case Success(value) => value
      case Failure(exception) =>
        LOG.error(exception)
        false
    }
  }


  private def createMethodCall(step: GherkinStep, context: PsiElement): ScMethodCall = {
    val cucumberStep = new Step(Seq.empty[Comment].asJava, step.getKeyword.getText, step.getName, 0, null, null)
    val generator = new SnippetGenerator(new ScalaSnippet())

    val snippet = generator.getSnippet(cucumberStep, new FunctionNameGenerator(new CamelCaseConcatenator()))
    ScalaPsiElementFactory.createExpressionFromText(snippet, context).asInstanceOf[ScMethodCall]
  }
}

object ScStepDefinitionCreator {
  private final val LOG = Logger.getInstance(classOf[ScStepDefinitionCreator])

  def apply(): ScStepDefinitionCreator = new ScStepDefinitionCreator()
}
