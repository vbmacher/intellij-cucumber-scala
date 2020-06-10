
class StepDefinitions extends ScalaDslIndirection {
  val calc = new Calculator

  When("""^I add (\d+) and (\d+)$"""){ (arg1: Double, arg2: Double) =>
    calc push arg1
    calc push arg2
    calc push "+"
  }

  When("I sub (\\d+)" + " and " + "(\\d+)") {
    (arg1: Double, arg2: Double) =>
      calc push arg1
      calc push arg2
      calc push "-"
  }

  When("I div " + (5 + 5) + " by " + (10 - 8)) {
    calc push 10.0
    calc push 2.0
    calc push "/"
  }
}