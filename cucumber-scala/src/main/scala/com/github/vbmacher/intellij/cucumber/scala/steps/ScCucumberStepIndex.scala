package com.github.vbmacher.intellij.cucumber.scala.steps

import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber._
import com.intellij.lang.{LighterAST, LighterASTNode}
import com.intellij.openapi.util.io.DataInputOutputUtilRt
import com.intellij.psi.tree.IElementType
import com.intellij.util.indexing._
import com.intellij.util.io.{DataExternalizer, EnumeratorStringDescriptor, KeyDescriptor}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._

import java.io.{DataInput, DataOutput}
import scala.jdk.CollectionConverters._

class ScCucumberStepIndex extends FileBasedIndexExtension[String, Seq[Int]] {

  override def getName: ID[String, Seq[Int]] = ScCucumberStepIndex.Id

  override def getVersion: Int = 12

  override def dependsOnFileContent(): Boolean = true

  override def getInputFilter: FileBasedIndex.InputFilter = new DefaultFileTypeSpecificInputFilter(ScalaFileType.INSTANCE)

  override def getKeyDescriptor: KeyDescriptor[String] = EnumeratorStringDescriptor.INSTANCE

  override def getValueExternalizer: DataExternalizer[Seq[Int]] = new DataExternalizer[Seq[Int]] {
    override def save(out: DataOutput, value: Seq[Int]): Unit =
      DataInputOutputUtilRt.writeSeq[Int](out, value.asJava, DataInputOutputUtilRt.writeINT(out, _))

    override def read(in: DataInput): Seq[Int] =
      DataInputOutputUtilRt.readSeq(in, () => DataInputOutputUtilRt.readINT(in)).asScala.toSeq
  }

  override def getIndexer: DataIndexer[String, Seq[Int], FileContent] = content => {
    val ast = content.asInstanceOf[PsiDependentFileContent].getLighterAST
    val text = content.getContentAsText
    // Keep DSL registrations in the same index and search scope.
    Map(
      ScCucumberStepIndex.Steps -> declarationOffsets(ast, text, isStepKeyword),
      ScCucumberStepIndex.Parameters -> declarationOffsets(ast, text, _ == "ParameterType"),
      ScCucumberStepIndex.Hooks -> declarationOffsets(ast, text, ScCucumberStepIndex.HookKeywords),
      ScCucumberStepIndex.Tables -> declarationOffsets(ast, text, _ == "DataTableType"),
      ScCucumberStepIndex.Transformers -> declarationOffsets(ast, text, ScCucumberStepIndex.TransformerKeywords)
    ).filter(_._2.nonEmpty).asJava
  }

  private def declarationOffsets(ast: LighterAST, text: CharSequence, acceptsKeyword: String => Boolean): Seq[Int] = {
    def children(node: LighterASTNode) = ast.getChildren(node).asScala.toSeq

    def ofType(node: LighterASTNode, types: IElementType*) = children(node).filter(n => types.contains(n.getTokenType))

    def typeDefinitions(node: LighterASTNode) = ofType(node, ClassDefinition, TraitDefinition, ObjectDefinition)

    def packagedTypes(node: LighterASTNode): Seq[LighterASTNode] =
      typeDefinitions(node) ++ ofType(node, PACKAGING).flatMap(packagedTypes)

    def stepCalls(call: LighterASTNode): Seq[Int] = {
      def invoked(node: LighterASTNode): Option[LighterASTNode] = children(node).headOption.flatMap { first =>
        if (first.getTokenType == METHOD_CALL) invoked(first) else Some(first)
      }
      invoked(call).filter(keyword => acceptsKeyword(text.subSequence(keyword.getStartOffset, keyword.getEndOffset).toString))
        .map(_ => call.getStartOffset).toSeq
    }

    packagedTypes(ast.getRoot)
      .flatMap(ofType(_, EXTENDS_BLOCK))
      .flatMap(ofType(_, TEMPLATE_BODY))
      .flatMap(ofType(_, METHOD_CALL))
      .flatMap(stepCalls)
  }
}

object ScCucumberStepIndex {
  val Steps = "step"
  val Parameters = "parameter"
  val Hooks = "hook"
  val Tables = "table"
  val Transformers = "transformer"
  val HookKeywords: Set[String] = Set("Before", "After", "BeforeStep", "AfterStep", "BeforeAll", "AfterAll")
  val TransformerKeywords: Set[String] = Set("DocStringType", "DefaultParameterTransformer", "DefaultDataTableCellTransformer", "DefaultDataTableEntryTransformer")
  val Id: ID[String, Seq[Int]] = ID.create("scala.cucumber.step")
}
