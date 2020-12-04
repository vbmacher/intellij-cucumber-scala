class StepDefinitions extends StepDefinitionsTrait with CustomTypes {

  When("""^I add (\d+) and (\d+)$""") { (arg1: Double, arg2: Double) =>
    calc push arg1
    calc push arg2
    calc push "+"
  }

  When("I sub (\\d+)" + " and " + "(\\d+)") { (arg1: Double, arg2: Double) =>
    calc push arg1
    calc push arg2
    calc push "-"
  }

  When("I div " + (5 + 5) + " by " + (10 - 8)) {
    calc push 10.0
    calc push 2.0
    calc push "/"
  }

  When("I move at {speed} for {time}") { (arg1: Speed, arg2: Time) =>
    calc push arg1.kmPerHour
    calc push arg2.hours
    calc push "*"
  }

  When("I move at {speed-m-s} for {time-s}") { (arg1: Speed, arg2: Time) =>
    calc push arg1.kmPerHour
    calc push arg2.hours
    calc push "*"
  }

  When("""I/We divide (\d+) by (\d+)""") { (arg1: Double, arg2: Double) =>
    calc push arg1
    calc push arg2
    calc push "*"
  }

  When("""I do {int} nop(s)""") { (arg1: Int) => (0 to arg1).foreach { _ => calc push "NOP" } }
}
