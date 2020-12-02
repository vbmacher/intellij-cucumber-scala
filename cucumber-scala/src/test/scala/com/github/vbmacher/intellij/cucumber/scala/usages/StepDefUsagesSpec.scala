package com.github.vbmacher.intellij.cucumber.scala.usages

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepDefUsagesSpec extends ScCucumberSpecBase {

  @Test
  def testFindUsages(): Unit = {
    loadTestCase("usages/StepDefinitions.scala", "usages/testcase1.feature", "usages/testcase2.feature")
    val usages = myFixture.testFindUsages(getTestDataPath + "/usages/StepDefinitions.scala")
    assert(usages.size() == 2)
  }
}
