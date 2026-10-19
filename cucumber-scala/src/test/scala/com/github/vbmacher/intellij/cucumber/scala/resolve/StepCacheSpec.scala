package com.github.vbmacher.intellij.cucumber.scala.resolve

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumber.inReadAction
import com.github.vbmacher.intellij.cucumber.scala.steps.ScCucumberExtension
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class StepCacheSpec extends ScCucumberSpecBase {
  @Test
  def testStepLookupAcrossExtensionInstancesRefreshesAfterScalaChanges(): Unit = {
    myFixture.addFileToProject("Steps.scala", "class Steps { Given(\"original step\") { () => () } }")
    val first = new ScCucumberExtension
    val second = new ScCucumberExtension

    inReadAction {
      val steps = first.loadStepsFor(myFixture.getModule)
      steps.asScala.map(_.getExpression).toSeq shouldBe Seq("original step")
      assert(second.loadStepsFor(myFixture.getModule) eq steps)
    }

    myFixture.addFileToProject("MoreSteps.scala", "class MoreSteps { Then(\"another step\") { () => () } }")

    inReadAction {
      val steps = second.loadStepsFor(myFixture.getModule)
      steps.asScala.map(_.getExpression).toSet shouldBe Set("original step", "another step")
      assert(first.loadStepsFor(myFixture.getModule) eq steps)
      assert(steps.asScala.forall(_.getElement.isValid))
    }
  }
}
