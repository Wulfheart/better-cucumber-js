@flaky
Feature: Retries
  Run with the "retry" profile: the scenario fails once, is retried and then passes.
  Without retries it fails.

  Scenario: Fails on the first attempt only
    Then this step fails on the first attempt only
