package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberStepIndex.INDEX_ID
import com.github.vbmacher.intellij.cucumber.scala.ScCucumberUtil.ALL_STEP_KEYWORDS
import com.intellij.lang.{LighterAST, LighterASTNode}
import com.intellij.openapi.util.io.DataInputOutputUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.util.indexing._
import com.intellij.util.io.{DataExternalizer, InlineKeyDescriptor, KeyDescriptor}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType

import java.io.{DataInput, DataOutput}
import java.util
import scala.jdk.CollectionConverters._

class ScCucumberStepIndex extends FileBasedIndexExtension[Boolean, Seq[Int]] {
  val getName: ID[Boolean, Seq[Int]] = INDEX_ID

  val getVersion = 7

  val getInputFilter: FileBasedIndex.InputFilter = new DefaultFileTypeSpecificInputFilter(ScalaFileType.INSTANCE) {
    override def acceptInput(file: VirtualFile): Boolean = {
      super.acceptInput(file)
    }
  }

  override def dependsOnFileContent(): Boolean = true

  val getIndexer: DataIndexer[Boolean, Seq[Int], FileContent] = {
    inputData => {
      val text = inputData.getContentAsText
      val lighterAst = inputData.asInstanceOf[PsiDependentFileContent].getLighterAST
      val result = this.getStepDefinitionOffsets(lighterAst, text)

      Map(true -> result).asJava
    }
  }

  override val getKeyDescriptor: KeyDescriptor[Boolean] = new InlineKeyDescriptor[Boolean] {
    override def fromInt(n: Int): Boolean = n != 0

    override def toInt(t: Boolean): Int = if (t) 1 else 0

    override def isCompactFormat: Boolean = true
  }


  val getValueExternalizer: DataExternalizer[Seq[Int]] = new DataExternalizer[Seq[Int]]() {
    override def save(out: DataOutput, value: Seq[Int]): Unit = {
      DataInputOutputUtilRt.writeSeq[Int](out, value.asJava, descriptor => {
        DataInputOutputUtilRt.writeINT(out, descriptor)
      })
    }

    override def read(in: DataInput): Seq[Int] = {
      DataInputOutputUtilRt.readSeq(in, () => DataInputOutputUtilRt.readINT(in)).asScala.toSeq
    }
  }

  private def getStepDefinitionOffsets(lighterAst: LighterAST, text: CharSequence): Seq[Int] = {
    val q = new util.ArrayDeque[LighterASTNode]()
    val result = new util.ArrayList[Int]()

    def getMethodCalls(node: LighterASTNode) = {
      LightTreeUtil.getChildrenOfType(lighterAst, node, ScalaElementType.METHOD_CALL).asScala
    }

    def getClassLikeDefinitions(parent: LighterASTNode) = {
      LightTreeUtil.getChildrenOfType(lighterAst, parent, ScalaElementType.ClassDefinition).asScala ++
        LightTreeUtil.getChildrenOfType(lighterAst, parent, ScalaElementType.TraitDefinition).asScala ++
        LightTreeUtil.getChildrenOfType(lighterAst, parent, ScalaElementType.ObjectDefinition).asScala
    }

    val rootClasses = {
      val root = lighterAst.getRoot
      val directClasses = getClassLikeDefinitions(root)

      // Also look inside PACKAGING nodes (for files with package declarations)
      val packagingNodes = LightTreeUtil.getChildrenOfType(lighterAst, root, ScalaElementType.PACKAGING).asScala
      val packagedClasses = packagingNodes.flatMap(getClassLikeDefinitions)

      directClasses ++ packagedClasses
    }

    val templateBodies = rootClasses
      .flatMap(c => LightTreeUtil.getChildrenOfType(lighterAst, c, ScalaElementType.EXTENDS_BLOCK).asScala)
      .flatMap(c => LightTreeUtil.getChildrenOfType(lighterAst, c, ScalaElementType.TEMPLATE_BODY).asScala)

    templateBodies.flatMap(c => getMethodCalls(c)).foreach(q.offer)


    while (!q.isEmpty) {
      val current = q.poll()

      val children = lighterAst.getChildren(current)

      // Find the first METHOD_CALL child (inner method call) and check if it's a step definition call
      val innerMethodCall = children.asScala.find(_.getTokenType == ScalaElementType.METHOD_CALL)
      val hasArgExprs = children.asScala.exists(_.getTokenType == ScalaElementType.ARG_EXPRS)

      innerMethodCall match {
        case Some(inner) if hasArgExprs =>
          // This is an outer method call like: When("regex") { body }
          // Check if the inner method call's first child is a step keyword reference
          val innerChildren = lighterAst.getChildren(inner)
          val keyword = innerChildren.asScala.headOption
          if (keyword.exists(k => isStepDefinitionCall(k, text))) {
            result.add(current.getStartOffset)
          }

        case _ =>
          // No matching pattern, try to find METHOD_CALL children deeper
          getMethodCalls(current).foreach(q.offer)
      }
    }

    result.asScala.toSeq
  }


  private def isStepDefinitionCall(methodName: LighterASTNode, text: CharSequence) = {
    ALL_STEP_KEYWORDS.contains(text.subSequence(methodName.getStartOffset, methodName.getEndOffset).toString)
  }
}

object ScCucumberStepIndex {
  final val INDEX_ID: ID[Boolean, Seq[Int]] = ID.create("scala.cucumber.step")
}