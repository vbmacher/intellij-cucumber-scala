Feature: Match Cucumber expression syntax
  These steps demonstrate built-in parameters, optional text and literal escaping.

  Scenario Outline: Resolve optional plural text
    Given I enter 42.5
    When I clear the <word>
    Then the calculator is empty

    Examples:
      | word     |
      | operand  |
      | operands |

  Scenario: Match quoted strings and escaped braces and parentheses
    Given the calculator label is "{result} (preview)"
    Then the label reads {result} (preview)
