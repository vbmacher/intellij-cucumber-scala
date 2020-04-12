@foo
Feature: Basic Arithmetic

  Scenario: Adding
    # Try to change one of the values below to provoke a failure
    When I add 4 and 5
    Then the result is 9

  Scenario: Subtracting
    When I sub 4 and 5
    Then the result is -1

  Scenario: Dividing
    When I div 10 by 2
    Then the result is 5
