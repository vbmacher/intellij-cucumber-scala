package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.openapi.module.Module
import com.intellij.psi.util.PsiTreeUtil
import io.cucumber.cucumberexpressions.{CaptureGroupTransformer, ParameterType, ParameterTypeRegistry}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScMethodCall}

import java.lang.reflect.Type
import java.util.Locale

object ScParameterTypes {
  final case class Registration(name: String, regex: String, resultType: Option[String], call: ScMethodCall)

  def registrations(module: Module): Seq[Registration] =
    indexedCalls(module.getProject, stepSearchScope(module), kind = ScCucumberStepIndex.Parameters).flatMap { call =>
      call.parameterType.map { case (name, regex) =>
        val resultType = Option(PsiTreeUtil.findChildOfType(call.args, classOf[ScFunctionExpr]))
          .flatMap(_.result).flatMap(_.`type`().toOption).map(_.canonicalText)
          .filterNot(Set("_root_.scala.Nothing", "_root_.scala.Any"))
        Registration(name, regex, resultType, call)
      }
    }

  def registry(module: Option[Module], snippetsOnly: Boolean): ParameterTypeRegistry = {
    val registry = new ParameterTypeRegistry(Locale.ENGLISH)
    module.toSeq.flatMap(registrations).groupBy(_.name).toSeq.sortBy(_._1).foreach { case (name, values) =>
      // Conflicting registrations have no statically unique runtime meaning.
      val distinct = values.distinctBy(r => (r.regex, r.resultType))
      if (distinct.size == 1 && (!snippetsOnly || distinct.head.resultType.nonEmpty)) {
        val registration = distinct.head
        val resultType = new Type { override def getTypeName: String = registration.resultType.getOrElse("unknown") }
        registry.defineParameterType(new ParameterType[AnyRef](name, java.util.Collections.singletonList(registration.regex), resultType,
          new CaptureGroupTransformer[AnyRef] { override def transform(args: Array[String]): AnyRef = null }, true, false))
      }
    }
    registry
  }
}
