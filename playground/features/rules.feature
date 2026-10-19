Feature: Rules
  Scenarios grouped under Rule blocks, each with its own Background.

  Background:
    Given a calculator

  Scenario: Scenario before any Rule
    Given the number 1
    When I add them
    Then the result is 1

  Rule: Addition
    Background:
      Given the number 10

    Scenario: Scenario inside a Rule
      Given the number 5
      When I add them
      Then the result is 15

    Scenario Outline: Outline inside a Rule
      Given the number <n>
      When I add them
      Then the result is <sum>

      Examples:
        | n | sum |
        | 1 | 11  |
        | 2 | 12  |

  Rule: Multiplication

    Example: Example inside a second Rule
      Given the number 3
      And the number 4
      When I multiply them
      Then the result is 12
