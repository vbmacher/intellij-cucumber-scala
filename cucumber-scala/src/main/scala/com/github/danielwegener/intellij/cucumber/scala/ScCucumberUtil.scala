package com.github.danielwegener.intellij.cucumber.scala

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiMember}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

import scala.annotation.tailrec

object ScCucumberUtil {
  private final val CUCUMBER_SCALA_STEP_DEF_TRAIT = "cucumber.api.scala.ScalaDsl"
  private final val CUCUMBER_SCALA_PACKAGE = "cucumber.api.scala"

  val LOG: Logger = Logger.getInstance(ScCucumberUtil.getClass)

  private final val evaluator = new ScalaConstantExpressionEvaluator()

  def isStepDefinition(candidate: ScMethodCall): Boolean = {
    val packageName = innerMethod(candidate)
      .flatMap(inner => Option(inner.getEffectiveInvokedExpr))
      .flatMap(_ match {
        case MethodValue(method) => getPackageName(method)

        case expression: ScReferenceExpression =>
          val navigable = Option(expression.resolve()).map(_.getNavigationElement)
          navigable match {
            case Some(referencePattern: ScReferencePattern) => getPackageName(referencePattern)
            case _ => None
          }

        case _ => None
      })

    packageName.contains(CUCUMBER_SCALA_PACKAGE) && getStepName(candidate).nonEmpty
  }

  def getStepName(stepDefinition: ScMethodCall): Option[String] = {
    val literals = for {
      innerMethod <- innerMethod(stepDefinition)
      expression <- innerMethod.args.exprs.headOption
      literal <- Option(evaluator.computeConstantExpression(expression, throwExceptionOnOverflow = false))
    } yield literal.toString

    literals
  }

  def getStepDefinitionClasses(searchScope: GlobalSearchScope, project: Project, justClasses: Boolean = false): Seq[ScTypeDefinition] = inReadAction {
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
