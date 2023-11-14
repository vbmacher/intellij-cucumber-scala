package com.github.vbmacher.intellij.cucumber.scala.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.util.{CachedValuesManager, PsiTreeUtil}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import scala.collection.immutable.ArraySeq.unsafeWrapArray

case class CustomParameterType(name: String, regex: String)

object CustomParameterType {

  private lazy val evaluator = new ScalaConstantExpressionEvaluator()

  /**
    * Finds all custom parameter types (with evaluated regexes) in the given step definition.
    *
    * @param stepDefinition step definition
    * @return all custom parameter types
    */
  def findAll(stepDefinition: ScMethodCall): Seq[CustomParameterType] = {
    val klass = PsiTreeUtil.getParentOfType(stepDefinition, classOf[PsiClass])
    CachedValuesManager.getProjectPsiDependentCache(klass, (context: PsiClass) => {
      find(context, Seq.empty).distinctBy(_.name)
    })
  }

  private def find(klass: PsiClass, processed: Seq[PsiClass]): Seq[CustomParameterType] = {
    val thisClassParamTypes = for {
      body <- Option(PsiTreeUtil.findChildOfAnyType(klass, classOf[ScTemplateBody])).toSeq
      m <- body.getChildren.collect({ case c: ScMethodCall => c })

      mm = m.getFirstChild
      if mm.isInstanceOf[ScMethodCall]

      r = mm.getFirstChild
      if r.isInstanceOf[ScReferenceExpression]
      if r.textMatches("ParameterType")

      args = mm.asInstanceOf[ScMethodCall].args.exprs
      if args.size == 2

      eargs = for {
        arg <- args
        earg <- Option(evaluator.computeConstantExpression(arg, throwExceptionOnOverflow = false)).toSeq
      } yield earg.toString
      if eargs.size == 2

    } yield CustomParameterType(eargs.head, eargs(1))

    val supers = unsafeWrapArray(klass.getSupers.filterNot(processed.contains))
    thisClassParamTypes ++ supers.flatMap(s => find(s, klass +: supers))
  }
}
