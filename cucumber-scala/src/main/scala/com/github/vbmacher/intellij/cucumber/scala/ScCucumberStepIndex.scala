package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.lang.{LighterAST, LighterASTNode}
import com.intellij.openapi.util.io.DataInputOutputUtilRt
import com.intellij.psi.tree.IElementType
import com.intellij.util.indexing._
import com.intellij.util.io.{BooleanDataDescriptor, DataExternalizer, KeyDescriptor}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._

import java.io.{DataInput, DataOutput}
import java.lang.{Boolean => JBoolean}
import scala.jdk.CollectionConverters._

class ScCucumberStepIndex extends FileBasedIndexExtension[JBoolean, Seq[Int]] {

  override def getName: ID[JBoolean, Seq[Int]] = ScCucumberStepIndex.Id

  override def getVersion: Int = 8

  override def dependsOnFileContent(): Boolean = true

  override def getInputFilter: FileBasedIndex.InputFilter = new DefaultFileTypeSpecificInputFilter(ScalaFileType.INSTANCE)

  override def getKeyDescriptor: KeyDescriptor[JBoolean] = BooleanDataDescriptor.INSTANCE

  override def getValueExternalizer: DataExternalizer[Seq[Int]] = new DataExternalizer[Seq[Int]] {
    override def save(out: DataOutput, value: Seq[Int]): Unit =
      DataInputOutputUtilRt.writeSeq[Int](out, value.asJava, DataInputOutputUtilRt.writeINT(out, _))

    override def read(in: DataInput): Seq[Int] =
      DataInputOutputUtilRt.readSeq(in, () => DataInputOutputUtilRt.readINT(in)).asScala.toSeq
  }

  override def getIndexer: DataIndexer[JBoolean, Seq[Int], FileContent] = content => {
    val ast = content.asInstanceOf[PsiDependentFileContent].getLighterAST
    java.util.Map.of(JBoolean.TRUE, stepDefinitionOffsets(ast, content.getContentAsText))
  }

  private def stepDefinitionOffsets(ast: LighterAST, text: CharSequence): Seq[Int] = {
    def children(node: LighterASTNode) = ast.getChildren(node).asScala.toSeq

    def ofType(node: LighterASTNode, types: IElementType*) = children(node).filter(n => types.contains(n.getTokenType))

    def typeDefinitions(node: LighterASTNode) = ofType(node, ClassDefinition, TraitDefinition, ObjectDefinition)

    def stepCalls(call: LighterASTNode): Seq[Int] = {
      val calls = ofType(call, METHOD_CALL)
      calls.headOption match {
        case Some(inner) if ofType(call, ARG_EXPRS).nonEmpty =>
          children(inner).headOption
            .filter(keyword => isStepKeyword(text.subSequence(keyword.getStartOffset, keyword.getEndOffset).toString))
            .map(_ => call.getStartOffset)
            .toSeq
        case _ => calls.flatMap(stepCalls)
      }
    }

    val root = ast.getRoot
    (typeDefinitions(root) ++ ofType(root, PACKAGING).flatMap(typeDefinitions))
      .flatMap(ofType(_, EXTENDS_BLOCK))
      .flatMap(ofType(_, TEMPLATE_BODY))
      .flatMap(ofType(_, METHOD_CALL))
      .flatMap(stepCalls)
  }
}

object ScCucumberStepIndex {
  val Id: ID[JBoolean, Seq[Int]] = ID.create("scala.cucumber.step")
}
