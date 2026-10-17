package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.debug.{FeatureBreakpointType, FeatureBreakpoints, FeatureDebugHighlight, FeatureJavaBreakpoint}
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.{XDebugSession, XDebugSessionListener}
import com.intellij.xdebugger.impl.BreakpointManagerState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class FeatureBreakpointsSpec extends ScCucumberSpecBase {
  @Test
  def testSharedStepFiltersFeatureAndExampleContext(): Unit = {
    FeatureBreakpoints.matches("file:///suite/a.feature", 4, Some(9), "file:/suite/a.feature", 4, 9) shouldBe true
    FeatureBreakpoints.matches("file:///suite/a.feature", 4, Some(9), "file:/suite/a.feature", 4, 10) shouldBe false
    FeatureBreakpoints.matches("file:///suite/a.feature", 4, None, "file:/suite/b.feature", 4, 9) shouldBe false
    FeatureBreakpoints.matches("file:///suite/a.feature", 4, None, "file:/suite/a.feature", 5, 9) shouldBe false
  }

  @Test
  def testOutlineRowMapsAllStepsAndAmbiguityIsExplicit(): Unit = {
    myFixture.addFileToProject("DebugSteps.scala", "class DebugSteps { Given(\"value {int}\") { (value: Int) => println(value) } }")
    val file = myFixture.configureByText("debug.feature", "Feature: Debug\n  Scenario Outline: Debug\n    Given value <n>\n    Examples:\n      | n |\n      | 1 |\n      | 2 |\n").asInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile]
    ScCucumber.inReadAction {
      val targets = FeatureBreakpoints.targets(file, 5).toOption.get
      targets.size shouldBe 1
      targets.head.exampleLine shouldBe Some(6)
      targets.head.stepLine shouldBe 3
      targets.head.implementation.getFile.getName shouldBe "DebugSteps.scala"
    }
    myFixture.addFileToProject("Duplicate.scala", "class Duplicate { Given(\"value {int}\") { (value: Int) => () } }")
    ScCucumber.inReadAction {
      FeatureBreakpoints.targets(file, 5).left.toOption.get should include("ambiguous")
    }
  }

  @Test
  def testBreakpointPsiCanBeReadOnDebuggerThread(): Unit = {
    myFixture.addFileToProject("DebugSteps.scala", "class DebugSteps { Given(\"value {int}\") { (value: Int) => println(value) } }")
    val file = myFixture.configureByText("debug.feature", "Feature: Debug\n  Scenario: Debug\n    Given value 1\n")
    val manager = XDebuggerManager.getInstance(getProject).getBreakpointManager
    val breakpointType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(classOf[FeatureBreakpointType])
    val breakpoint = manager.addLineBreakpoint(breakpointType, file.getVirtualFile.getUrl, 2,
      breakpointType.createBreakpointProperties(file.getVirtualFile, 2))
    try {
      val javaBreakpoint = new FeatureJavaBreakpoint(getProject, breakpoint)
      val read = ApplicationManager.getApplication.executeOnPooledThread(new java.util.concurrent.Callable[Boolean] {
        override def call(): Boolean = {
          val valid = javaBreakpoint.isValid
          val psiClass = javaBreakpoint.getPsiClass
          val element = javaBreakpoint.getEvaluationElement
          ScCucumber.inReadAction {
            valid && psiClass.getName == "DebugSteps" && element.getContainingFile.getName == "DebugSteps.scala"
          }
        }
      })
      com.intellij.testFramework.PlatformTestUtil.waitForFuture(read) shouldBe true
    } finally manager.removeBreakpoint(breakpoint)
  }

  @Test
  def testUnresolvedStepsAndExamplesHeadersRejectBreakpoints(): Unit = {
    val file = myFixture.configureByText("debug.feature", "Feature: Debug\n  Scenario Outline: Debug\n    Given missing <n>\n    Examples:\n      | n |\n      | 1 |\n").asInstanceOf[org.jetbrains.plugins.cucumber.psi.GherkinFile]
    ScCucumber.inReadAction {
      FeatureBreakpoints.targets(file, 2).left.toOption.get should include("undefined/ambiguous")
      FeatureBreakpoints.targets(file, 5).left.toOption.get should include("undefined/ambiguous")
      FeatureBreakpoints.targets(file, 4).left.toOption.get should include("Choose a Gherkin step")
      FeatureBreakpoints.targets(file, -1).isLeft shouldBe true
    }
  }

  @Test
  def testRestoredBreakpointHasJavaPropertiesAndLeavesScalaConditionUntouched(): Unit = {
    val file = myFixture.configureByText("debug.feature", "Feature: Debug\n  Scenario: Debug\n    Given missing\n")
    val scalaFile = myFixture.addFileToProject("DebugSteps.scala", "class DebugSteps { val value = 1 }")
    val manager = XDebuggerManager.getInstance(getProject).getBreakpointManager.asInstanceOf[XBreakpointManagerImpl]
    val breakpointType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(classOf[FeatureBreakpointType])
    val javaType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(classOf[com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType])
    val scalaBreakpoint = manager.addLineBreakpoint(javaType, scalaFile.getVirtualFile.getUrl, 0,
      javaType.createBreakpointProperties(scalaFile.getVirtualFile, 0))
    scalaBreakpoint.setCondition("value == 999")
    val featureBreakpoint = manager.addLineBreakpoint(breakpointType, file.getVirtualFile.getUrl, 2,
      breakpointType.createBreakpointProperties(file.getVirtualFile, 2))
    val state = manager.saveState(new BreakpointManagerState)
    manager.loadState(state)
    val restored = manager.getBreakpoints(breakpointType).iterator().next()
    try {
      restored.getProperties should not be null
      new FeatureJavaBreakpoint(getProject, restored).isCountFilterEnabled shouldBe false
      restored.setEnabled(false)
      restored.setEnabled(true)
      manager.removeBreakpoint(restored)
      manager.getBreakpoints(javaType).iterator().next().getConditionExpression.getExpression shouldBe "value == 999"
    } finally {
      import scala.jdk.CollectionConverters._
      manager.getBreakpoints(javaType).asScala.toSeq.foreach(manager.removeBreakpoint(_))
      manager.getBreakpoints(breakpointType).asScala.toSeq.foreach(manager.removeBreakpoint(_))
    }
  }

  @Test
  def testHighlightsAreOwnedBySessionAndCancelledOnResumeOrStop(): Unit = {
    val file = myFixture.configureByText("debug.feature", "Feature: Debug\n  Scenario: Debug\n    Given missing\n")
    def session(): (XDebugSession, scala.collection.mutable.Buffer[XDebugSessionListener]) = {
      val listeners = scala.collection.mutable.Buffer.empty[XDebugSessionListener]
      val proxy = java.lang.reflect.Proxy.newProxyInstance(classOf[XDebugSession].getClassLoader, Array(classOf[XDebugSession]),
        (_: Any, method: java.lang.reflect.Method, args: Array[AnyRef]) => {
          if (method.getName == "addSessionListener") listeners += args(0).asInstanceOf[XDebugSessionListener]
          null
        }).asInstanceOf[XDebugSession]
      (proxy, listeners)
    }
    def lines(): Seq[Int] = myFixture.getEditor.getMarkupModel.getAllHighlighters.toSeq
      .filter(_.getLayer == com.intellij.openapi.editor.markup.HighlighterLayer.SELECTION - 1)
      .map(h => myFixture.getEditor.getDocument.getLineNumber(h.getStartOffset) + 1).sorted
    def dispatch(): Unit = com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val (first, firstListeners) = session()
    val (second, secondListeners) = session()
    val a = new FeatureDebugHighlight(getProject, first)
    val b = new FeatureDebugHighlight(getProject, second)
    a.show(file.getVirtualFile.getUrl, 2, Some(3))
    b.show(file.getVirtualFile.getUrl, 3, None)
    dispatch()
    lines() shouldBe Seq(2, 3, 3)
    firstListeners.foreach(_.beforeSessionResume())
    dispatch()
    lines() shouldBe Seq(3)
    secondListeners.foreach(_.sessionStopped())
    dispatch()
    lines() shouldBe empty
    a.show(file.getVirtualFile.getUrl, 2, None)
    firstListeners.foreach(_.sessionStopped())
    dispatch()
    lines() shouldBe empty
  }
}
