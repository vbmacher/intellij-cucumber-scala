package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionResultSet}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.inspections.GherkinInspection
import org.jetbrains.plugins.cucumber.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

import scala.jdk.CollectionConverters._

/** Only explicit entry-map reads passed to a resolved constructor are mapped; no case-class naming convention is inferred. */
object DataTableMappings {
  final case class Mapping(result: ScClass, headers: Map[String, ScParameter])
  private[scala] def registrations(module: com.intellij.openapi.module.Module): Seq[Mapping] =
    indexedCalls(module.getProject, stepSearchScope(module), ScCucumberStepIndex.Tables).filter(_.isFromCucumber).flatMap { call =>
      for {
        lambda <- Option(PsiTreeUtil.findChildOfType(call.args, classOf[ScFunctionExpr])).toSeq
        if lambda.parameters.size == 1
        entry = lambda.parameters.head
        entryType <- entry.`type`().toOption.toSeq
        if entryType.canonicalText.contains("Map[") && entryType.canonicalText.contains("String") && !entryType.canonicalText.contains("java.util")
        body <- lambda.result.toSeq.flatMap {
          case block: ScBlock if block.exprs.size == 1 => block.exprs.collect { case c: ScMethodCall => c }
          case c: ScMethodCall => Seq(c)
          case _ => Seq.empty
        }
        resultType <- body.`type`().toOption.toSeq
        result <- resultType.extractClass.toSeq.collect { case c: ScClass => c }
        if (body.getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => ref.bind().exists(_.getActualElement.getNavigationElement == result)
          case _ => false
        })
        if body.args.exprs.size == result.parameters.size && !body.args.exprs.exists(_.isInstanceOf[ScAssignment])
        headers = body.args.exprs.zip(result.parameters).flatMap { case (argument, field) =>
          val reads = (Seq(argument) ++ PsiTreeUtil.findChildrenOfType(argument, classOf[ScMethodCall]).asScala).collect { case c: ScMethodCall => c }.distinct
          reads.flatMap { read =>
            val parameterRead = read.getEffectiveInvokedExpr match {
              case ref: ScReferenceExpression => ref.bind().exists(_.getActualElement == entry) ||
                (ref.refName == "apply" && ref.qualifier.exists { case r: ScReferenceExpression => r.bind().exists(_.getActualElement == entry); case _ => false })
              case _ => false
            }
            if (parameterRead && read.args.exprs.size == 1) constantValue(read.args.exprs.head).collect { case name: String => name -> (field: ScParameter) }.toSeq else Seq.empty
          }
        }
        // Dynamic keys, extra reads and duplicate aliases make the mapping incomplete and suppress diagnostics.
        allReads = PsiTreeUtil.findChildrenOfType(body, classOf[ScMethodCall]).asScala.filter(_.getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => ref.bind().exists(_.getActualElement == entry) || (ref.refName == "apply" && ref.qualifier.exists { case r: ScReferenceExpression => r.bind().exists(_.getActualElement == entry); case _ => false })
          case _ => false
        })
        if headers.nonEmpty && headers.size == allReads.size && headers.map(_._1).distinct.size == headers.size
      } yield Mapping(result, headers.toMap)
    }

  def headers(cell: GherkinTableCell): Map[String, ScParameter] = {
    val row = PsiTreeUtil.getParentOfType(cell, classOf[GherkinTableRow])
    val table = Option(row).flatMap(r => Option(r.getParent).collect { case t: GherkinTable => t })
    if (!table.exists(_.getHeaderRow == row)) return Map.empty
    val resolvedTable = table.get
    com.intellij.psi.util.CachedValuesManager.getCachedValue(resolvedTable, () => com.intellij.psi.util.CachedValueProvider.Result.create(
      resolve(resolvedTable), com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT,
      com.intellij.openapi.roots.ProjectRootManager.getInstance(cell.getProject)))
  }

  private def resolve(tableElement: GherkinTable): Map[String, ScParameter] = {
    val table = Some(tableElement)
    val step = table.flatMap(t => Option(PsiTreeUtil.getParentOfType(t, classOf[GherkinStep])))
    val definitions = step.toSeq.flatMap(matchingSteps)
    if (definitions.size != 1) return Map.empty
    val parameter = definitions.head.getElement match {
      case element => asCall(element.getParent).toSeq.flatMap(_.parameters).lastOption
    }
    val targetClass = parameter.flatMap(_.`type`().toOption).flatMap { tpe =>
      tpe match {
        case p: ParameterizedType if p.typeArguments.size == 1 && p.designator.extractClass.exists(c => Set("scala.collection.immutable.List", "scala.collection.immutable.Seq", "scala.collection.Seq", "java.util.List").contains(c.getQualifiedName)) => p.typeArguments.head.extractClass
        case _ => None
      }
    }
    val mappings = Option(ModuleUtilCore.findModuleForPsiElement(tableElement)).toSeq.flatMap(registrations).filter(m => targetClass.contains(m.result))
    if (mappings.size == 1) mappings.head.headers else Map.empty
  }

  def range(cell: GherkinTableCell): TextRange = {
    val start = cell.getText.indexWhere(!_.isWhitespace)
    if (start < 0) new TextRange(cell.getTextLength, cell.getTextLength)
    else new TextRange(start, cell.getText.reverse.indexWhere(!_.isWhitespace) match { case n => cell.getTextLength - n })
  }
}

class DataTableHeaderReferences extends PsiReferenceContributor {
  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit =
    registrar.registerReferenceProvider(com.intellij.patterns.PlatformPatterns.psiElement(classOf[GherkinTableCell]), new PsiReferenceProvider {
      override def getReferencesByElement(element: PsiElement, context: com.intellij.util.ProcessingContext): Array[PsiReference] = {
        val cell = element.asInstanceOf[GherkinTableCell]
        val mapped = DataTableMappings.headers(cell)
        if (mapped.isEmpty) PsiReference.EMPTY_ARRAY
        else Array(new PsiReferenceBase[GherkinTableCell](cell, DataTableMappings.range(cell), true) {
          override def resolve(): PsiElement = mapped.get(getCanonicalText).orNull
          override def getVariants: Array[AnyRef] = mapped.keys.toSeq.sorted.map(name => LookupElementBuilder.create(name).asInstanceOf[AnyRef]).toArray
        })
      }
    })
}

class DataTableHeaderCompletion extends CompletionContributor {
  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    Option(PsiTreeUtil.getParentOfType(parameters.getPosition, classOf[GherkinTableCell], false)).foreach { cell =>
      DataTableMappings.headers(cell).keys.toSeq.sorted.foreach(name => result.addElement(LookupElementBuilder.create(name).withTypeText("mapped table header")))
    }
  }
}

class DataTableHeaderInspection extends GherkinInspection {
  override def buildVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor = new GherkinElementVisitor {
    override def visitGherkinTableCell(cell: GherkinTableCell): Unit = {
      val headers = DataTableMappings.headers(cell)
      val name = DataTableMappings.range(cell).substring(cell.getText)
      if (headers.nonEmpty && !headers.contains(name)) holder.registerProblem(cell, "Unknown mapped header; expected " + headers.keys.toSeq.sorted.mkString(", "))
    }
  }
}
