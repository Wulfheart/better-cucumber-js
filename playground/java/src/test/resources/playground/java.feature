Feature: Java steps
  Steps implemented in Java (JavaSteps.java), run by Cucumber for Java, not by this plugin

  Scenario: Shout a greeting
    Given a Java greeting "hello"
    When the Java greeting is shouted
    Then the Java greeting is "HELLO"

  Scenario: Shared step
    # Also matches Given('a passing step') in js/features/step_definitions/status_steps.js
    Given a passing step
    And a Java greeting "hello"
    When the Java greeting is shouted
    Then the Java greeting is "HELLO"
