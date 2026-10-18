package com.github.vbmacher.intellij.cucumber.scala.publish

import com.github.vbmacher.intellij.cucumber.scala.run.ScenarioSelection
import com.github.vbmacher.intellij.cucumber.scala.steps.StepUsage
import com.github.vbmacher.intellij.cucumber.scala.tables.GherkinTables
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._

object FeatureHtml {
  private def escape(text: String): String = StringUtil.escapeXmlEntities(Option(text).getOrElse(""))
  def render(file: GherkinFile, selected: Option[Set[Int]] = None, rows: Option[Set[Int]] = None, dark: Boolean = false): String = {
    def link(element: PsiElement, title: String): String = s"<a href=\"source:${element.getTextOffset}\">${escape(title)}</a>"
    def children(element: PsiElement): String = element.getChildren.map(node).mkString
    def heading(element: PsiElement, level: Int, title: String): String = s"<h$level>${link(element, title)}</h$level>"
    def description(element: PsiElement): String = {
      val text = element.getText
      val headerOffset = element match {
        case holder: GherkinStepsHolder => ScenarioSelection.sourceOffset(holder) - element.getTextOffset
        case _ => 0
      }
      val lineEnd = text.indexOf('\n', headerOffset)
      if (lineEnd < 0) "" else {
        val firstContent = element.getChildren.find(e => e.isInstanceOf[GherkinStep] || e.isInstanceOf[GherkinStepsHolder] || e.isInstanceOf[GherkinRule] || e.isInstanceOf[GherkinExamplesBlock])
          .map(_.getTextOffset - element.getTextOffset).getOrElse(text.length)
        val value = text.substring(lineEnd + 1, math.max(lineEnd + 1, firstContent)).trim
        if (value.isEmpty) "" else "<pre>" + escape(value) + "</pre>"
      }
    }
    def node(element: PsiElement): String = element match {
      case error: PsiErrorElement => s"<p class=\"error\">${link(error, error.getErrorDescription)}</p>"
      case feature: GherkinFeature => heading(feature, 1, feature.getFeatureName) + description(feature) + children(feature)
      case rule: GherkinRule if selected.forall(allowed => com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(rule, classOf[GherkinStepsHolder]).asScala.exists(s => allowed(s.getTextOffset))) =>
        heading(rule, 2, rule.getRuleName) + description(rule) + children(rule)
      case _: GherkinRule => ""
      case scenario: GherkinStepsHolder if selected.forall(_(scenario.getTextOffset)) || (scenario.isInstanceOf[GherkinScenario] && scenario.asInstanceOf[GherkinScenario].isBackground) =>
        "<section>" + heading(scenario, 3, scenario.getScenarioKeyword + ": " + scenario.getScenarioName) +
          "<p class=\"tags\">" + escape(StepUsage.tags(scenario).mkString(" ")) + "</p>" + description(scenario) + children(scenario) + "</section>"
      case _: GherkinStepsHolder => ""
      case step: GherkinStep => "<p>" + link(step, step.getKeyword.getText + " " + step.getName) + "</p>" +
        Option(step.getTable).map(node).getOrElse("") + Option(step.getPystring).map(node).getOrElse("") +
        com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(step, classOf[PsiErrorElement]).asScala.map(node).mkString
      case doc: GherkinPystring => "<pre>" + escape(doc.getText) + "</pre>"
      case table: GherkinTable => "<table>" + (Option(table.getHeaderRow).toSeq ++ table.getDataRows.asScala.filter(row =>
        !table.getParent.isInstanceOf[GherkinExamplesBlock] || rows.forall(_(row.getTextOffset)))).map { row =>
        "<tr>" + row.getPsiCells.asScala.map(cell => "<td>" + escape(GherkinTables.decode(cell.getText.trim)).replace("\n", "<br>") + "</td>").mkString + "</tr>"
      }.mkString + "</table>"
      case block: GherkinExamplesBlock if rows.exists(allowed => !Option(block.getTable).toSeq.flatMap(_.getDataRows.asScala).exists(row => allowed(row.getTextOffset))) => ""
      case block: GherkinExamplesBlock => heading(block, 4, block.getText.linesIterator.nextOption().getOrElse("Examples")) + "<p class=\"tags\">" + escape(StepUsage.tags(block).mkString(" ")) + "</p>" + description(block) + children(block)
      case _: GherkinFile => children(element)
      case _ => ""
    }
    val background = if (dark) "#252526" else "#ffffff"
    val foreground = if (dark) "#dddddd" else "#222222"
    // Swing's HTML renderer needs an explicit link color; CSS color:inherit renders links black.
    s"""<!doctype html><html><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'"><title>${escape(file.getName)}</title>
       |<style>body{font-family:sans-serif;max-width:1000px;margin:2em;color:$foreground;background:$background}section{border:1px solid #888;padding:1em;margin:1em 0}a{color:$foreground}table{border-collapse:collapse}td{border:1px solid #888;padding:.4em}pre{white-space:pre-wrap}.tags{color:#888}.error{color:#c44}</style></head><body>${node(file)}</body></html>""".stripMargin
  }
}
