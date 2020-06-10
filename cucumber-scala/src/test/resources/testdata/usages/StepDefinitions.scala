import io.cucumber.scala.ScalaDsl

class StepDefinitions extends ScalaDsl {
  val calc = new Calculator

  When("^I<caret> add (\\d+) and (\\d+)$") { (arg1: Double, arg2: Double) =>
    calc push arg1
    calc push arg2
    calc push "+"
  }
}