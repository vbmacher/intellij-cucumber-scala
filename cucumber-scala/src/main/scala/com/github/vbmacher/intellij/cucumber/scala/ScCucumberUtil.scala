package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.steps.ScStepDefinition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMember}
import io.cucumber.gherkin.GherkinDialectProvider
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFunctionExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import collection.JavaConverters._

object ScCucumberUtil {

  private final val CUCUMBER_4X_SCALA_PACKAGE = "cucumber.api.scala."
  private final val CUCUMBER_5X_SCALA_PACKAGE = "io.cucumber.scala."
  final val CUCUMBER_PACKAGES = Seq(
    CUCUMBER_4X_SCALA_PACKAGE,
    CUCUMBER_5X_SCALA_PACKAGE
  )

  private lazy val evaluator = new ScalaConstantExpressionEvaluator()
  private lazy val allKeywords = {
    val provider = new GherkinDialectProvider()
    val languages = provider.getLanguages.asScala
    val dialects = languages.map(provider.getDialect(_, null))

    dialects.flatMap(_.getStepKeywords.asScala.map(_.trim)).toSet
  }


  def isStepDefinition(candidate: PsiElement): Boolean = candidate match {
    case sc: ScMethodCall =>
    println("  it's a method call!")
      isStepDefinition(sc)
    case pt: PomNamedTarget if pt.isInstanceOf[ScStepDefinition] =>
      println("  it's a pom target")
      true
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

    maybePackageName.forall(pkg => CUCUMBER_PACKAGES.exists(pkg.startsWith)) && getStepName(candidate).nonEmpty
  }

  def getStepName(stepDefinition: ScMethodCall): Option[String] = {
    val literals = for {
      innerMethod <- innerMethod(stepDefinition)

      keyword <- Option(PsiTreeUtil.findChildOfType(innerMethod, classOf[ScReferenceExpression]))
      if isKeywordValid(keyword.getText)

      regex <- getStepRegex(stepDefinition)
    } yield keyword.getText + " " + regex

    literals
  }

  def getStepRegex(stepDefinition: ScMethodCall): Option[String] = {
    val literals = for {
      innerMethod <- innerMethod(stepDefinition)
      expression <- innerMethod.args.exprs.headOption
      literal <- Option(evaluator.computeConstantExpression(expression, throwExceptionOnOverflow = false))
    } yield literal.toString

    literals
  }

  def getStepArguments(stepDefinition: ScMethodCall): Seq[ScParameter] = {
    (for {
      block <- stepDefinition.args.exprs.collectFirst({ case b: ScBlockExpr => b })
      function <- block.getChildren.collectFirst({ case f: ScFunctionExpr => f })

      parameters = function.parameters
    } yield parameters).getOrElse(Seq.empty)
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

  private def isKeywordValid(keyword: String) = allKeywords.contains(keyword)
}
