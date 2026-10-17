package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.steps.StepUsage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import io.cucumber.tagexpressions.TagExpressionParser
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._
import scala.util.Try

object ScenarioSelection {
  def sourceOffset(target: PsiElement): Int = target match {
    case scenario: GherkinStepsHolder =>
      scenario.getChildren.find(child => Set[com.intellij.psi.tree.IElementType](GherkinTokenTypes.SCENARIO_KEYWORD, GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD, GherkinTokenTypes.BACKGROUND_KEYWORD).contains(child.getNode.getElementType))
        .map(_.getTextOffset).getOrElse(scenario.getTextOffset)
    case _ => target.getTextOffset
  }
  def tagSets(scenario: GherkinStepsHolder): Seq[Seq[String]] = scenario match {
    case outline: GherkinScenarioOutline =>
      val sets = outline.getExamplesBlocks.asScala.toSeq.filter(block => Option(block.getTable).exists(!_.getDataRows.isEmpty))
        .map(StepUsage.tags)
      if (sets.nonEmpty) sets else Seq(StepUsage.tags(outline))
    case _ => Seq(StepUsage.tags(scenario))
  }
  def tags(scenario: GherkinStepsHolder): Seq[String] = tagSets(scenario).flatten.distinct.sorted
  final case class Location(scenario: GherkinStepsHolder, target: PsiElement, tags: Seq[String]) {
    def line: Int = target.getContainingFile.getText.take(sourceOffset(target)).count(_ == '\n') + 1
    def selector: String = target.getContainingFile.getVirtualFile.getPath + ":" + line
    override def toString: String = selector + " · " + scenario.getScenarioName + " · " + tags.mkString(" ")
  }
  def locations(project: Project, scope: GlobalSearchScope, expression: String): Either[String, Seq[Location]] =
    Try(TagExpressionParser.parse(expression)).toEither.left.map(_.getMessage).map { parsed =>
      FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).asScala.toSeq.sortBy(_.getPath).flatMap { file =>
        ProgressManager.checkCanceled()
        Option(PsiManager.getInstance(project).findFile(file)).toSeq.flatMap { psi =>
          PsiTreeUtil.findChildrenOfType(psi, classOf[GherkinStepsHolder]).asScala.toSeq.flatMap {
            case scenario: GherkinScenario if scenario.isBackground => Seq.empty
            case outline: GherkinScenarioOutline => outline.getExamplesBlocks.asScala.toSeq.flatMap { block =>
              Option(block.getTable).toSeq.flatMap(_.getDataRows.asScala).map(row => Location(outline, row, StepUsage.tags(row)))
            }
            case scenario => Seq(Location(scenario, scenario, StepUsage.tags(scenario)))
          }.filter(location => parsed.evaluate(location.tags.asJava))
        }
      }
    }
}
