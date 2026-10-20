Feature: Slow steps
  Steps that take a while, to watch pending, running and finished states in the test tree.
  The whole feature takes about 20 seconds.

  Background:
    Given this step takes 500 ms

  Scenario: Several slow steps in a row
    Given this step takes 1000 ms
    When this step takes 1500 ms
    Then this step takes 1000 ms

  Scenario: Output while a step runs
    When I wait 3000 ms while logging progress

  Scenario: A slow step fails and the rest are skipped
    Given this step takes 1000 ms
    When a step that fails after 1500 ms
    Then this step takes 1000 ms

  Scenario: A step that times out
    Then a step that exceeds its timeout

  Scenario Outline: Slow example rows
    Given this step takes <first> ms
    Then this step takes <second> ms

    Examples:
      | first | second |
      | 500   | 1000   |
      | 1500  | 500    |
      | 1000  | 1000   |
