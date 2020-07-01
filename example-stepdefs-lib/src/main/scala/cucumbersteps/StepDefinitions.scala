package cucumbersteps

import io.cucumber.scala.{EN, ScalaDsl}

class StepDefinitions extends ScalaDsl with EN {

  When("""^I add (\d+) and (\d+)$"""){ (arg1: Double, arg2: Double) =>
    println(s"Adding $arg1 and $arg2")
  }

  When("I sub (\\d+)" + " and " + "(\\d+)") {
    (arg1: Double, arg2: Double) =>
      println(s"Sub $arg1 and $arg2")
  }

  When("I div " + (5 + 5) + " by " + (10 - 8)) {
    println(s"Div 10 bt 2")
  }
}
