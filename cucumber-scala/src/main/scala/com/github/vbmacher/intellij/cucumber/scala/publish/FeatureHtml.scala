package com.github.vbmacher.intellij.cucumber.scala.publish

import com.github.vbmacher.intellij.cucumber.scala.scenarios.Scenarios
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
    def tags(element: PsiElement): String = {
      val names = Scenarios.inheritedTags(element)
      if (names.isEmpty) "" else "<p class=\"tags\">" + escape(names.mkString(" ")) + "</p>"
    }
    def headerOffset(element: PsiElement): Int = {
      // Keywords are AST leaves and are omitted by Gherkin's getChildren implementation.
      Option(element.getNode.findChildByType(GherkinTokenTypes.KEYWORDS))
        .map(_.getStartOffset - element.getTextRange.getStartOffset).getOrElse(0)
    }
    def headerLine(element: PsiElement): String =
      element.getText.substring(headerOffset(element)).linesIterator.nextOption().getOrElse("").trim

    def description(element: PsiElement): String = {
      val text = element.getText
      val startOffset = element.getTextRange.getStartOffset
      val lineEnd = text.indexOf('\n', headerOffset(element))
      if (lineEnd < 0) "" else {
        val firstContent = element.getChildren.find(e => e.isInstanceOf[GherkinStep] || e.isInstanceOf[GherkinStepsHolder] || e.isInstanceOf[GherkinRule] || e.isInstanceOf[GherkinExamplesBlock] || e.isInstanceOf[GherkinTable])
          .map(_.getTextRange.getStartOffset - startOffset).getOrElse(text.length)
        val value = text.substring(lineEnd + 1, math.max(lineEnd + 1, firstContent)).trim
        if (value.isEmpty) "" else "<p class=\"description\">" + escape(value).replace("\n", "<br>") + "</p>"
      }
    }
    def node(element: PsiElement): String = element match {
      case error: PsiErrorElement => s"<p class=\"error\">${link(error, error.getErrorDescription)}</p>"
      case feature: GherkinFeature => heading(feature, 1, feature.getFeatureName) + description(feature) + children(feature)
      case rule: GherkinRule if selected.forall(allowed => com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(rule, classOf[GherkinStepsHolder]).asScala.exists(s => allowed(s.getTextOffset))) =>
        heading(rule, 2, rule.getRuleName) + description(rule) + children(rule)
      case _: GherkinRule => ""
      case scenario: GherkinStepsHolder if selected.forall(_(scenario.getTextOffset)) || (scenario.isInstanceOf[GherkinScenario] && scenario.asInstanceOf[GherkinScenario].isBackground) =>
        "<div class=\"scenario\">" + heading(scenario, 3, headerLine(scenario)) +
          tags(scenario) + description(scenario) + children(scenario) + "</div>"
      case _: GherkinStepsHolder => ""
      case step: GherkinStep => "<p class=\"step\">" + link(step, step.getKeyword.getText + " " + step.getName) + "</p>" +
        Option(step.getTable).map(node).getOrElse("") + Option(step.getPystring).map(node).getOrElse("") +
        com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(step, classOf[PsiErrorElement]).asScala.map(node).mkString
      case doc: GherkinPystring => "<pre>" + escape(doc.getText) + "</pre>"
      case table: GherkinTable => "<table cellspacing=\"0\" cellpadding=\"5\">" + (Option(table.getHeaderRow).toSeq ++ table.getDataRows.asScala.filter(row =>
        !table.getParent.isInstanceOf[GherkinExamplesBlock] || rows.forall(_(row.getTextOffset)))).map { row =>
        val cellTag = if (row == table.getHeaderRow) "th" else "td"
        "<tr>" + row.getPsiCells.asScala.map(cell => s"<$cellTag>" + escape(GherkinTables.decode(cell.getText.trim)).replace("\n", "<br>") + s"</$cellTag>").mkString + "</tr>"
      }.mkString + "</table>"
      case block: GherkinExamplesBlock if rows.exists(allowed => !Option(block.getTable).toSeq.flatMap(_.getDataRows.asScala).exists(row => allowed(row.getTextOffset))) => ""
      case block: GherkinExamplesBlock => heading(block, 4, headerLine(block)) + tags(block) + description(block) + children(block)
      case _: GherkinFile => children(element)
      case _ => ""
    }
    val background = if (dark) "#252526" else "#ffffff"
    val foreground = if (dark) "#dddddd" else "#222222"
    val border = if (dark) "#4b4d50" else "#d6d9dd"
    val surface = if (dark) "#2d2f31" else "#f5f6f7"
    val muted = if (dark) "#aaaaaa" else "#666666"
    val cellStyle = s"border:1px solid $border;padding:4px 8px;text-align:left"
    // Use HTML 3.2 containers and pixel spacing supported by Swing's HTML renderer.
    // Swing's HTML renderer needs an explicit link color; CSS color:inherit renders links black.
    s"""<!doctype html><html><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'"><title>${escape(file.getName)}</title>
       |<style>
       |body{font-family:sans-serif;font-size:12pt;max-width:1000px;margin:16px;color:$foreground;background:$background}
       |h1{font-size:18pt;margin:0 0 10px 0}h2{font-size:14pt;margin:14px 0 6px 0}
       |h3{font-size:13pt;margin:0 0 6px 0}h4{font-size:12pt;margin:10px 0 4px 0}
       |p{margin:3px 0}.description{margin:0 0 10px 0}
       |div.scenario{border:1px solid $border;padding:10px;margin:6px 0}
       |a{color:$foreground;text-decoration:none}.step{margin:2px 0}
       |table{border-collapse:collapse;margin:6px 0}td{$cellStyle}th{$cellStyle;background:$surface}
       |pre{font-size:11pt;white-space:pre-wrap;background:$surface;padding:8px;margin:6px 0}
       |.tags{font-size:10pt;color:$muted;margin:0 0 6px 0}.error{color:#c44}
       |</style></head><body>${node(file)}</body></html>""".stripMargin
  }
}
