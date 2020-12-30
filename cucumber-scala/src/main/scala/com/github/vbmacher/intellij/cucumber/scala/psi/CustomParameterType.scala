package com.github.vbmacher.intellij.cucumber.scala.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

case class CustomParameterType(name: String, regex: String)

object CustomParameterType {

  private lazy val evaluator = new ScalaConstantExpressionEvaluator()

  def findAll(stepDefinition: ScMethodCall): Seq[CustomParameterType] = {
    val klass = PsiTreeUtil.getParentOfType(stepDefinition, classOf[PsiClass])
    find(klass, Seq.empty).distinctBy(_.name)
  }

  private def find(klass: PsiClass, processed: Seq[PsiClass]): Seq[CustomParameterType] = {

    println("At: " + klass.getName)

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

    val supers = klass.getSupers.filterNot(processed.contains)
    thisClassParamTypes ++ supers.flatMap(s => find(s, klass +: supers))
  }
}
