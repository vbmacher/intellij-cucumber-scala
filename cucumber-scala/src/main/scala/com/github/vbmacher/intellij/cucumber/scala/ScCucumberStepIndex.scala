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

  val getVersion = 6

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
      LightTreeUtil.getChildrenOfType(lighterAst, node, ScalaElementType.METHOD_CALL)
    }

    val rootCalls = getMethodCalls(lighterAst.getRoot)
    rootCalls.forEach(q.offer)

    while (!q.isEmpty) {
      val current = q.poll()

      val methodAndArguments = lighterAst.getChildren(current)
      if (methodAndArguments.size() < 2) {
        val children = getMethodCalls(current)
        children.forEach(q.offer)
      } else {
        val gherkinMethod = methodAndArguments.get(0)
        if (gherkinMethod != null && isStepDefinitionCall(gherkinMethod, text)) {
          val expression = methodAndArguments.get(1)
          if (expression.getTokenType == ScalaElementType.ARG_EXPRS) {
            result.add(current.getStartOffset)
          }
        }
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