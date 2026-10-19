Feature: Scenario Outlines
  Every kind of Scenario Outline. Each Examples row is its own test.

  Scenario Outline: Add <a> and <b>
    Given the number <a>
    And the number <b>
    When I add them
    Then the result is <sum>

    Examples:
      | a | b | sum |
      | 1 | 2 | 3   |
      | 2 | 2 | 4   |
      | 5 | 5 | 99  |

  Scenario Template: Scenario Template and Scenarios keywords are aliases
    Given the number <n>
    When I add them
    Then the result is <n>

    Scenarios:
      | n |
      | 8 |

  Scenario Outline: Several named and tagged Examples tables
    Given the number <a>
    And the number <b>
    When I multiply them
    Then the result is <product>

    @small
    Examples: Small numbers
      | a | b | product |
      | 1 | 1 | 1       |
      | 2 | 3 | 6       |

    @large
    Examples: Large numbers
      | a   | b   | product |
      | 100 | 100 | 10000   |

    Examples:
      | a | b | product |
      | 0 | 9 | 0       |

  Scenario Outline: Placeholders inside step arguments
    Given the numbers:
      | <a> | <b> |
    And the expression:
      """
      <c> + <c>
      """
    When I add them
    Then the result is <sum>

    Examples:
      | a | b | c | sum |
      | 1 | 2 | 3 | 9   |

  Scenario Outline: Duplicate rows produce identically named tests
    Given the number <n>
    When I add them
    Then the result is <n>

    Examples:
      | n |
      | 1 |
      | 1 |

  Scenario Outline: Name without placeholders
    Given the number <n>
    When I add them
    Then the result is <n>

    Examples:
      | n |
      | 3 |
      | 4 |

  Scenario Outline: Examples with a header row only (no tests)
    Given the number <n>

    Examples:
      | n |

  Scenario Outline: No Examples at all (runs once with <n> unreplaced, so undefined)
    Given the number <n>
