Feature: Feature in a subdirectory
  Checks that test locations resolve for features outside the root features directory.

  Scenario: Nested scenario
    Given the number 2
    When I add them
    Then the result is 2
