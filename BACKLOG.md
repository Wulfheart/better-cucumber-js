# Backlog

Feature ideas and known bugs that aren't scheduled yet.

Each item has a stable ID (`B` for bugs, `F` for features) to refer to it by, e.g. "work on F2". Finished items get checked off (`[x]`) and stay in place; IDs are never reused. New items take the next free number in their section.

## Bugs

- [x] **B1: Rerun breaks after adding a line to the feature file.** The test runner's "Rerun" action (not "Rerun Failed Tests") stops working once a new line is added to the feature file. Likely cause: the run configuration created from the gutter holds a fixed `file:line`, which no longer points at the scenario after the edit. "Rerun Failed Tests" avoids this through `ScenarioAnchors`; Rerun may need the same treatment.

## Feature ideas

- [ ] **F1: Find Usages from step definitions.** Find Usages from a JS/TS step definition to the feature steps that use it, via a `referencesSearch` executor built on Gherkin's `CucumberUtil.findGherkinReferencesToElement`.
- [ ] **F2: Consistent Example row names.** Rows are named after their cell values, or `Example #k` past 60 characters. If at least one row in an Examples table is too long and shown as `Example #k`, name every row in that table `Example #k`, so the table doesn't show a mix of cell values and numbers.
