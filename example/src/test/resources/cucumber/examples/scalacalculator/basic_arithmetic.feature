@foo
Feature: Basic Arithmetic

  Scenario Outline: Adding
    When I add <fst> and <snd>
    Then the result is <result>
    Examples:
      | fst | snd | result |
      | 5   | 6   | 11     |
      | -5  | 6   | 1      |

  Scenario: Subtracting
    When I sub 4 and 5
    Then the result is -1

  Scenario: Dividing
    When I div 10 by 2
    Then the result is 5

  Scenario: WeDividing
    When We divide 10 by 2
    Then the result is 5

  Scenario: IDividing
    When I divide 10 by 2
    Then the result is 5

  Scenario: Moving
    #sadly suggests both, but its better then no suggestion
    # - "I move at {speed} for {time}"
    # - "I move at {speed-m-s} for {time-s}"
    When I move at 10km/h for 10h
    Then the result is 100

  Scenario: Moving meters Per second
    #sadly suggests both, but its better then no suggestion
    # - "I move at {speed} for {time}"
    # - "I move at {speed-m-s} for {time-s}"
    When I move at 10m/s for 10s
    Then the result is 100

  Scenario: NOP
    When I div 10 by 2
    When I do 10 nops
    When I do 10 nop
    Given basic step with string "a"
    And basic step with string "a" suffix
    Then the result is 5