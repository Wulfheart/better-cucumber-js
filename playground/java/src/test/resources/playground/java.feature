Feature: Java steps
  Steps implemented in Java (JavaSteps.java), run by Cucumber for Java, not by this plugin

  Scenario: Shout a greeting
    Given a Java greeting "hello"
    When the Java greeting is shouted
    Then the Java greeting is "HELLO"
