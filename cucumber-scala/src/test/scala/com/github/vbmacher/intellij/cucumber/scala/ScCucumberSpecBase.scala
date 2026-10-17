package com.github.vbmacher.intellij.cucumber.scala

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.getLineMarkers
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.intellij.psi._
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuiteLike

import java.io.File
import scala.jdk.CollectionConverters._

abstract class ScCucumberSpecBase extends BasePlatformTestCase with Matchers with JUnitSuiteLike {
  val DESCRIPTOR = new LibraryLightProjectDescriptor()

  override def getProjectDescriptor: LibraryLightProjectDescriptor = DESCRIPTOR

  override lazy val getTestDataPath = {
    val resource = classOf[ScCucumberSpecBase].getClassLoader.getResource("testdata")
    new File(resource.toURI).getPath.replace(File.separatorChar, '/')
  }

  def loadTestCase(files: String*): Array[PsiFile] = {
    val r = myFixture.configureByFiles(files.map(getTestDataPath + File.separator + _): _*)
    myFixture.doHighlighting()
    r
  }

  def findLineMarkers() = {
    val editor = myFixture.getEditor
    val project = getProject

    myFixture.doHighlighting()

    inReadAction(getLineMarkers(editor.getDocument, project).asScala.toSeq)
  }
}
