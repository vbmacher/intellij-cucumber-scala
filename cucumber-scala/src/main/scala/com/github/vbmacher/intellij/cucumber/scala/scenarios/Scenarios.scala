package com.github.vbmacher.intellij.cucumber.scala.scenarios

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import io.cucumber.tagexpressions.TagExpressionParser
import org.jetbrains.plugins.cucumber.psi._

import scala.jdk.CollectionConverters._
import scala.util.Try

/** Shared Gherkin semantics. Call PSI operations under a read action with indexes ready. */
object Scenarios {
  /** Source declarations: outlines count once and Backgrounds are excluded. */
  def declarations(root: PsiElement): Seq[GherkinStepsHolder] =
    PsiTreeUtil.findChildrenOfType(root, classOf[GherkinStepsHolder]).asScala.toSeq
      .filterNot { case scenario: GherkinScenario => scenario.isBackground; case _ => false }

  def declarations(project: Project, scope: GlobalSearchScope): Seq[GherkinStepsHolder] =
    FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).asScala.toSeq.sortBy(_.getPath).flatMap { file =>
      ProgressManager.checkCanceled()
      Option(PsiManager.getInstance(project).findFile(file)).toSeq.flatMap(declarations)
    }

  /** A Feature Background applies to its descendants; a Rule Background stays in that Rule. */
  def forStep(step: GherkinStep): Seq[GherkinStepsHolder] = step.getStepHolder match {
    case background: GherkinScenario if background.isBackground =>
      Option(background.getParent).toSeq.flatMap(declarations)
    case scenario => Seq(scenario)
  }

  /** Applicable Backgrounds, in execution order: Feature first, then Rule. */
  def backgrounds(element: PsiElement): Seq[GherkinScenario] =
    Iterator.iterate(element)(_.getParent).takeWhile(_ != null).toSeq.reverse
      .filter(e => e.isInstanceOf[GherkinFeature] || e.isInstanceOf[GherkinRule])
      .flatMap(_.getChildren.collect { case background: GherkinScenario if background.isBackground => background })

  def inheritedTags(element: PsiElement): Seq[String] = {
    def trivia(e: PsiElement): Boolean = e.isInstanceOf[com.intellij.psi.PsiWhiteSpace] || e.isInstanceOf[com.intellij.psi.PsiComment]
    def taggable(e: PsiElement): Boolean = e.isInstanceOf[GherkinFile] || e.isInstanceOf[GherkinFeature] ||
      e.isInstanceOf[GherkinRule] || e.isInstanceOf[GherkinStepsHolder] || e.isInstanceOf[GherkinExamplesBlock]
    Iterator.iterate(element)(_.getParent).takeWhile(_ != null).filter(taggable).flatMap { owner =>
      val leading = owner.getChildren.takeWhile(e => trivia(e) || e.isInstanceOf[GherkinTag])
        .collect { case tag: GherkinTag => tag.getText }
      // Examples tags are siblings before the block in the Gherkin PSI, not its children.
      val preceding = Iterator.iterate(owner.getPrevSibling)(e => if (e == null) null else e.getPrevSibling)
        .takeWhile(e => e != null && (trivia(e) || e.isInstanceOf[GherkinTag]))
        .collect { case tag: GherkinTag => tag.getText }.toSeq
      leading ++ preceding
    }.toSeq.distinct.sorted
  }

  def sourceOffset(target: PsiElement): Int = target match {
    case scenario: GherkinStepsHolder =>
      scenario.getChildren.find(child => Set[com.intellij.psi.tree.IElementType](GherkinTokenTypes.SCENARIO_KEYWORD, GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD, GherkinTokenTypes.BACKGROUND_KEYWORD).contains(child.getNode.getElementType))
        .map(_.getTextOffset).getOrElse(scenario.getTextOffset)
    case _ => target.getTextOffset
  }
  def tagSets(scenario: GherkinStepsHolder): Seq[Seq[String]] = scenario match {
    case outline: GherkinScenarioOutline =>
      val sets = outline.getExamplesBlocks.asScala.toSeq.filter(block => Option(block.getTable).exists(!_.getDataRows.isEmpty))
        .map(inheritedTags)
      if (sets.nonEmpty) sets else Seq(inheritedTags(outline))
    case _ => Seq(inheritedTags(scenario))
  }
  def tags(scenario: GherkinStepsHolder): Seq[String] = tagSets(scenario).flatten.distinct.sorted
  final case class Location(scenario: GherkinStepsHolder, target: PsiElement, tags: Seq[String]) {
    def line: Int = target.getContainingFile.getText.take(sourceOffset(target)).count(_ == '\n') + 1
    def selector: String = target.getContainingFile.getVirtualFile.getPath + ":" + line
    override def toString: String = selector + " · " + scenario.getScenarioName + " · " + tags.mkString(" ")
  }
  /** Executable locations: one per ordinary scenario, one per outline data row, none for Backgrounds. */
  def locations(scenario: GherkinStepsHolder): Seq[Location] = scenario match {
    case background: GherkinScenario if background.isBackground => Seq.empty
    case outline: GherkinScenarioOutline => outline.getExamplesBlocks.asScala.toSeq.flatMap { block =>
      Option(block.getTable).toSeq.flatMap(_.getDataRows.asScala).map(row => Location(outline, row, inheritedTags(row)))
    }
    case _ => Seq(Location(scenario, scenario, inheritedTags(scenario)))
  }

  def locations(project: Project, scope: GlobalSearchScope, expression: String): Either[String, Seq[Location]] =
    Try(TagExpressionParser.parse(expression)).toEither.left.map(_.getMessage).map { parsed =>
      declarations(project, scope).flatMap(locations).filter { location =>
        ProgressManager.checkCanceled()
        parsed.evaluate(location.tags.asJava)
      }
    }
}
