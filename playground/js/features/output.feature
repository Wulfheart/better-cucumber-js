Feature: Step output
  Steps that write output in every way cucumber-js supports. Use these scenarios to check where
  the output appears in the test runner and which test it belongs to.

  Scenario: Console output
    Given the step writes "hello from stdout" to stdout
    And the step writes "hello from stderr" to stderr
    And the step writes 20 lines to stdout

  Scenario: Interleaved stdout and stderr
    Then the step writes stdout and stderr in turns

  Scenario: Output arriving over time
    Then the step writes a line every 500 ms, 4 times

  Scenario: Logs and attachments
    Given the step logs "hello from this.log"
    And the step attaches a text document
    And the step attaches JSON
    And the step attaches a PNG image

  Scenario: Output before a failure
    Given the step writes "written before the failure" to stdout
    And the step logs "logged before the failure"
    Then this step fails

  @output-hooks
  Scenario: Output from hooks
    Given the step writes "written by the step" to stdout

  Scenario Outline: Output per example row
    Given the step writes "<line>" to stdout
    And the step logs "hello world"

    Examples:
      | line          |
      | first row     |
      | second row    |
