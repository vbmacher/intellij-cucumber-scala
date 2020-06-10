import io.cucumber.scala.ScalaDsl

class StepDefs2 extends ScalaDsl {
  val calc = new Calculator

  Then("^the result is ([+-]?\\d+)$") { expected: Double =>
    assertEquals(expected, calc.value, 0.001)
  }
}