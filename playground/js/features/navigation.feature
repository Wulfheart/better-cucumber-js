Feature: Step navigation
  Ctrl+click a step to jump to its definition in features/step_definitions/navigation_steps.js.

  Scenario: Custom parameter type and regex
    Given a green ball
    Then the ball is GREEN

  Scenario: Parameter types with a string and an array regexp
    Given a big square
    Then it is big and square
    Given a small circle
    Then it is small and circle indeed

  Scenario: Regex that isn't a literal (shown as undefined in the IDE, passes in cucumber-js)
    Given a step with a built regex

  Scenario: Template literal
    Given a step defined with a template literal

  Scenario: Optional text, alternatives and a case-insensitive regex
    Given I have 1 cucumber in my belly
    And I have 3 cucumbers in my stomach
    Then that is MANY CUCUMBERS
