package com.github.danielwegener.intellij.cucumber.scala

import com.github.danielwegener.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager}
import com.intellij.psi.{JavaPsiFacade, PsiDocumentManager, PsiElement, PsiMember}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

import scala.annotation.tailrec

class ScCucumberUtil
object ScCucumberUtil {
  private final val CUCUMBER_SCALA_STEP_DEF_TRAIT = "cucumber.api.scala.ScalaDsl"
  private final val CUCUMBER_SCALA_PACKAGE = "cucumber.api.scala"

  private final val evaluator = new ScalaConstantExpressionEvaluator()

  def isStepDefinition(candidate: PsiElement): Boolean = candidate match {
    case sc: ScMethodCall => isStepDefinition(sc)
    case pt: PomNamedTarget if pt.isInstanceOf[ScStepDefinition] => true
    case _ => false
  }

  def isStepDefinition(candidate: ScMethodCall): Boolean = {
    val maybePackageName = for {
      inner <- innerMethod(candidate)
      packageName <- Option(inner.getEffectiveInvokedExpr).collect {
        case MethodValue(method) => getPackageName(method)
        case expr: ScReferenceExpression =>
          val navigable = Option(expr.resolve()).map(_.getNavigationElement)
          navigable match {
            case Some(referencePattern: ScReferencePattern) => getPackageName(referencePattern)
            case _ => None
          }
      }.flatten
    } yield packageName

    maybePackageName.contains(CUCUMBER_SCALA_PACKAGE) && getStepName(candidate).nonEmpty
  }

  def getStepName(stepDefinition: ScMethodCall): Option[String] = {
    val literals = for {
      innerMethod <- innerMethod(stepDefinition)
      expression <- innerMethod.args.exprs.headOption
      literal <- Option(evaluator.computeConstantExpression(expression, throwExceptionOnOverflow = false))
    } yield literal.toString

    literals
  }

  def getStepDefinitionClasses(searchScope: GlobalSearchScope, project: Project, justClasses: Boolean = false): Seq[ScTypeDefinition] = {
    val psiFacade = JavaPsiFacade.getInstance(project)

    for {
      cucumberDslClass <- psiFacade.findClasses(CUCUMBER_SCALA_STEP_DEF_TRAIT, searchScope).toSeq
      candidate <- ScalaInheritors.withStableScalaInheritors(cucumberDslClass).collect {
        case sc: ScClass => sc
        case sct: ScTrait if !justClasses => sct
      }
      glueCodeClass <- classAndItsInheritors(candidate)
    } yield glueCodeClass
  }

  def getCachedStepDefinition(statement: ScMethodCall): Option[ScStepDefinition] = {
    Option(CachedValuesManager.getCachedValue(statement, () => {
      val document = Option(statement.getContainingFile)
        .map(PsiDocumentManager.getInstance(statement.getProject).getDocument)

      document
        .map(d => CachedValueProvider.Result.create(ScStepDefinition(statement), d))
        .getOrElse(CachedValueProvider.Result.create(ScStepDefinition(statement)))
    }))
  }

  def getStepDefinitionExpr(stepDefinition: ScMethodCall): Option[ScReferenceExpression] = {
    if (isStepDefinition(stepDefinition)) {
      stepDefinition.getEffectiveInvokedExpr match {
        case ref: ScReferenceExpression => Some(ref)
        case mc: ScMethodCall => mc.getEffectiveInvokedExpr match {
          case r: ScReferenceExpression => Some(r)
          case _ => None
        }
        case _ => None
      }
    } else None
  }


  private def getPackageName(member: PsiMember): Option[String] = {
    for {
      containingClass <- Option(member.getContainingClass)
      qualifiedName <- Option(containingClass.getQualifiedName)
    } yield StringUtil.getPackageName(qualifiedName)
  }

  private def innerMethod(outerMethod: ScMethodCall) = {
    Option(outerMethod.getEffectiveInvokedExpr).collect { case some: ScMethodCall => some }
  }

  private def classAndItsInheritors(parent: ScTypeDefinition): Iterable[ScTypeDefinition] = {

    @tailrec
    def rec(queue: Seq[ScTypeDefinition], accumulator: Set[ScTypeDefinition]): Set[ScTypeDefinition] = {
      queue match {
        case Seq() => accumulator
        case queueHead +: queueTail =>

          val newChildren = ScalaInheritors.findInheritorObjects(queueHead).collect {
            case sc: ScClass => sc
            case sct: ScTrait => sct
          }

          val childrenToExplore = newChildren
            .map(_.asInstanceOf[ScTypeDefinition])
            .diff(accumulator)
            .toSeq

          rec(queueTail ++ childrenToExplore, accumulator + queueHead)
      }
    }

    rec(Seq(parent), Set.empty)
  }
}
