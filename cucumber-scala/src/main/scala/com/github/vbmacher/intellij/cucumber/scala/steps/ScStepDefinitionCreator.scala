package com.github.vbmacher.intellij.cucumber.scala.steps

import java.util.{Locale, Properties}

import com.github.vbmacher.intellij.cucumber.scala.{inWriteAction, isUnitTestMode}
import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template._
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{FileIndexFacade, ModuleRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import io.cucumber.core.snippets.{SnippetGenerator, SnippetType}
import io.cucumber.cucumberexpressions.ParameterTypeRegistry
import io.cucumber.scala.ScalaSnippet
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ScStepDefinitionCreator extends AbstractStepDefinitionCreator {
  private final val LOG = Logger.getInstance(classOf[ScStepDefinitionCreator])

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

  override def getDefaultStepDefinitionFolderPath(step: GherkinStep): String = {
    val featureFile = step.getContainingFile

    val fileIndexFacade = FileIndexFacade.getInstance(featureFile.getProject)
    val module = fileIndexFacade.getModuleForFile(featureFile.getVirtualFile)
    val testSourceRoots = ModuleRootManager.getInstance(module)
      .getSourceRoots(JavaSourceRootType.TEST_SOURCE)
      .asScala

    testSourceRoots.headOption.map(featureFile.getManager.findDirectory).map(_.getVirtualFile.getPath).orNull
  }

  override def createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean = {
    Try {
      if (step == null || file == null || !file.isInstanceOf[ScalaFile]) return false

      val project = file.getProject
      val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
      assert(editor != null)

      closeActiveTemplateBuilders(file)

      inWriteAction {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }

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
        element.foreach(runTemplateBuilder(project, _))
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
    val generator = new SnippetGenerator(new ScalaSnippet, new ParameterTypeRegistry(Locale.ENGLISH))
    val snippet = generator.getSnippet(CucumberStep(step), SnippetType.CAMELCASE).get(0)
    ScalaPsiElementFactory.createExpressionFromText(snippet, context).asInstanceOf[ScMethodCall]
  }

  private def runTemplateBuilder(project: Project, scMethod: ScMethodCall): Unit = {
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(scMethod).asInstanceOf[TemplateBuilderImpl]

    for (argument <- ScCucumberUtil.getStepArguments(scMethod)) {
      val name = argument.getName()
      val range = new TextRange(0, argument.getName().length)
      builder.replaceElement(argument, range, name)
    }

    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument)

    val template = builder.buildInlineTemplate()
    editor.getCaretModel.moveToOffset(scMethod.getTextRange.getStartOffset)

    val adapter = new TemplateEditingAdapter() {

      override def templateFinished(template: Template, brokenOff: Boolean): Unit = {
        inWriteAction {
          documentManager.commitDocument(editor.getDocument)
        }
      }
    }

    TemplateManager.getInstance(project).startTemplate(editor, template, adapter)
  }
}

object ScStepDefinitionCreator {

  def apply(): ScStepDefinitionCreator = new ScStepDefinitionCreator()
}
