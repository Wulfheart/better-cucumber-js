@calculator
Feature: Plain scenarios
  Every kind of scenario without Examples.

  Background:
    Given a calculator

  Scenario: Add two numbers
    Given the number 1
    And the number 2
    When I add them
    Then the result is 3

  Example: Example keyword is an alias of Scenario
    Given the number 2
    And the number 3
    When I multiply them
    Then the result is 6

  @smoke @slow
  Scenario: Tagged scenario (the gutter line is the keyword, not the tags)
    Given the number 5
    When I add them
    Then the result is 5

  Scenario: Scenario with a description
    This free text sits between the scenario line and its first step.
    It should not affect the scenario's line number.

    Given the number 7
    When I add them
    Then the result is 7

  Scenario: Data table argument
    Given the numbers:
      | 1 | 2 |
      | 3 | 4 |
    When I add them
    Then the result is 10

  Scenario: Doc string argument
    Given the expression:
      """
      10 + 20 + 12
      """
    When I add them
    Then the result is 42

  Scenario: Scenario without steps of its own

  Scenario: Same name twice
    Given the number 1
    When I add them
    Then the result is 1

  Scenario: Same name twice
    Given the number 2
    When I add them
    Then the result is 2
