# Playground

A Cucumber.js project for manually testing the plugin. `./gradlew runIde` opens it in the sandbox IDE.

Setup: run `npm install` in this directory, then set a Node.js interpreter in the sandbox IDE settings.

| File | What it exercises |
| --- | --- |
| `features/scenarios.feature` | Scenarios without Examples: `Scenario`/`Example` keywords, tags before the keyword, descriptions, data tables, doc strings, a scenario with no steps, two scenarios with the same name, feature-level Background |
| `features/outlines.feature` | Scenario Outlines: `Scenario Template`/`Scenarios` aliases, one failing row, multiple named and tagged Examples tables, placeholders in step arguments, duplicate rows, a name without placeholders, header-only Examples (0 tests), no Examples (1 undefined test), a row too wide to use as a test name (so its whole table is numbered) |
| `features/rules.feature` | `Rule` blocks with their own Background, plus scenarios and outlines inside rules |
| `features/statuses.feature` | Every result status: passed, failed (assertion and plain error), pending, undefined, ambiguous, skipped, and a slow step |
| `features/output.feature` | Step output: stdout, stderr, interleaved streams, output spread over time, `this.log`, text/JSON/PNG attachments, output before a failure, output from tagged hooks, output per example row |
| `features/hooks.feature` | Hooks: a tagged Before hook and a tagged After hook that throw (`@failing-before`, `@failing-after`) |
| `features/slow.feature` | Slow steps (about 20 s in total) for watching pending and running states: a slow Background, several slow steps in a row, progress logged while a step runs, a slow failure followed by a skipped step, a step that exceeds its own 1 s timeout, slow outline rows |
| `features/flaky.feature` | Retries: passes with `--profile retry` (fails once, then passes), fails without it |
| `features/navigation.feature` | Step navigation (Ctrl+click a step): `defineParameterType` types with a regex, string or array `regexp`, regex literals (anchored, unanchored with `i`, several capture groups, a named and an optional group), a template literal, optional text and alternatives. A step defined with `new RegExp(...)` stays unresolved in the IDE (known limitation). Ambiguous steps (chooser) and an undefined step (warning plus "Create step definition") are in `statuses.feature` |
| `features/i18n.feature` | German Gherkin keywords (`# language: de`) |
| `features/empty.feature` | A feature with no scenarios |
| `features/nested/deep.feature` | Location resolution for a feature in a subdirectory |

Useful run configuration settings to try: Tags `@smoke`, `@large` or `not @flaky`, and Profile `retry`.
