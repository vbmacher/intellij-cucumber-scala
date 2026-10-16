@undefined
Feature: Generate a missing Scala step definition
  This scenario is intentionally undefined and excluded from the runnable examples.
  Place the caret on the missing step and use the Create step definition quick-fix.
  Select the existing Scala step class to try the template and typed parameters.

  Scenario: Generate a step with numeric and quoted string parameters
    Given I enter 42
    When I save the result as "answer"
    Then the result is 42
