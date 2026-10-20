# Backlog

Feature ideas and known bugs that aren't scheduled yet.

Each item has a stable ID (`B` for bugs, `F` for features) to refer to it by, e.g. "work on F2". Finished items get checked off (`[x]`) and stay in place; IDs are never reused. New items take the next free number in their section.

## Bugs

- [x] **B1: Rerun breaks after adding a line to the feature file.** The test runner's "Rerun" action (not "Rerun Failed Tests") stops working once a new line is added to the feature file. Likely cause: the run configuration created from the gutter holds a fixed `file:line`, which no longer points at the scenario after the edit. "Rerun Failed Tests" avoids this through `ScenarioAnchors`; Rerun may need the same treatment.
- [ ] **B2: A gutter run executes the whole suite when the config sets `paths`.** cucumber-js appends feature paths from the command line to the config's `paths` instead of replacing them (checked against 13.3.0), so running one scenario from the gutter runs everything when the user's `cucumber.js` sets `paths`. Fix by overriding `paths` for gutter runs, e.g. a generated config that loads the user's config/profile and clears `paths`.
- [x] **B3: The gutter offers Cucumber for Java instead of Cucumber.js in mixed projects.** With the Cucumber for Java plugin and cucumber-java on a module's classpath, its producers accept every `.feature` file and keep the clicked leaf as their source element. The platform ranks configurations by source element before `shouldReplace`, so ours (source: the enclosing scenario) was dropped. Fixed by keeping the clicked element as source, replacing other runners where the feature's steps resolve to JS/TS, and stepping back (`isPreferredConfiguration`) where they resolve to another language such as Java. Reproduce with `./gradlew runIde` (see "Mixed Java and JS project" in `playground/README.md`).

## Feature ideas

- [ ] **F1: Find Usages from step definitions.** Find Usages from a JS/TS step definition to the feature steps that use it, via a `referencesSearch` executor built on Gherkin's `CucumberUtil.findGherkinReferencesToElement`.
- [x] **F2: Consistent Example row names.** Rows are named after their cell values, or `Example #k` past 60 characters. If at least one row in an Examples table is too long and shown as `Example #k`, name every row in that table `Example #k`, so the table doesn't show a mix of cell values and numbers.
- [x] **F3: Highlight the Examples column at the caret.** With the caret in a cell of an Examples table, the whole column is highlighted in every row, header included, so values can be compared down the column even when long rows soft-wrap.
- [x] **F4: Rainbow columns.** Each column of an Examples table gets its own color, so wrapped rows can still be read column by column. Colors are in Settings | Editor | Color Scheme | Cucumber.js Tables. Both F3 and F4 can be turned off in Settings | Languages & Frameworks | Cucumber.js Tables.
- [x] **F5: Breadcrumbs for feature files.** Feature › Rule › Scenario › Examples › row › column, where the column crumb is the header name of the cell at the caret.
- [x] **F6: Example Row tool window.** Shows the Examples row at the caret as `header | value` lines. A row can be pinned to compare it with the row at the caret, by column within a table or by header name across tables, with a filter for differing columns. Values can be edited in place (escaped and re-aligned, one undo step), and selecting a line moves the caret to that cell.
- [x] **F7: Run and debug from the Example Row tool window.** Run and Debug buttons in both tabs start the shown Examples row through the same run configuration producer as its gutter icon, reusing a matching configuration.
- [x] **F8: Expanded scenario tab.** A Scenario tab in the Example Row tool window shows the scenario as cucumber-js runs it for the shown row: Backgrounds first, then the outline's steps with placeholders replaced in step text, data tables (re-aligned) and doc strings, inserted values highlighted.
- [ ] **F9: Outline placeholder inspection.** Warn about a `<name>` placeholder in a Scenario Outline step (text, data table or doc string) that has no matching column in one of its Examples tables, and about Examples columns no step uses. Quick fix: add the missing column. Build on `ExamplesTables` for columns.
- [ ] **F10: Inline failure markers.** After a run, show a failed step's error message as an editor annotation/inlay on its line in the `.feature` file, cleared on the next run or on edit. Locations come from `CucumberJsLocations`.
- [ ] **F11: Open binary attachments.** Text from `this.log`/`this.attach` already shows as step output, but binary attachments (e.g. screenshots) only get a one-line summary in `CucumberMessagesTranslator.onAttachment`. Save them to a temp file and make the summary a link that opens them in the IDE.
- [x] **F12: Run a directory of feature files.** A directory with a `.feature` file anywhere below it can be run or debugged from its context menu in the Project view. cucumber-js gets the directory as its path and runs every feature file in it.
