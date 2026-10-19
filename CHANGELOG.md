<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Better-cucumber-js Changelog

## [Unreleased]

### Added

- Run and debug Cucumber.js features, scenarios and Scenario Outline examples from the gutter of `.feature` files
- The test runner shows the whole run up front, with pending tests, as Feature > Rule > Scenario (or Scenario Outline > Examples > example row) > Background > steps
- Step output, `this.log` messages and attachments appear under their step in the test runner
- Failing hooks show up as their own nodes, and each `--retry` attempt gets its own node; a scenario that passes on a retry is shown as passed
