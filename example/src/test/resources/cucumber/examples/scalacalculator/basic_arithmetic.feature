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
