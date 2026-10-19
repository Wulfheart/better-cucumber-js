Feature: Calculator

  Background:
    Given a calculator

  @smoke
  Scenario: Adding two numbers
    Given I have the number 2
    Then the result is 2

  Scenario Outline: Adding many numbers
    Given I have the number <a>

    Examples:
      | a |
      | 1 |
      | 2 |

  Example: Synonym for scenario
    Given I have the number 3
