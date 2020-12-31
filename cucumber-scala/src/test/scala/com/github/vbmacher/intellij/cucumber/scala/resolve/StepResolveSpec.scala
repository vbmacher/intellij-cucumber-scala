package com.github.vbmacher.intellij.cucumber.scala.resolve

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepResolveSpec extends StepResolveSpecBase {
  private val checkSingleOccurrenceDirect =
    singleResolve("resolveDirect/testcase.feature", "resolveDirect/StepDefinitions.scala") _
  private val checkNoOccurrenceDirect =
    multiResolve(0, "resolveDirect/testcase.feature", "resolveDirect/StepDefinitions.scala") _
  private val checkSingleOccurrenceIndirect =
    singleResolve("resolveIndirect/testcase.feature", "resolveIndirect/StepDefinitions.scala") _

  @Test
  def testResolveSimple(): Unit = {
    checkSingleOccurrenceDirect("And nothing else")
  }

  @Test
  def testWithParameters(): Unit = {
    checkSingleOccurrenceDirect("I add 4 and 5") // does not include "When", because test regex is within ^$
  }

  @Test
  def testScalaExpressionInName(): Unit = {
    checkSingleOccurrenceDirect("When I div 10 by 2")
  }

  @Test
  def testResolveIndirect(): Unit = {
    checkSingleOccurrenceIndirect("When I div 10 by 2")
  }

  @Test
  def testResolveDirectWithCucumberParameters(): Unit = {
    checkSingleOccurrenceDirect("Some 55 parameter with 3.14")
  }

  @Test
  def testCustomTypeIsSupported(): Unit = {
    checkSingleOccurrenceDirect("I move at 10km/h for 1h")
  }

  @Test
  def testCustomTypeRegexIsConsidered(): Unit = {
    checkNoOccurrenceDirect("my weight is 10")
    checkSingleOccurrenceDirect("my weight is 10kg")
  }

  @Test
  def testAlternativeTextSupport(): Unit = {
    checkSingleOccurrenceDirect("We divide 10 by 1")
    checkSingleOccurrenceDirect("I divide 10 by 1")
  }

  @Test
  def testOptionalTextSupport(): Unit = {
    checkSingleOccurrenceDirect("I do 10 nop")
    checkSingleOccurrenceDirect("I do 10 nops")
  }

  @Test
  def testResolveMultipleDefinitions(): Unit = {
    multiResolve(
      expectedCount = 2,
      "resolveMultiple/testcase.feature",
      "resolveMultiple/StepDefs1.scala",
      "resolveMultiple/StepDefs2.scala"
    )("the result is 9")
  }
}
