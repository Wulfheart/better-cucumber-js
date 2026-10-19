# Playground

A Cucumber.js project for manually testing the plugin. `./gradlew runIde` opens it in the sandbox IDE.

Setup: run `npm install` in this directory, then set a Node.js interpreter in the sandbox IDE settings.

| File | What it exercises |
| --- | --- |
| `features/scenarios.feature` | Scenarios without Examples: `Scenario`/`Example` keywords, tags before the keyword, descriptions, data tables, doc strings, a scenario with no steps, two scenarios with the same name, feature-level Background |
| `features/outlines.feature` | Scenario Outlines: `Scenario Template`/`Scenarios` aliases, one failing row, multiple named and tagged Examples tables, placeholders in step arguments, duplicate rows, a name without placeholders, header-only Examples (0 tests), no Examples (1 undefined test) |
| `features/rules.feature` | `Rule` blocks with their own Background, plus scenarios and outlines inside rules |
| `features/statuses.feature` | Every result status: passed, failed (assertion and plain error), pending, undefined, ambiguous, skipped, and a slow step |
| `features/flaky.feature` | Retries: passes with `--profile retry` (fails once, then passes), fails without it |
| `features/i18n.feature` | German Gherkin keywords (`# language: de`) |
| `features/empty.feature` | A feature with no scenarios |
| `features/nested/deep.feature` | Location resolution for a feature in a subdirectory |

Useful run configuration settings to try: Tags `@smoke`, `@large` or `not @flaky`, and Profile `retry`.
