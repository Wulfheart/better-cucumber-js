# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project goal

An IntelliJ Platform plugin (Kotlin) that lets users work with [Cucumber.js](https://github.com/cucumber/cucumber-js) projects: Gherkin `.feature` files whose steps are implemented in JavaScript/TypeScript step definition files.

## Project state

v1 is in progress: run and debug a feature, scenario or Scenario Outline example row from the gutter of `.feature` files, with results in the IDE test runner, and navigate from steps to their step definitions. Planned work and known bugs are in `BACKLOG.md`, with IDs like `B1` or `F2`. When you finish an item, check it off there (`[x]`) and leave it in place. `verifyPlugin` rejects the template's default `<vendor>` values, so keep a real vendor in `plugin.xml`.

The run feature lives in `src/main/kotlin/run/` (package `com.wulfindustries.plugins.run`). It is built on three plugin dependencies:
- JetBrains' **Gherkin** plugin (Marketplace, pinned in `build.gradle.kts`) for `.feature` PSI.
- The bundled **JavaScript** and **NodeJS** plugins for Node interpreters, process launching and the Node debugger. The run configuration extends `AbstractNodeTargetRunProfile` and the state implements `NodeBaseRunProfileState`, following the shape of JetBrains' Mocha integration.

The plugin runs the project's own `@cucumber/cucumber` with `--format message` as the last argument, so the NDJSON goes to stdout alongside the steps' own output, in the order things happened. `CucumberJsTestEventsConverter` (registered through `SMCustomMessagesParsing` on the console properties) picks the messages out of stdout. `CucumberMessagesTranslator` turns them into TeamCity service messages for an id-based tree (`isIdBasedTestTree` on the console properties, so every message carries `nodeId`/`parentNodeId`). At the first `testCaseStarted` it registers the whole tree as pending (`running='false'`), built from `testCase` messages, not pickles, because cucumber-js emits a pickle for every scenario it parses, including filtered-out ones. `GherkinIndex` maps AST ids to rules, outlines, Examples tables, backgrounds and step keywords. The tree is Feature > Rule > Scenario > Background > steps, or Feature > Rule > Outline > Examples (only with several tables) > row > Background > steps. Steps are the tests. Rows are named after their cell values, or `Example #k` for every row of an Examples table where any row is past 60 characters. Hooks only get a node when they do not pass. Retries add `Attempt N` suites. A failure in an attempt that will be retried is reported as ignored, so it doesn't turn the scenario red. Anything never started is ignored as "Not run" at `testRunFinished`, because the runner marks the whole tree terminated otherwise. Events are routed by `testCaseStartedId`, since `--parallel` interleaves test cases. Other process output is wrapped as `testStdOut`/`testStdErr` for the running step while exactly one test case runs; otherwise it passes through. The process runs without a PTY because ConPTY can wrap long NDJSON lines. Test locations are `file://<path>:<line>` URLs built by `CucumberJsLocations`. Use that helper everywhere so gutter state icons match the test tree. Outline steps under a row point at the row as `:<row line>:1`; the column keeps their URL apart from the row's, since the IDE stores one gutter state per URL.

"Rerun Failed Tests" (`CucumberJsRerunFailedTestsAction`) reruns whole scenarios / example rows, which the translator tags with `metainfo="scenario"`. Because the tree's lines go stale once a feature file is edited, `ScenarioAnchors` puts a `ScenarioAnchor` on every tagged node when the first scenario starts: a `RangeMarker` on the scenario keyword or row, plus a name-based `ScenarioPath` (rule, name, occurrence, Examples index, cells) as a fallback for when the marker is invalidated. Run configurations created from the gutter store that `ScenarioPath` next to their line. `CucumberJsRunConfiguration.currentLocation()` looks the scenario up again on every run, so plain Rerun and saved configurations also follow edits.

Step navigation lives in `src/main/kotlin/steps/` (package `com.wulfindustries.plugins.steps`). `CucumberJsExtension` implements the Gherkin plugin's public `cucumberJvmExtensionPoint`. That one extension gives Ctrl+click from steps, completion, and Gherkin's undefined-step inspection, whose "Create step definition" fixes call `CucumberJsStepDefinitionCreator` (stubs come from `CucumberJsSnippet`). `CucumberJsSupportCode` finds `Given/When/Then/defineStep/defineParameterType` calls with literal arguments in project JS/TS files outside `node_modules`. It finds candidate files through the word index by those identifiers, because the index doesn't hold string literals like `'@cucumber/cucumber'`. The result is cached project-wide per PSI modification. Strings are always Cucumber expressions in cucumber-js; only regex literals are regexes. Gherkin matches with `Matcher.find()`, which is how cucumber-js handles an unanchored regex.

Examples table helpers live in `src/main/kotlin/tables/` (package `com.wulfindustries.plugins.tables`): rainbow columns (`RainbowColumnsAnnotator`), the column at the caret highlighted in every row (`CurrentColumnHighlighter`, an `EditorFactoryListener`), breadcrumbs for `.feature` files (`GherkinBreadcrumbsProvider`), and the Example Row tool window (`ExampleRowToolWindow.kt`). The tool window has two tabs that share one `ExampleRowSelection`, which follows the caret. The Values tab compares the row with a pinned one and edits values. The Scenario tab (`ExpandedScenarioPanel`, text from `ExpandedScenario`) shows Backgrounds plus the outline with placeholders replaced. Both tabs run or debug the row through `ExampleRowRunner`, which reuses `CucumberJsRunConfigurationProducer`. They all go through `ExamplesTables`, which defines columns as the spans between `PIPE` tokens, not `GherkinTableRow.psiCells`, because an empty cell has no PSI and would shift every later column. Step data tables are left out on purpose. `ExamplesTables.tableOf` only accepts tables inside an Examples block.

cucumber-js behaviour, checked against 13.3.0 in `playground/`:
- stdout gets the **last** `--format` without a target, or `progress` if there is none. Formats from the config file and the CLI are combined, with CLI formats last.
- Feature paths on the command line are **appended to** the config's `paths`, not used instead of them. If a user's `cucumber.js` sets `paths`, a gutter run of one scenario runs the whole suite. This is a known open bug in the plugin. The playground config leaves out `paths` on purpose.
- A Scenario Outline with no Examples section still produces one pickle, with placeholders left as literal `<name>` (usually undefined steps). Examples that have only a header row produce no pickles.
- `--retry N` reports each attempt as its own `testCaseStarted`, and every attempt except the last has `willBeRetried: true`. Hook steps also count as steps in the summary.
- All `testCase` messages are emitted before the first `testCaseStarted`, also with `--parallel`. `--fail-fast` still starts every test case and reports the remaining steps as `SKIPPED`. Ambiguous steps come with no `message`. `--format message:<path>` needs a relative path; with an absolute Windows path it silently writes nothing.

Only use public IntelliJ Platform APIs. These members are `@ApiStatus.Internal` and must be avoided: `NodePackageField.getDescriptor()`, `ExecutorAction.wrap(...)`, `RunLineMarkerContributor.EXTENSION`.

## Commands

Use `./gradlew` (or `.\gradlew.bat` on Windows). CI runs on JDK 21.

- `./gradlew buildPlugin`: build the distributable ZIP into `build/distributions/`
- `./gradlew runIde`: launch a sandbox IDE with the plugin installed, opening `playground/` (debug through the IDE's `.run/runIde.run.xml` config). `playground/` is a Cucumber.js project covering scenario shapes and result statuses for manual testing. Run `npm install` there first; its README maps each feature file to what it exercises. The sandbox needs `org.jetbrains.intellij.platform.subscriptionKey=<path to idea.key>` in `~/.gradle/gradle.properties`, and `runIde` fails without it. Without a subscription the IDE runs in free mode, which disables the Ultimate-only Node.js plugin and therefore this plugin, so no gutter icons appear.
- `./gradlew check`: run tests (JUnit 4 + IntelliJ Platform test framework)
- `./gradlew test --tests "com.wulfindustries.plugins.SomeTest"`: run a single test class (append `.methodName` for one method)
- `./gradlew verifyPlugin`: run the IntelliJ Plugin Verifier for compatibility checks
- `./gradlew getChangelog --unreleased`: print the unreleased section of `CHANGELOG.md`

No `src/test` directory exists yet. Put tests under `src/test/kotlin`. Platform tests usually extend `BasePlatformTestCase`.

## Architecture

- **`src/main/resources/META-INF/plugin.xml`** is the plugin manifest. Every extension (tool windows, inspections, language support, etc.) must be registered here. The `<id>` (`com.wulfindustries.plugins.better-cucumber-js`) must never change between releases. The plugin depends only on `com.intellij.modules.platform`; anything that needs JavaScript/Gherkin PSI also needs a `<depends>` entry here plus a matching `bundledPlugin(...)`/`plugin(...)` in the `intellijPlatform {}` block of `build.gradle.kts`.
- **Target IDE** is set by `intellijIdea("2026.2.3")` in `build.gradle.kts`. The README's sample version is outdated, so treat the build file as the source of truth.
- **Kotlin sources** sit directly in `src/main/kotlin/` but declare `package com.wulfindustries.plugins`. Class names referenced from `plugin.xml` (e.g. `factoryClass`) must use the package FQN, not the file path.
- **User-facing strings** go through `MyMessageBundle` (backed by `messages/MyMessageBundle.properties`, declared via `<resource-bundle>` in `plugin.xml`). Use `MyMessageBundle.message(key, ...)`, or `lazyMessage` where a `Supplier` is needed.
- **Build config**: Gradle plugin versions are pinned in `settings.gradle.kts` (`pluginManagement` plus the `org.jetbrains.intellij.platform.settings` plugin, which also sets up repositories). Library versions live in `gradle/libs.versions.toml`. Configuration cache and build cache are on in `gradle.properties`, which also holds the plugin `version`.

## Release flow

On a push to `main`, `.github/workflows/build.yml` builds, tests, and verifies the plugin, then creates a draft GitHub release named after `version` in `gradle.properties`, using the `[Unreleased]` section of `CHANGELOG.md` as the notes. Publishing that draft triggers `release.yml`, which pushes the plugin to JetBrains Marketplace. That step needs the `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, and `PRIVATE_KEY_PASSWORD` secrets. Keep `CHANGELOG.md`'s `[Unreleased]` section current, because release notes are generated from it.
