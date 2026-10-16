package com.github.vbmacher.intellij.cucumber.scala.run

import com.github.vbmacher.intellij.cucumber.scala._
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
}
