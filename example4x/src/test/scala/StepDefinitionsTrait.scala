import cucumber.api.scala.EN
import org.junit.Assert.assertEquals

trait StepDefinitionsTrait extends ScalaDslIndirection with EN {

  val calc = new Calculator

  Before("~@foo") {
    scenario => println("Runs before scenarios *not* tagged with @foo")
  }

  Then("^the result is ([+-]?\\d+)$") { expected: Double =>
    assertEquals(expected, calc.value, 0.001)
  }
}
