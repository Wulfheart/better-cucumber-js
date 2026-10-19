# Backlog

Feature ideas and known bugs that aren't scheduled yet.

Each item has a stable ID (`B` for bugs, `F` for features) to refer to it by, e.g. "work on F2". Finished items get checked off (`[x]`) and stay in place; IDs are never reused. New items take the next free number in their section.

## Bugs

- [x] **B1: Rerun breaks after adding a line to the feature file.** The test runner's "Rerun" action (not "Rerun Failed Tests") stops working once a new line is added to the feature file. Likely cause: the run configuration created from the gutter holds a fixed `file:line`, which no longer points at the scenario after the edit. "Rerun Failed Tests" avoids this through `ScenarioAnchors`; Rerun may need the same treatment.

## Feature ideas

- [ ] **F1: Find Usages from step definitions.** Find Usages from a JS/TS step definition to the feature steps that use it, via a `referencesSearch` executor built on Gherkin's `CucumberUtil.findGherkinReferencesToElement`.
- [x] **F2: Consistent Example row names.** Rows are named after their cell values, or `Example #k` past 60 characters. If at least one row in an Examples table is too long and shown as `Example #k`, name every row in that table `Example #k`, so the table doesn't show a mix of cell values and numbers.
- [x] **F3: Highlight the Examples column at the caret.** With the caret in a cell of an Examples table, the whole column is highlighted in every row, header included, so values can be compared down the column even when long rows soft-wrap.
- [x] **F4: Rainbow columns.** Each column of an Examples table gets its own color, so wrapped rows can still be read column by column. Colors are in Settings | Editor | Color Scheme | Cucumber.js Tables. Both F3 and F4 can be turned off in Settings | Languages & Frameworks | Cucumber.js Tables.
- [x] **F5: Breadcrumbs for feature files.** Feature › Rule › Scenario › Examples › row › column, where the column crumb is the header name of the cell at the caret.
- [x] **F6: Example Row tool window.** Shows the Examples row at the caret as `header | value` lines. A row can be pinned to compare it with the row at the caret, by column within a table or by header name across tables, with a filter for differing columns. Values can be edited in place (escaped and re-aligned, one undo step), and selecting a line moves the caret to that cell.
