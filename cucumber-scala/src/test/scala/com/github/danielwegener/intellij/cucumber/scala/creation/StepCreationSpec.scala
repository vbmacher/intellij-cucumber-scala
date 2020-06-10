package com.github.danielwegener.intellij.cucumber.scala.creation

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class StepCreationSpec extends StepCreationSpecBase {

  @Test
  def testNoParameters(): Unit = {
    val files = loadTestCase("creation/noparams.feature", "creation/StepDefinitions.scala")
    checkStepCreation(files(0), files(1),
      """import io.cucumber.scala.ScalaDsl
        |
        |class StepDefinitions extends ScalaDsl {
        |
        |  When("I do anything") { () =>
        |    // Write code here that turns the phrase above into concrete actions
        |  }
        |}""".stripMargin
    )
  }

  @Test
  def testParameters(): Unit = {
    val files = loadTestCase("creation/params.feature", "creation/StepDefinitions.scala")
    checkStepCreation(files(0), files(1),
      """import io.cucumber.scala.ScalaDsl
        |
        |class StepDefinitions extends ScalaDsl {
        |
        |  When("I sub {int} and {int}") { (int1: java.lang.Integer,int2: java.lang.Integer) =>
        |    // Write code here that turns the phrase above into concrete actions
        |  }
        |}""".stripMargin
    )
  }

  @Test
  def testOutline(): Unit = {
    val files = loadTestCase("creation/outline.feature", "creation/StepDefinitions.scala")
    checkStepCreation(files(0), files(1),
      """import io.cucumber.scala.ScalaDsl
        |
        |class StepDefinitions extends ScalaDsl {
        |
        |  When("I add {int} and {int}") { (int1: java.lang.Integer,int2: java.lang.Integer) =>
        |    // Write code here that turns the phrase above into concrete actions
        |  }
        |}""".stripMargin
    )
  }

  @Test
  def testTable(): Unit = {
    val files = loadTestCase("creation/table.feature", "creation/StepDefinitions.scala")
    checkStepCreation(files(0), files(1),
      """import io.cucumber.scala.ScalaDsl
        |
        |class StepDefinitions extends ScalaDsl {
        |
        |  Given("the following data") { (dataTable: io.cucumber.datatable.DataTable) =>
        |    // Write code here that turns the phrase above into concrete actions
        |  }
        |}""".stripMargin
    )
  }
}
