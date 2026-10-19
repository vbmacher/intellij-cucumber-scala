package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.refactoring.ScStepRename
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.ScParameterTypes
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import io.cucumber.cucumberexpressions.{Expression, ExpressionFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object ScStepTemplates {
  def insert(context: InsertionContext, target: PsiElement, expression: String, samples: Seq[String], originalPrefix: String): Boolean = {
    context.setAddCompletionChar(false)
    insert(context.getEditor, context.getStartOffset, context.getTailOffset, target, expression, samples, originalPrefix)
  }

  def insert(editor: Editor, start: Int, tail: Int, target: PsiElement, expression: String, samples: Seq[String], originalPrefix: String): Boolean = {
    val module = moduleOf(target)
    val factory = new ExpressionFactory(ScParameterTypes.registry(module, snippetsOnly = false))
    val parsed = Try(factory.createExpression(expression)).toOption
    val proposed = ScStepRename.parts(expression).toOption.map { case (text, names) =>
      val custom = module.toSeq.flatMap(ScParameterTypes.registrations).map(r => r.name -> r.regex).toMap
      val values = names.map {
        case "string" => Some("\"value\"")
        case "word" | "" => Some("value")
        case "int" | "long" | "short" | "byte" | "biginteger" => Some("1")
        case "float" | "double" | "bigdecimal" => Some("1.0")
        case name => custom.get(name).flatMap(regexSample)
      }
      if (values.forall(_.nonEmpty)) Some(text.head + values.flatten.zip(text.drop(1)).map { case (value, tail) => value + tail }.mkString) else None
    }.flatten
    val usable = (samples ++ proposed).find(sample => parsed.exists(p => Try(Option(p.`match`(sample))).toOption.flatten.nonEmpty))
    usable match {
      case Some(wording) => startTemplate(editor, start, tail, target.getProject, parsed.get, wording)
      case None =>
        editor.getDocument.replaceString(start, tail, originalPrefix)
        editor.getCaretModel.moveToOffset(start + originalPrefix.length)
        parsed.foreach(expressionValue => askForWording(editor, start, target.getProject, expression, expressionValue, originalPrefix))
    }
    true
  }

  // Callers insert under a write action, where a modal dialog must not run; ask afterwards and insert into the unchanged prefix.
  private def askForWording(editor: Editor, start: Int, project: Project, expression: String, parsed: Expression, prefix: String): Unit = {
    val marker = editor.getDocument.createRangeMarker(start, start + prefix.length)
    ApplicationManager.getApplication.invokeLater(() => {
      try {
        Option(Messages.showInputDialog(project, "Enter example step wording matching this expression:\n" + expression, "Step Parameter Values", null))
          .filter(value => Try(Option(parsed.`match`(value))).toOption.flatten.nonEmpty)
          .filter(_ => marker.isValid && !editor.isDisposed && marker.getDocument.getText(marker.getTextRange) == prefix)
          .foreach { wording =>
            WriteCommandAction.runWriteCommandAction(project, "Insert Scala step", null, () =>
              startTemplate(editor, marker.getStartOffset, marker.getEndOffset, project, parsed, wording))
          }
      } finally marker.dispose()
    }, project.getDisposed)
  }

  private def startTemplate(editor: Editor, start: Int, tail: Int, project: Project, parsed: Expression, wording: String): Unit = {
    val arguments = Option(parsed.`match`(wording)).toSeq.flatMap(_.asScala)
    val template = TemplateManager.getInstance(project).createTemplate("", "Scala Cucumber")
    var end = 0
    arguments.zipWithIndex.foreach { case (argument, index) =>
      val group = argument.getGroup
      template.addTextSegment(wording.substring(end, group.getStart))
      val value = new TextExpression(wording.substring(group.getStart, group.getEnd))
      template.addVariable("parameter" + index, value, value, true)
      end = group.getEnd
    }
    template.addTextSegment(wording.substring(end))
    editor.getDocument.deleteString(start, tail)
    editor.getCaretModel.moveToOffset(start)
    TemplateManager.getInstance(project).startTemplate(editor, template)
  }

  // Bounded sample proposals are verified against the runtime regex before being used.
  // Complex regexes without an existing usage require an explicit example from the author.
  private def regexSample(regex: String): Option[String] = {
    val simplified = regex.stripPrefix("^").stripSuffix("$")
      .replaceAll("\\\\d(?:[+*?]|\\{[0-9,]+\\})?", "1")
      .replaceAll("\\[0-9\\](?:[+*?]|\\{[0-9,]+\\})?", "1")
      .replaceAll("\\[A-Z\\](?:[+*?]|\\{[0-9,]+\\})?", "A")
      .replaceAll("\\[a-z\\](?:[+*?]|\\{[0-9,]+\\})?", "a")
    Seq(simplified, "1", "value", "A", "a").find(value => Try(java.util.regex.Pattern.compile(regex).matcher(value).matches()).getOrElse(false))
  }
}
