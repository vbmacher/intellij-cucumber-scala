package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util.{Objects, Properties}

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDirectory, PsiFile}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator

class ScStepDefinitionCreator extends AbstractStepDefinitionCreator {

  import ScStepDefinitionCreator.LOG

  private final val templateName = "Step Definitions.scala"

  override def createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile = {
    val templateManager = FileTemplateManager.getInstance(directory.getProject)
    val template = templateManager.getInternalTemplate(templateName)

    val defaultProperties = templateManager.getDefaultProperties
    val properties = new Properties(defaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name)

    val fileName = name + '.' + ScalaFileType.INSTANCE
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

  override def createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean = {
    if (step == null || file == null || !file.isInstanceOf[ScalaFile]) false
    else {
      val project = file.getProject
      val vFile = Objects.requireNonNull(file.getVirtualFile)
      val descriptor = new OpenFileDescriptor(project, vFile)

      val fileEditorManager = FileEditorManager.getInstance(project)
      fileEditorManager.getAllEditors(vFile)
      fileEditorManager.openTextEditor(descriptor, true)
      val editor = FileEditorManager.getInstance(project).getSelectedTextEditor

      if (editor != null) {
        val templateManager = TemplateManager.getInstance(file.getProject)
        val templateState = TemplateManagerImpl.getTemplateState(editor)
        val template = templateManager.getActiveTemplate(editor)
        if (templateState != null && template != null) {
          templateState.gotoEnd()
        }
      }

      true
    }
  }
}

object ScStepDefinitionCreator {
  private final val LOG = Logger.getInstance(classOf[ScStepDefinitionCreator])

  def apply(): ScStepDefinitionCreator = new ScStepDefinitionCreator()
}
