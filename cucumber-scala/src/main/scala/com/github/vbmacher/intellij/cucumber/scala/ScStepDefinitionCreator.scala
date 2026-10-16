package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template._
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{FileIndexFacade, ModuleRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import io.cucumber.cucumberexpressions.{CucumberExpressionGenerator, ParameterTypeRegistry}
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.project.ProjectContext

import java.lang.reflect.Type
import java.util.{Locale, Properties}
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScStepDefinitionCreator extends AbstractStepDefinitionCreator {
  import ScStepDefinitionCreator._

  override def createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile = {
    val templateManager = FileTemplateManager.getInstance(directory.getProject)
    val properties = new Properties(templateManager.getDefaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name)

    try {
      val template = templateManager.getInternalTemplate("Scala Step Definitions.scala")
      FileTemplateUtil.createFromTemplate(template, name + ScalaFileType.INSTANCE.getExtensionWithDot, properties, directory).getContainingFile
    } catch {
      case e: Exception if !e.isInstanceOf[IncorrectOperationException] =>
        Log.error(e)
        null
    }
  }

  override def validateNewStepDefinitionFileName(project: Project, name: String): Boolean =
    name.nonEmpty && ScalaNamesValidator.isIdentifier(name)

  override def getDefaultStepFileName(step: GherkinStep): String = "StepDefinitions"

  override def getDefaultStepDefinitionFolderPath(step: GherkinStep): String = {
    val featureFile = step.getContainingFile
    val module = FileIndexFacade.getInstance(featureFile.getProject).getModuleForFile(featureFile.getVirtualFile)
    ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).asScala.headOption
      .map(featureFile.getManager.findDirectory(_).getVirtualFile.getPath)
      .orNull
  }

  override def createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean = file match {
    case scalaFile: ScalaFile if step != null =>
      Try {
        val project = file.getProject
        val documentManager = PsiDocumentManager.getInstance(project)
        closeActiveTemplateBuilders(file)
        documentManager.commitAllDocuments()

        val created = for {
          typeDefinition <- scalaFile.typeDefinitions.headOption
          body <- typeDefinition.extendsBlock.templateBody
        } yield {
          val snippet = ScalaPsiElementFactory.createExpressionFromText(snippetFor(step), body)(ProjectContext.fromProject(project))
          CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(body.addBefore(snippet, body.getLastChild).asInstanceOf[ScMethodCall])
        }
        documentManager.commitAllDocuments()

        if (withTemplate && !ApplicationManager.getApplication.isUnitTestMode) created.foreach(runTemplate(project, _))
        created.nonEmpty
      }.fold(e => { Log.error(e); false }, identity)

    case _ => false
  }

  private def runTemplate(project: Project, call: ScMethodCall): Unit =
    Option(FileEditorManager.getInstance(project).getSelectedTextEditor).foreach { editor =>
      val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(call).asInstanceOf[TemplateBuilderImpl]
      for (parameter <- call.parameters) {
        builder.replaceElement(parameter, new TextRange(0, parameter.getName.length), parameter.getName)
      }

      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument)
      val template = builder.buildInlineTemplate()
      editor.getCaretModel.moveToOffset(call.getTextRange.getStartOffset)

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
        override def templateFinished(template: Template, brokenOff: Boolean): Unit =
          documentManager.commitDocument(editor.getDocument)
      })
    }
}

object ScStepDefinitionCreator {
  private val Log = Logger.getInstance(classOf[ScStepDefinitionCreator])

  private lazy val expressionGenerator = new CucumberExpressionGenerator(new ParameterTypeRegistry(Locale.getDefault))

  private[scala] def snippetFor(step: GherkinStep): String = {
    val expression = expressionGenerator.generateExpressions(step.getSubstitutedName).get(0)
    val parameters = expression.getParameterNames.asScala.zip(expression.getParameterTypes.asScala.map(t => scalaType(t.getType)))
    val argument = Option(step.getTable).map(_ => "dataTable" -> "io.cucumber.datatable.DataTable")
      .orElse(Option(step.getPystring).map(_ => "docString" -> "String"))
    val keyword = step.getKeyword.getText.replaceAll("[\\s',!\u00AD’]", "") match {
      case "*" => "Given"
      case name => name
    }
    val arguments = (parameters ++ argument).map { case (name, tpe) => s"$name: $tpe" }.mkString(", ")
    val quotes = "\"\"\""

    s"""$keyword($quotes${expression.getSource}$quotes) { ($arguments) =>
       |  // Write code here that turns the phrase above into concrete actions
       |  throw new io.cucumber.scala.PendingException()
       |}""".stripMargin
  }

  private def scalaType(tpe: Type): String = tpe match {
    case c: Class[_] if c == classOf[java.lang.Integer] => "Int"
    case c: Class[_] => c.getSimpleName
    case other => other.getTypeName
  }
}
