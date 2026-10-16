package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.{ScCucumberExtension, ScCucumberSpecBase, StepDeclaration, StepDeclarationSearcher}
import com.intellij.openapi.project.DumbService
import com.intellij.pom.PomTarget
import com.intellij.testFramework.DumbModeTestUtils
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class DumbModeNavigationSpec extends ScCucumberSpecBase {

  private def extension: ScCucumberExtension =
    CucumberJvmExtensionPoint.EP_NAME.getExtensionList.asScala.collectFirst { case e: ScCucumberExtension => e }
      .getOrElse(fail("Expected the Cucumber Scala extension"))

  @Test
  def testStepLookupWaitsForIndexes(): Unit = {
    myFixture.configureByFiles(
      getTestDataPath + "/resolveDirect/testcase.feature",
      getTestDataPath + "/resolveDirect/StepDefinitions.scala"
    )

    DumbModeTestUtils.runInDumbModeSynchronously(getProject, () => inReadAction {
      assert(DumbService.isDumb(getProject))
      assert(extension.loadStepsFor(myFixture.getModule).isEmpty)
    })

    inReadAction {
      assert(!extension.loadStepsFor(myFixture.getModule).isEmpty)
    }
  }

  @Test
  def testCachedStepsAreUnavailableDuringIndexing(): Unit = {
    loadTestCase("resolveDirect/testcase.feature", "resolveDirect/StepDefinitions.scala")
    val steps = inReadAction(extension.loadStepsFor(myFixture.getModule))
    assert(!steps.isEmpty)
    val reference = inReadAction {
      val step = myFixture.findElementByText("I add 4 and 5", classOf[GherkinStep])
      val ref = step.getReferences.collectFirst { case r: CucumberStepReference => r }
        .getOrElse(fail("Expected a Cucumber step reference"))
      assert(ref.resolve().getContainingFile.getName == "StepDefinitions.scala")
      ref
    }

    DumbModeTestUtils.runInDumbModeSynchronously(getProject, () => inReadAction {
      assert(DumbService.isDumb(getProject))
      assert(extension.loadStepsFor(myFixture.getModule).isEmpty)
      assert(reference.multiResolve(false).isEmpty)
    })

    inReadAction {
      assert(extension.loadStepsFor(myFixture.getModule).size() == steps.size())
      assert(reference.resolve().getContainingFile.getName == "StepDefinitions.scala")
    }
  }

  @Test
  def testScalaNavigationTargetsWaitForIndexes(): Unit = {
    loadTestCase("usages/StepDefinitions.scala")
    val leaf = myFixture.getFile.findElementAt(myFixture.getCaretOffset)
    val searcher = new StepDeclarationSearcher

    def targets(): Seq[PomTarget] = {
      val result = ArrayBuffer.empty[PomTarget]
      searcher.findDeclarationsAt(leaf, 0, (target: PomTarget) => { result += target; () })
      result.toSeq
    }

    val declaration = inReadAction {
      val found = targets()
      assert(found.size == 1)
      val target = found.head.asInstanceOf[StepDeclaration]
      assert(target.canNavigate)
      target
    }

    DumbModeTestUtils.runInDumbModeSynchronously(getProject, () => inReadAction {
      assert(DumbService.isDumb(getProject))
      assert(targets().isEmpty)
      assert(!declaration.canNavigate)
      assert(!declaration.canNavigateToSource)
    })

    inReadAction {
      assert(targets().size == 1)
      assert(declaration.canNavigate)
    }
  }
}
