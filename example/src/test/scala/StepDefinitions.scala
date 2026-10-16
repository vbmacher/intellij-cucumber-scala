package cucumber.examples.scalacalculator

import org.junit.Assert.assertEquals

final class StepDefinitions extends StepDefinitionsTrait with CustomTypes {
  private var label: String = ""

  Given("I enter {double}") { value: Double =>
    calculator.push(Val(value))
  }

  // Anchored regexes support captures, including signed numbers.
  When("""^I add (-?\d+) and (-?\d+)$""") { (left: Int, right: Int) =>
    calculate(left, right, "+")
  }

  // The plugin evaluates concatenated constant expressions for navigation.
  When("I subtract {double}" + " from {double}") { (right: Double, left: Double) =>
    calculate(left, right, "-")
  }

  When("I divide the defaults " + (5 + 5) + " by " + (10 - 8)) { () =>
    calculate(10, 2, "/")
  }

  // Both alternatives navigate to this definition.
  When("I/We divide {double} by {double}") { (left: Double, right: Double) =>
    calculate(left, right, "/")
  }

  // Parentheses make the plural suffix optional in a Cucumber expression.
  When("I clear the operand(s)") { () => calculator.clear() }

  When("I move at {speed} for {time}") { (speed: Speed, time: Time) =>
    calculate(speed.kmPerHour, time.hours, "*")
  }

  When("I move at {speed-m-s} for {time-s}") { (speed: Speed, time: Time) =>
    calculate(speed.kmPerHour, time.hours, "*")
  }

  Given("the calculator label is {string}") { text: String => label = text }

  // Escaped braces and parentheses match literal characters.
  Then("""the label reads \{result} \(preview)""") { () =>
    assertEquals("{result} (preview)", label)
  }

  private def calculate(left: Double, right: Double, operator: String): Unit = {
    calculator.push(Val(left))
    calculator.push(Val(right))
    calculator.push(Op(operator))
  }
}
