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

  Scenario: WeDividing
    When We divide 10 by 2
    Then the result is 5

  Scenario: IDividing
    When I divide 10 by 2
    Then the result is 5

  Scenario: Moving
    When I move at 10m/s for 10m
    Then the result is 100

  Scenario: NOP
    When I div 10 by 2
    When I do 10 nops
    When I do 10 nop
    Then the result is 5

  Scenario: Weight
    When my weight is 10kg
    When my weight is 10

  Scenario: Escaping
    When I have 42 \{int} cucumbers in my belly \(amazing!)