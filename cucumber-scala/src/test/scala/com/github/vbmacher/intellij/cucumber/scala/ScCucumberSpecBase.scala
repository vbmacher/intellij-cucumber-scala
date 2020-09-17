package com.github.vbmacher.intellij.cucumber.scala

import java.io.File

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.getLineMarkers
import com.intellij.psi._
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.apache.log4j.Logger
import org.junit.{After, Before}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import collection.JavaConverters._

abstract class ScCucumberSpecBase extends BasePlatformTestCase with AnyFunSpecLike with Matchers {
  protected lazy val LOG = Logger.getRootLogger

  val DESCRIPTOR = new LibraryLightProjectDescriptor(
    //RemoteDependency("io.cucumber:cucumber-scala_2.12:6.1.2")
  )

  @Before
  override def setUp(): Unit = {
    super.setUp()
  }

  @After
  def shutdown(): Unit = super.tearDown()

  override def getProjectDescriptor: LibraryLightProjectDescriptor = DESCRIPTOR

  override lazy val getTestDataPath = {
    val resource = classOf[ScCucumberSpecBase].getClassLoader.getResource("testdata")
    new File(resource.toURI).getPath.replace(File.separatorChar, '/')
  }

  def loadTestCase(files: String*): Array[PsiFile] = {
    myFixture.configureByFiles(files.map(getTestDataPath + File.separator + _): _*)
  }

  def findLineMarkers() = {
    val editor = myFixture.getEditor
    val project = myFixture.getProject

    myFixture.doHighlighting()

    var lineMarkers = collection.mutable.Seq.empty[LineMarkerInfo[_]]
    UsefulTestCase.edt(() => {
      getLineMarkers(editor.getDocument, project).asScala.foreach(lineMarkers +:= _)
    })

    lineMarkers
  }
}
