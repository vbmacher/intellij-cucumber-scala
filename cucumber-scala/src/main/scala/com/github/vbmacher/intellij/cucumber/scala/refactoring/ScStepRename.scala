package com.github.vbmacher.intellij.cucumber.scala.refactoring

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.github.vbmacher.intellij.cucumber.scala.steps.{ScParameterTypes, ScStepDefinition, StepDeclaration}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.references.PomService
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import io.cucumber.cucumberexpressions.ExpressionFactory
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util
import scala.jdk.CollectionConverters._
import scala.util.Try

object ScStepRename {
  private val Parameter = "\\{([^{}]*)\\}".r
  private[scala] def parts(expression: String): Either[String, (Seq[String], Seq[String])] = {
    if (!CucumberUtil.isCucumberExpression(expression) || expression.exists("\\()/".contains(_)))
      Left("Rename supports literal Cucumber Expressions with unchanged parameters; regex, optional, alternative and escaped forms require manual review.")
    else {
      val matches = Parameter.findAllMatchIn(expression).toSeq
      Right((Parameter.split(expression).toSeq.padTo(matches.size + 1, ""), matches.map(_.group(1))))
    }
  }

  def plan(declaration: StepDeclaration, newName: String, steps: Seq[GherkinStep]): Either[String, Seq[(GherkinStep, String)]] = {
    val element = declaration.element
    for {
      old <- parts(declaration.getName)
      fresh <- parts(newName)
      ifSame <- if (old._2 == fresh._2) Right(()) else Left("Keep parameter names, types and order unchanged when renaming static wording.")
      literal <- asCall(element).flatMap(_.args.exprs.headOption).collect { case l: ScLiteral => l }
        .toRight("Dynamic and concatenated Scala expressions cannot be renamed safely.")
      writable <- if (element.isWritable && steps.forall(_.isWritable)) Right(()) else Left("The definition or a matching usage is read-only. Review library consumers before renaming.")
      unique <- if (steps.forall(s => matchingSteps(s).size == 1)) Right(()) else Left("A matching usage has ambiguous definitions. Resolve ambiguity before renaming.")
      result <- {
        val factory = new ExpressionFactory(ScParameterTypes.registry(moduleOf(element), snippetsOnly = false))
        val expression = Try {
          factory.createExpression(newName)
          factory.createExpression(declaration.getName)
        }.toEither.left.map(_.getMessage)
        expression.flatMap { parsed =>
          def captures(text: String): Option[Seq[String]] =
            Try(Option(parsed.`match`(text)).map(_.asScala.toSeq.map(a => text.substring(a.getGroup.getStart, a.getGroup.getEnd)))).toOption.flatten
          def wording(args: Seq[String]): String =
            fresh._1.head + args.zip(fresh._1.drop(1)).map { case (arg, text) => arg + text }.mkString
          val replacements = steps.map { step =>
            val original = step.getName
            val arguments = if (original == step.getSubstitutedName) captures(original)
            else {
              // Outline placeholders must remain placeholders, including in quotes.
              val regex = old._1.map(java.util.regex.Pattern.quote).mkString("^", "(.+?)", "$").r
              regex.findFirstMatchIn(original).map(m => (1 to m.groupCount).map(m.group))
            }
            arguments.filter(_.size == fresh._2.size).map(args => step -> wording(args))
          }
          if (replacements.exists(_.isEmpty)) Left("At least one usage cannot preserve its arguments safely.")
          else {
            val modules = (moduleOf(element).toSeq ++ steps.flatMap(moduleOf)).distinct
            val otherDefinitions = modules.map(m => m -> CucumberStepHelper.loadStepsFor(m).asScala.toSeq
              .filter(d => d.getElement != null && d.getElement != element)).toMap
            val collision = otherDefinitions.values.flatten.exists(_.getExpression == newName) || steps.exists { step =>
              captures(step.getSubstitutedName).exists(args => moduleOf(step).toSeq.flatMap(otherDefinitions)
                .exists(d => d.supportsStep(step) && d.matches(wording(args))))
            }
            if (collision) Left("The renamed expression conflicts with another step definition.")
            else Right(replacements.flatten)
          }
        }
      }
    } yield result
  }

  def setDefinition(declaration: StepDeclaration, name: String): Unit = {
    val literal = asCall(declaration.element).flatMap(_.args.exprs.headOption).getOrElse(throw new IncorrectOperationException("Missing step expression"))
    literal.replace(ScalaPsiElementFactory.createExpressionFromText("\"" + StringUtil.escapeStringCharacters(name) + "\"", literal)(ProjectContext.fromProject(literal.getProject)))
  }
}

class ScStepRenameProcessor extends RenamePsiElementProcessor {
  private def declaration(element: PsiElement): Option[StepDeclaration] = StepDeclaration.of(element).orElse(element match {
    case step: GherkinStep => matchingSteps(step) match {
      case Seq(definition: ScStepDefinition) => asCall(definition.getElement.getParent).flatMap(_.pattern).map(StepDeclaration(definition.getElement, _))
      case _ => None
    }
    case _ => None
  })
  override def canProcessElement(element: PsiElement): Boolean = declaration(element).nonEmpty
  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement =
    declaration(element).map(d => PomService.convertToPsi(element.getProject, d)).orNull
  override def forcesShowPreview(): Boolean = true
  override def isInplaceRenameSupported: Boolean = false
  override def findReferences(element: PsiElement, scope: SearchScope, searchInComments: Boolean): util.Collection[PsiReference] =
    ReferencesSearch.search(element, scope).findAll()

  override def findExistingNameConflicts(element: PsiElement, name: String, conflicts: MultiMap[PsiElement, String]): Unit = {
    declaration(element).foreach { d =>
      val steps = ReferencesSearch.search(element).findAll().asScala.map(_.getElement).collect { case s: GherkinStep => s }.toSeq
      ScStepRename.plan(d, name, steps).left.foreach(conflicts.putValue(element, _))
    }
  }

  override def renameElement(element: PsiElement, name: String, usages: Array[UsageInfo], listener: RefactoringElementListener): Unit = {
    declaration(element).foreach { d =>
      val steps = usages.toSeq.map(_.getElement).collect { case s: GherkinStep => s }.distinct
      val edits = ScStepRename.plan(d, name, steps).fold(message => throw new IncorrectOperationException(message), identity)
      edits.foreach { case (step, wording) => step.setName(wording) }
      ScStepRename.setDefinition(d, name)
      if (listener != null) listener.elementRenamed(element)
    }
  }
}
