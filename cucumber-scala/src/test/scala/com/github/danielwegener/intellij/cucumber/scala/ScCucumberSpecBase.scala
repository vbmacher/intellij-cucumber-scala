package com.github.danielwegener.intellij.cucumber.scala

import java.io.File

import com.intellij.psi._
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.apache.log4j.Logger
import org.junit.{After, Before}
import org.scalatest.funspec.AnyFunSpecLike

abstract class ScCucumberSpecBase extends BasePlatformTestCase with AnyFunSpecLike {
  protected lazy val LOG = Logger.getRootLogger

  val DESCRIPTOR = new LibraryLightProjectDescriptor(
    RemoteDependency("io.cucumber:cucumber-scala_2.12:5.7.0")
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

  def loadTestCase(feature: String, stepDef: String): Array[PsiFile] = {
    myFixture.configureByFiles(
      getTestDataPath + File.separator + feature,
      getTestDataPath + File.separator + stepDef
    )
  }

  def referenceUnderCaret[T <: PsiReference](refType: Class[T]): T = {
    val ref = myFixture.getFile.findReferenceAt(myFixture.getCaretOffset)
    UsefulTestCase.assertInstanceOf(ref, refType)
  }
}
