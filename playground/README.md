# Playground

Projects for manually testing the plugin. `./gradlew runIde` opens this directory in the sandbox IDE.

- `js/`: the Cucumber.js project the tables below describe.
- `java/`: a Cucumber for Java project, for a mixed setup (see the end of this file). It is a sibling of `js/` rather than below it, so `node_modules/@cucumber/cucumber` sits above the JS features only, as in a typical mixed repository.

Setup: run `npm install` in `js/`, then set a Node.js interpreter in the sandbox IDE settings. Paths in the tables are relative to `js/`.

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
| `features/wide-examples.feature` | Examples table helpers on a 15-column, 30-row table: rainbow columns, the highlighted column at the caret, breadcrumbs (row and column), and the Example Row tool window (Values tab: pin a row, compare, show differences only, edit a value; Scenario tab: the scenario with the row's values filled in; Run and Debug buttons). Cells include an escaped pipe and a very long note (no empty cells, which Gherkin's broken-table inspection wrongly flags); turn on soft wrap. The second table has some of the same headers, for comparing a pinned row across tables by header name |
| `features/i18n.feature` | German Gherkin keywords (`# language: de`) |
| `features/empty.feature` | A feature with no scenarios |
| `features/nested/deep.feature` | Location resolution for a feature in a subdirectory |

Useful run configuration settings to try: Tags `@smoke`, `@large` or `not @flaky`, and Profile `retry`.

## Mixed Java and JS project

`java/` is a small Maven project with Cucumber for Java (`java/src/test/resources/playground/java.feature`, steps in `JavaSteps.java`). It reproduces a project that has both runners, where Cucumber for Java's run configuration producer also accepts every JS feature file.

1. Run `./gradlew runIde` from the repository root. Its sandbox includes the Cucumber for Java plugin.
2. `runIde` links `java/pom.xml` as a Maven project of the playground (in `.idea/misc.xml`), so the sandbox imports it on opening. Set a JDK 17+ as the project SDK if it asks for one.
3. Once indexing is done, open a JS feature such as `js/features/scenarios.feature` and click a gutter icon. It should run with this plugin's Cucumber.js configuration, not "Cucumber Java".
4. Open `java/src/test/resources/playground/java.feature`. Its gutter should still offer Cucumber for Java.
5. Shared step texts: the "Shared step" scenario in `java.feature` uses `a passing step`, which both `JavaSteps.java` and `js/features/step_definitions/status_steps.js` define. Each feature file belongs to one runner, so Ctrl+click on it goes straight to `JavaSteps.java` in `java.feature`, and straight to `status_steps.js` in `js/features/statuses.feature`, with no chooser.
6. Hoisted install: `package.json` in this directory installs `@cucumber/cucumber` above `java/`, the way a workspace hoists it to the repository root. Run `npm install` here (not only in `js/`) and repeat steps 3 to 5: nothing should change, since `java/pom.xml` keeps `java.feature` with Cucumber for Java and `js/package.json` keeps the JS features with Cucumber.js.
