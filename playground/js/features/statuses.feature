Feature: Result statuses
  One scenario per cucumber-js result status.

  Scenario: Passed
    Given a passing step

  Scenario: Failed assertion
    Given a passing step
    Then this step fails
    And a passing step

  Scenario: Failed with a plain error
    Then this step throws a plain error

  Scenario: Pending
    Given a passing step
    Then this step is pending
    And a passing step

  Scenario: Undefined
    Given a passing step
    Then this step has no definition

  Scenario: Ambiguous
    Then an ambiguous step

  Scenario: Skipped
    Given a passing step
    Then this step is skipped

  Scenario: Slow
    Then this step takes 1500 ms
