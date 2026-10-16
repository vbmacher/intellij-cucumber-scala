Feature: Navigate between arithmetic steps and Scala definitions
  Use Go to Declaration on a step, then Find Usages on its Scala definition.
  Result assertions are inherited from a trait through an indirect ScalaDsl parent.

  Scenario Outline: Add signed integers using an anchored regular expression
    When I add <left> and <right>
    Then the result is <result>

    Examples:
      | left | right | result |
      | 5    | 6     | 11     |
      | -5   | 6     | 1      |
      | 5    | -6    | -1     |

  Scenario: Navigate to a concatenated step expression
    When I subtract 5 from 4
    Then the result is -1

  Scenario: Navigate to a computed constant expression
    When I divide the defaults 10 by 2
    Then the result is 5

  Scenario Outline: Resolve alternative text to the same definition
    When <subject> divide 10 by 2
    Then the result is 5

    Examples:
      | subject |
      | I       |
      | We      |
