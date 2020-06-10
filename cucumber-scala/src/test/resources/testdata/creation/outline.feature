Feature: Outline

  Scenario Outline: Adding
    When I<caret> add <fst> and <snd>
    Then the result is <result>
    Examples:
      | fst | snd | result |
      | 5   | 6   | 11     |
      | -5  | 6   | 1      |