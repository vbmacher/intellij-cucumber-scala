Feature: Basic Arithmetic

  Scenario: Adding
    When I add 4 and 5
    Then the result is 9

  Scenario: Subtracting
    When I sub 4 and 5
    Then the result is -1

  Scenario: Dividing
    When I div 10 by 2
    Then the result is 5
    And nothing else
    And Some 55 parameter with 3.14
