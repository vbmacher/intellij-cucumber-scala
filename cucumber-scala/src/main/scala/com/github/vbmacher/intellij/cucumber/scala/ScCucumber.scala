package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.{CachedValuesManager, PsiTreeUtil}
import com.intellij.psi.{PsiClass, PsiElement, PsiMember}
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

// Step definition: outer call `When("pattern") { (args) => body }` invoking inner call `When("pattern")`
object ScCucumber {

  private val CucumberPackages = Seq("cucumber.api.scala", "io.cucumber.scala")

  private lazy val evaluator = new ScalaConstantExpressionEvaluator()

  def inReadAction[T](body: => T): T =
    ApplicationManager.getApplication.runReadAction(new Computable[T] { override def compute(): T = body })

  def isStepKeyword(keyword: String): Boolean = JsonGherkinKeywordProvider.getKeywordProvider(true).isStepKeyword(keyword)

  private def evaluate(expression: ScExpression): Option[String] =
    Option(evaluator.computeConstantExpression(expression, throwExceptionOnOverflow = false)).map(_.toString)

  private def packageOf(member: PsiMember): Option[String] =
    Option(member.getContainingClass).flatMap(c => Option(c.getQualifiedName)).map(StringUtil.getPackageName)

  implicit class StepDefinitionCall(private val call: ScMethodCall) extends AnyVal {

    private def inner: Option[ScMethodCall] = Option(call.getEffectiveInvokedExpr).collect { case c: ScMethodCall => c }

    def pattern: Option[String] = inner.flatMap(_.args.exprs.headOption).flatMap(evaluate)

    def keyword: Option[String] = inner
      .flatMap(c => Option(PsiTreeUtil.findChildOfType(c, classOf[ScReferenceExpression])))
      .map(_.getText.trim)
      .filter(isStepKeyword)

    /** `(keyword, pattern)` if this is a cucumber step definition */
    def definition: Option[(String, String)] = for {
      k <- keyword
      p <- pattern
      if isFromCucumber
    } yield (k, p)

    def parameters: Seq[ScParameter] = call.args.exprs
      .collectFirst { case block: ScBlockExpr => block }
      .flatMap(_.getChildren.collectFirst { case f: ScFunctionExpr => f.parameters })
      .getOrElse(Seq.empty)

    // Unresolved keywords (e.g. missing library) are accepted, resolved ones must come from cucumber
    private def isFromCucumber: Boolean = inner
      .flatMap(c => Option(c.getEffectiveInvokedExpr))
      .flatMap {
        case MethodValue(method) => packageOf(method)
        case ref: ScReferenceExpression => Option(ref.resolve()).map(_.getNavigationElement).collect {
          case p: ScReferencePattern => p
        }.flatMap(packageOf)
        case _ => None
      }
      .forall(pkg => CucumberPackages.exists(pkg.startsWith))

    def customParameterTypes: Map[String, String] = Option(PsiTreeUtil.getParentOfType(call, classOf[PsiClass]))
      .map(CachedValuesManager.getProjectPsiDependentCache(_, (c: PsiClass) => parameterTypes(c, Set.empty).distinctBy(_._1).toMap))
      .getOrElse(Map.empty)
  }

  private def parameterTypes(klass: PsiClass, visited: Set[PsiClass]): Seq[(String, String)] = {
    val own = for {
      body <- Option(PsiTreeUtil.findChildOfType(klass, classOf[ScTemplateBody])).toSeq
      ScMethodCall(ScMethodCall(ref: ScReferenceExpression, Seq(nameArg, regexArg)), _) <- body.getChildren.toSeq
      if ref.textMatches("ParameterType")
      name <- evaluate(nameArg)
      regex <- evaluate(regexArg)
    } yield name -> regex

    val supers = klass.getSupers.toSeq.filterNot(visited.contains)
    own ++ supers.flatMap(parameterTypes(_, visited + klass ++ supers))
  }

  def asCall(element: PsiElement): Option[ScMethodCall] = element match {
    case c: ScMethodCall => Some(c)
    case _ => None
  }

  def callOfKeyword(leaf: PsiElement): Option[ScMethodCall] = leaf.getParent match {
    case ref: ScReferenceExpression => asCall(ref.getParent.getParent)
    case _ => None
  }

  def callOfPattern(expression: PsiElement): Option[ScMethodCall] =
    Option(PsiTreeUtil.getParentOfType(expression, classOf[ScArgumentExprList])).flatMap(args => asCall(args.getParent.getParent))

  def callOfIndexed(element: PsiElement): Option[ScMethodCall] =
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall])).flatMap(c => asCall(c.getParent))
}
