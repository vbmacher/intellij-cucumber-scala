package cucumber.examples.scalacalculator

import io.cucumber.scala.EN
import org.junit.Assert.{assertEquals, assertTrue}

trait StepDefinitionsTrait extends ScalaDslIndirection with EN {
  protected val calculator: Calculator = new Calculator

  Before { calculator.clear() }

  // Use the gutter icon or Find Usages to jump back to feature steps.
  Then("the result is {double}") { expected: Double =>
    assertEquals(expected, calculator.value, 0.001)
  }

  Then("the calculator is empty") { () =>
    assertTrue(calculator.isEmpty)
  }
}
