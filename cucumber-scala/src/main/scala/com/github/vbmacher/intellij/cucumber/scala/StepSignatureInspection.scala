package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import io.cucumber.cucumberexpressions.ExpressionFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._

import scala.jdk.CollectionConverters._
import scala.util.Try

/** Runtime expression arguments are authoritative; unknown conversions deliberately have no type verdict. */
class StepSignatureInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {
    override def visitElement(element: PsiElement): Unit = element match {
      case call: ScMethodCall if call.definition.nonEmpty =>
        StepSignatureInspection.problems(call).foreach { case (target, message) => holder.registerProblem(target, message) }
      case _ => ()
    }
  }
}

object StepSignatureInspection {
  private val builtins = Map("int" -> "scala.Int", "byte" -> "scala.Byte", "short" -> "scala.Short", "long" -> "scala.Long",
    "float" -> "scala.Float", "double" -> "scala.Double", "bigdecimal" -> "java.math.BigDecimal", "biginteger" -> "java.math.BigInteger",
    "word" -> "java.lang.String", "string" -> "java.lang.String")

  def problems(call: ScMethodCall): Seq[(PsiElement, String)] = {
    val module = Option(ModuleUtilCore.findModuleForPsiElement(call))
    val pattern = call.pattern.getOrElse(return Seq.empty)
    val registry = ScParameterTypes.registry(module, snippetsOnly = false)
    val expression = Try(new ExpressionFactory(registry).createExpression(pattern)).toOption.getOrElse(return Seq.empty)
    val entries = module.toSeq.flatMap(m => SuiteCatalog.entries(call.getProject, m.getModuleWithDependenciesAndLibrariesScope(true)))
      .filter(_.definitions.exists(_.getElement == call.getFirstChild))
    val observed = entries.flatMap { entry =>
      Try(Option(expression.`match`(entry.step.getSubstitutedName))).toOption.flatten.map { arguments =>
        val trailing = if (entry.step.getTable != null || entry.step.getPystring != null) 1 else 0
        (arguments.size() + trailing, arguments.asScala.map(_.getType.getTypeName).toSeq,
          if (entry.step.getPystring != null) Some("java.lang.String") else None)
      }
    }
    // Cucumber expressions have explicit named arguments, including escaped literals and optional text.
    // Regex capture nesting/type inference is delegated to the runtime matcher on observed feature text.
    val named = if (org.jetbrains.plugins.cucumber.CucumberUtil.isCucumberExpression(pattern)) {
      val names = "(?<!\\\\)\\{([^{}]*)\\}".r.findAllMatchIn(pattern).map(_.group(1)).toSeq
      val custom = module.toSeq.flatMap(ScParameterTypes.registrations).groupBy(_.name).collect {
        case (name, registrations) if registrations.map(_.resultType).distinct.size == 1 => name -> registrations.head.resultType
      }
      Some(names.map(n => builtins.get(n).orElse(custom.get(n).flatten)))
    } else None
    val arities = observed.map(_._1).distinct
    val expectedArity = if (arities.size == 1) arities.headOption else if (observed.isEmpty) named.map(_.size).filter(n => call.parameters.size != n + 1) else None
    val parameters = call.parameters
    val countProblems = expectedArity.filter(_ != parameters.size).toSeq.map { expected =>
      (call.getFirstChild: PsiElement) -> s"Step supplies $expected arguments; Scala lambda declares ${parameters.size}"
    }
    if (countProblems.nonEmpty) countProblems
    else {
      // Regex conversions receive lambda type hints at runtime; a match without those hints cannot prove a type mismatch.
      // DocString/DataTable conversions may also be registered independently, so only their observed arity is checked.
      val types = named.getOrElse(Seq.fill(observed.headOption.map(_._2.size).getOrElse(0))(None))
      parameters.zip(types).flatMap { case (parameter, expectedName) =>
        for {
          name <- expectedName.toSeq
          expected <- ScalaPsiElementFactory.createTypeFromText(name, call, call).toSeq
          actual <- parameter.`type`().toOption.toSeq
          if actual.extractClass.nonEmpty || actual.isPrimitive
          if !expected.conforms(actual)
        } yield (parameter: PsiElement) -> s"Step argument expects $name; lambda declares ${actual.canonicalText}"
      }
    }
  }
}
