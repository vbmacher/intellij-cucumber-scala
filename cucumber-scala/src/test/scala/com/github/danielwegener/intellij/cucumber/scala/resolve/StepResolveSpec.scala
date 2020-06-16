package com.github.danielwegener.intellij.cucumber.scala.resolve

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepResolveSpec extends StepResolveSpecBase {
  private val checkResolveDirect = singleResolve("resolveDirect/testcase.feature", "resolveDirect/StepDefinitions.scala") _
  private val checkResolveIndirect = singleResolve("resolveIndirect/testcase.feature", "resolveIndirect/StepDefinitions.scala") _

  @Test
  def testResolveSimple(): Unit = {
    checkResolveDirect("And nothing else")
  }

  @Test
  def testWithParameters(): Unit = {
    checkResolveDirect("I add 4 and 5")  // does not include "When", because test regex is within ^$
  }

  @Test
  def testScalaExpressionInName(): Unit = {
    checkResolveDirect("When I div 10 by 2")
  }

  @Test
  def testResolveIndirect(): Unit = {
    checkResolveIndirect("When I div 10 by 2")
  }

  @Test
  def testResolveDirectWithCucumberParameters(): Unit = {
    checkResolveDirect("Some 55 parameter with 3.14")
  }

  @Test
  def testResolveMultipleDefinitions(): Unit = {
    multiResolve(2,
      "resolveMultiple/testcase.feature",
      "resolveMultiple/StepDefs1.scala",
      "resolveMultiple/StepDefs2.scala"
    )("the result is 9")
  }
}