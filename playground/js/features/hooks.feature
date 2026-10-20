Feature: Failing hooks
  Tagged Before and After hooks that throw. The plugin shows a failing hook as its own node in the test tree.

  @failing-before
  Scenario: Before hook fails
    Given the number 1
    And the number 2

  @failing-after
  Scenario: After hook fails
    Given the number 1
    And the number 2
