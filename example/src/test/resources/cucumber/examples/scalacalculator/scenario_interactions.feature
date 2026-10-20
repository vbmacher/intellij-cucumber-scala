@interactions
Feature: Background usages and tagged Examples selections
  Background:
    When I clear the operands

  Rule: Small and large additions
    Background:
      Given I enter 10

    @addition
    Scenario Outline: Choose an Examples block by tag
      When I add <left> and <right>
      Then the result is <result>

      @small
      Examples: Small additions
        | left | right | result |
        | 1    | 2     | 3      |

      @large
      Examples: Large additions
        | left | right | result |
        | 100  | 200   | 300    |

  Rule: Another starting value
    Background:
      Given I enter 20

    Scenario Outline: Compare identical step bodies with different context
      When I add <left> and <right>
      Then the result is <result>

      Examples:
        | left | right | result |
        | 4    | 5     | 9      |
