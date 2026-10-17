package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.completion.ScStepCompletion
import com.github.vbmacher.intellij.cucumber.scala.run.RunResults
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScParameterTypes, ScStepDefinition, StepUsage}
import com.intellij.lang.documentation.AbstractDocumentationProvider
import io.cucumber.cucumberexpressions.ExpressionFactory
import scala.jdk.CollectionConverters._
import scala.util.Try
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep


class ScStepDocumentation extends AbstractDocumentationProvider {
  override def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val step = Option(originalElement).flatMap(e => Option(PsiTreeUtil.getParentOfType(e, classOf[GherkinStep], false)))
    step.filter(s => matchingSteps(s).nonEmpty || RunResults.getInstance(s.getProject).at(s).nonEmpty).map { s =>
      val candidates = matchingSteps(s).collect { case d: ScStepDefinition => d }
      val title = if (candidates.size > 1) s"Ambiguous step: ${candidates.size} matching definitions" else "Scala step"
      s"<html><body><b>$title</b>" + candidates.map(d => details(d, s)).mkString("<hr>", "<hr>", "") + "<pre>" + escape(RunResults.getInstance(s.getProject).describe(s)) + "</pre></body></html>"
    }.orElse {
      asCall(element).orElse(Option(element).flatMap(e => asCall(e.getParent))).filter(_.definition.nonEmpty)
        .map { call =>
          val usages = StepUsage.scenarios(call.getFirstChild)
          s"<html><body><pre>${escape(call.pattern.getOrElse(""))}</pre>" + usagePreview(usages) + "</body></html>"
        }
    }.orNull
  }

  private def details(definition: ScStepDefinition, step: GherkinStep): String = {
    val element = definition.getElement
    val call = asCall(element.getParent)
    val expression = call.flatMap(_.pattern).getOrElse(definition.getExpression)
    val params = call.toSeq.flatMap(_.parameters)
    val captures = Try(new ExpressionFactory(ScParameterTypes.registry(moduleOf(step), snippetsOnly = false))
      .createExpression(expression).`match`(step.getSubstitutedName)).toOption.flatMap(Option(_))
      .map(_.asScala.toSeq.map(_.getGroup.getValues.asScala.mkString(", ")))
    val rows = params.zipWithIndex.map { case (p, i) =>
      val trailing = if (i == params.size - 1 && step.getTable != null) Some("DataTable (" + step.getTable.getText + ")")
        else if (i == params.size - 1 && step.getPystring != null) Some("DocString (" + step.getPystring.getText + ")") else None
      val value = trailing.orElse(captures.flatMap(_.lift(i))).getOrElse("unknown (not statically resolved)")
      s"<li>${escape(p.getText)} = ${escape(value)}</li>"
    }.mkString
    val outline = if (step.getName != step.getSubstitutedName) "<p>Outline: representative example; other rows may differ.</p>" else ""
    s"<pre>${escape(expression)}</pre>${sourceLink(element)}" + ScStepCompletion.deprecation(element).map(d => s"<p>${escape(d)}</p>").getOrElse("") + s"<ul>$rows</ul>$outline" + usagePreview(StepUsage.scenarios(element))
  }

  private def usagePreview(scenarios: Seq[org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder]): String =
    s"<p>${scenarios.size} matching scenarios (includes ambiguous matches; outlines count once)</p>" +
      scenarios.headOption.map(s => s"<p>${escape(StepUsage.tags(s).mkString(" "))} ${sourceLink(s)}</p><pre>${escape(s.getText)}</pre>").getOrElse("")

  private def sourceLink(element: PsiElement): String = {
    val file = element.getContainingFile
    val path = file.getVirtualFile.getUrl
    s"<a href=\"psi_element://${escape(path)}#${element.getTextOffset}\">${escape(file.getName)}:${file.getText.take(element.getTextOffset).count(_ == '\n') + 1}</a>"
  }

  override def getDocumentationElementForLink(manager: PsiManager, link: String, context: PsiElement): PsiElement = {
    val split = link.lastIndexOf('#')
    if (split < 0) null
    else {
      val file = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(link.substring(0, split))
      for {
        virtualFile <- Option(file)
        psi <- Option(manager.findFile(virtualFile))
        offset <- link.substring(split + 1).toIntOption
        target <- Option(psi.findElementAt(offset))
      } yield target
    }.orNull
  }

  private def escape(value: String): String = StringUtil.escapeXmlEntities(Option(value).getOrElse("unknown"))
}
