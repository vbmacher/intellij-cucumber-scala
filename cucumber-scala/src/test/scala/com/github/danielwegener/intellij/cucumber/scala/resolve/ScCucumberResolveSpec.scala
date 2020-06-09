package com.github.danielwegener.intellij.cucumber.scala.resolve

import com.github.danielwegener.intellij.cucumber.scala.inReadAction
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalatest.matchers.should.Matchers

@RunWith(classOf[JUnit4])
class ScCucumberResolveSpec extends ScCucumberResolveSpecBase with Matchers {

  @Test
  def testResolveWithParameters(): Unit = {
    loadTestCase("resolve/testcase.feature", "resolve/StepDefinitions.scala")
    inReadAction {
      val definitions = findDefinitions("When I add 4 and 5")
      definitions.size shouldBe 1
    }
  }

  @Test
  def testResolveWithExpression(): Unit = {
    loadTestCase("resolve/testcase.feature", "resolve/StepDefinitions.scala")
    inReadAction {
      val definitions = findDefinitions("When I div 10 by 2")
      definitions.size shouldBe 1
    }
  }

  @Test
  def testResolveBasic(): Unit = {
    loadTestCase("resolve/testcase.feature", "resolve/StepDefinitions.scala")
    inReadAction {
      val definitions = findDefinitions("And nothing else")
      definitions.size shouldBe 1
    }
  }
}