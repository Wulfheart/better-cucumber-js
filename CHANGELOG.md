<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Better-cucumber-js Changelog

## [Unreleased]

### Added

- Run and debug Cucumber.js features, scenarios and Scenario Outline examples from the gutter of `.feature` files
- Run and debug every feature file in a directory from its context menu in the Project view
- In projects that also use Cucumber for Java, features whose steps are implemented in JavaScript or TypeScript run with Cucumber.js from the gutter
- The test runner shows the whole run up front, with pending tests, as Feature > Rule > Scenario (or Scenario Outline > Examples > example row) > Background > steps
- Clicking a step under an example row in the test runner jumps to that row
- The test runner's status line also counts scenarios (each example row is one): the scenario that is running while the run goes on, then how many failed, passed and were ignored
- Step output, `this.log` messages and attachments appear under their step in the test runner
- Failing hooks show up as their own nodes, and each `--retry` attempt gets its own node; a scenario that passes on a retry is shown as passed
- Ctrl+click (Go to Declaration) on a step jumps to its JavaScript or TypeScript step definition, with a chooser for ambiguous steps; Cucumber expressions, custom parameter types and regexes are supported
- Undefined steps are highlighted, and "Create step definition" adds a cucumber-js style stub to a new or existing step file
- Rerun Failed Tests reruns the failed scenarios and example rows, also after lines were added or removed in the feature files since the run
- Rerunning a scenario or example row, from the test runner or its run configuration, still finds it after lines were added or removed in the feature file
- Examples tables: each column gets its own color, and the column at the caret is highlighted in every row; both can be turned off in Settings | Languages & Frameworks | Cucumber.js Tables
- Breadcrumbs for `.feature` files, down to the Examples row and the column (header name) at the caret
- Example Row tool window: shows the Examples row at the caret as header and value pairs, compares it with a pinned row, and edits values in place
- The Example Row tool window runs or debugs the shown row, and its Scenario tab shows the scenario with the row's values filled in
- The Example Row tool window also shows the example row that is being run, and is empty outside Examples rows; it can select the row in the editor, keeps one pinned row per Scenario Outline, jumps to the selected cell only when "Jump to cell on selection" is on, and writes edited values to the feature file as you type
