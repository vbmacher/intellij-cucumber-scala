package com.github.vbmacher.intellij.cucumber.scala.psi

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil.{CUCUMBER_PACKAGES, isKeywordValid}
import com.github.vbmacher.intellij.cucumber.scala.psi.ScalaUtil.{getPackageName, innerMethod}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr, ScExpression, ScFunctionExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

// This is used for finding step DEFINITIONS. So not the text in Gherkin files, but the definitions in actual Scala code.
object StepDefinition {

  object implicits {

    implicit class ScMethodCallExt(stepDefinition: ScMethodCall) {
      private lazy val evaluator = new ScalaConstantExpressionEvaluator()
      private lazy val stepDefinitionInnerMethod = innerMethod(stepDefinition)

      def getRegex: Option[String] = {
        for {
          inner <- stepDefinitionInnerMethod
          expression <- inner.args.exprs.headOption
          literal <- Option(evaluator.computeConstantExpression(expression, throwExceptionOnOverflow = false))
        } yield literal.toString
      }

      def getName: Option[String] = {
        for {
          inner <- stepDefinitionInnerMethod
          keywordExpression <- Option(PsiTreeUtil.findChildOfType(inner, classOf[ScReferenceExpression]))
          keyword = keywordExpression.getText.trim

          if isKeywordValid(keyword)
        } yield keyword + " " + getRegex
      }

      def getArguments: Seq[ScParameter] = {
        (for {
          block <- stepDefinition.args.exprs.collectFirst({ case b: ScBlockExpr => b })
          function <- block.getChildren.collectFirst({ case f: ScFunctionExpr => f })

          parameters = function.parameters
        } yield parameters).getOrElse(Seq.empty)
      }

      def isStepDefinition: Boolean = {
        val maybePackageName = for {
          inner <- stepDefinitionInnerMethod
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

        maybePackageName.forall(pkg => CUCUMBER_PACKAGES.exists(pkg.startsWith)) && getName.nonEmpty
      }
    }
  }

  def fromLeafPsiElement(element: LeafPsiElement): Option[ScMethodCall] = {
    for {
      expr <- Option(element.getParent)
      if expr.isInstanceOf[ScReferenceExpression]

      inner <- Option(expr.getParent)
      outer <- Option(inner.getParent)

      if isStepDefinition(outer)
    } yield outer.asInstanceOf[ScMethodCall]
  }

  def fromScExpression(expression: ScExpression): Option[ScMethodCall] = {
    for {
      arguments <- Option(PsiTreeUtil.getParentOfType(expression, classOf[ScArgumentExprList]))
      innerMethod <- Option(arguments.getParent)
      outerMethod <- Option(innerMethod.getParent)

      if isStepDefinition(outerMethod)
    } yield outerMethod.asInstanceOf[ScMethodCall]
  }

  def fromIndexedElement(element: PsiElement): Option[ScMethodCall] = {
    for {
      inner <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall]))
      outer <- Option(inner.getParent)
      if outer.isInstanceOf[ScMethodCall]
    } yield outer.asInstanceOf[ScMethodCall]
  }


  private def isStepDefinition(candidate: PsiElement): Boolean = {
    candidate match {
      case sc: ScMethodCall =>
        import implicits._
        sc.isStepDefinition
      case pt: PomNamedTarget if pt.isInstanceOf[ScStepDefinition] => true
      case _ => false
    }
  }
}
