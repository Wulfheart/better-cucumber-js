<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Better-cucumber-js Changelog

## [Unreleased]

### Added

- Run and debug Cucumber.js features, scenarios and Scenario Outline examples from the gutter of `.feature` files
- The test runner shows the whole run up front, with pending tests, as Feature > Rule > Scenario (or Scenario Outline > Examples > example row) > Background > steps
- Step output, `this.log` messages and attachments appear under their step in the test runner
- Failing hooks show up as their own nodes, and each `--retry` attempt gets its own node; a scenario that passes on a retry is shown as passed
- Ctrl+click (Go to Declaration) on a step jumps to its JavaScript or TypeScript step definition, with a chooser for ambiguous steps; Cucumber expressions, custom parameter types and regexes are supported
- Undefined steps are highlighted, and "Create step definition" adds a cucumber-js style stub to a new or existing step file
- Rerun Failed Tests reruns the failed scenarios and example rows, also after lines were added or removed in the feature files since the run
- Rerunning a scenario or example row, from the test runner or its run configuration, still finds it after lines were added or removed in the feature file
- Examples tables: each column gets its own color, and the column at the caret is highlighted in every row; both can be turned off in Settings | Languages & Frameworks | Cucumber.js Tables
- Breadcrumbs for `.feature` files, down to the Examples row and the column (header name) at the caret
- Example Row tool window: shows the Examples row at the caret as header and value pairs, compares it with a pinned row, and edits values in place
