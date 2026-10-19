# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project goal

An IntelliJ Platform plugin (Kotlin) that lets users work with [Cucumber.js](https://github.com/cucumber/cucumber-js) projects: Gherkin `.feature` files whose steps are implemented in JavaScript/TypeScript step definition files.

## Project state

v1 is in progress: run and debug a feature, scenario or Scenario Outline example row from the gutter of `.feature` files, with results in the IDE test runner. Step navigation, inspections and completion come later. `plugin.xml` still has a placeholder vendor.

The run feature lives in `src/main/kotlin/run/` (package `com.wulfindustries.plugins.run`). It is built on three plugin dependencies:
- JetBrains' **Gherkin** plugin (Marketplace, pinned in `build.gradle.kts`) for `.feature` PSI.
- The bundled **JavaScript** and **NodeJS** plugins for Node interpreters, process launching and the Node debugger. The run configuration extends `AbstractNodeTargetRunProfile` and the state implements `NodeBaseRunProfileState`, following the shape of JetBrains' Mocha integration.

The plugin runs the project's own `@cucumber/cucumber` with `--format message:<tempfile>`. `CucumberMessagesTranslator` turns that NDJSON into TeamCity service messages, which are injected into the process output for the standard SM test runner. Test locations are `file://<path>:<line>` URLs built by `CucumberJsLocations`. Use that helper everywhere so gutter state icons match the test tree.

Only use public IntelliJ Platform APIs. These members are `@ApiStatus.Internal` and must be avoided: `NodePackageField.getDescriptor()`, `ExecutorAction.wrap(...)`, `RunLineMarkerContributor.EXTENSION`.

## Commands

Use `./gradlew` (or `.\gradlew.bat` on Windows). CI runs on JDK 21.

- `./gradlew buildPlugin`: build the distributable ZIP into `build/distributions/`
- `./gradlew runIde`: launch a sandbox IDE with the plugin installed (debug through the IDE's `.run/runIde.run.xml` config)
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
