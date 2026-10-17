package com.github.vbmacher.intellij.cucumber.scala.completion

import com.github.vbmacher.intellij.cucumber.scala.refactoring.ScStepRename
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.ScParameterTypes
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import io.cucumber.cucumberexpressions.ExpressionFactory

import scala.jdk.CollectionConverters._
import scala.util.Try

object ScStepTemplates {
  def insert(context: InsertionContext, target: PsiElement, expression: String, samples: Seq[String], originalPrefix: String): Boolean = {
    context.setAddCompletionChar(false)
    insert(context.getEditor, context.getStartOffset, context.getTailOffset, target, expression, samples, originalPrefix)
  }

  def insert(editor: com.intellij.openapi.editor.Editor, start: Int, tail: Int, target: PsiElement, expression: String, samples: Seq[String], originalPrefix: String): Boolean = {
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
    val sample = usable.orElse {
      Option(Messages.showInputDialog(target.getProject, "Enter example step wording matching this expression:\n" + expression,
        "Step Parameter Values", null)).filter(value => parsed.exists(p => Try(Option(p.`match`(value))).toOption.flatten.nonEmpty))
    }
    sample match {
      case None =>
        editor.getDocument.replaceString(start, tail, originalPrefix)
        editor.getCaretModel.moveToOffset(start + originalPrefix.length)
        true
      case Some(wording) =>
        val arguments = parsed.flatMap(p => Option(p.`match`(wording))).toSeq.flatMap(_.asScala)
        val template = TemplateManager.getInstance(target.getProject).createTemplate("", "Scala Cucumber")
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
        TemplateManager.getInstance(target.getProject).startTemplate(editor, template)
        true
    }
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
