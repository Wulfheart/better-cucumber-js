package com.wulfindustries.plugins.run

import org.junit.Assert.assertEquals
import org.junit.Test

class CucumberJsCommandLineTest {
    private fun render(
        line: Int? = null,
        tags: String = "",
        profile: String = "",
        extraArgs: String = "",
    ): List<String> = CucumberJsCommandLine.arguments(
        cucumberPackagePath = "C:\\project\\node_modules\\@cucumber\\cucumber",
        featurePath = "C:\\project\\features\\calculator.feature",
        line = line,
        tags = tags,
        profile = profile,
        extraArgs = extraArgs,
    ).map { it.render() }

    @Test
    fun wholeFeature() {
        assertEquals(
            listOf(
                "C:/project/node_modules/@cucumber/cucumber/bin/cucumber-js",
                "C:/project/features/calculator.feature",
                "--format",
                "message",
            ),
            render(),
        )
    }

    @Test
    fun scenarioLineIsAppendedToFeaturePath() {
        assertEquals("C:/project/features/calculator.feature:12", render(line = 12)[1])
    }

    @Test
    fun messageFormatComesLastSoItOwnsStdout() {
        // cucumber-js writes the last format without a target to stdout
        assertEquals(listOf("--format", "message"), render(extraArgs = "--format progress").takeLast(2))
    }

    @Test
    fun optionalFiltersAndExtraArgs() {
        assertEquals(
            listOf(
                "C:/project/node_modules/@cucumber/cucumber/bin/cucumber-js",
                "C:/project/features/calculator.feature:3",
                "--tags",
                "@smoke and not @wip",
                "--profile",
                "ci",
                "--fail-fast",
                "--world-parameters",
                "{\"a\": 1}",
                "--format",
                "message",
            ),
            render(
                line = 3,
                tags = " @smoke and not @wip ",
                profile = "ci",
                extraArgs = "--fail-fast --world-parameters '{\"a\": 1}'",
            ),
        )
    }

    @Test
    fun pathConversionAppliesToPathArgumentsOnly() {
        val converted = CucumberJsCommandLine.arguments(
            cucumberPackagePath = "/local/pkg",
            featurePath = "/local/a.feature",
            line = 7,
            tags = "@x",
            profile = "",
            extraArgs = "",
        ).map { arg -> arg.render { it.replace("/local", "/remote") } }

        assertEquals(
            listOf(
                "/remote/pkg/bin/cucumber-js",
                "/remote/a.feature:7",
                "--tags",
                "@x",
                "--format",
                "message",
            ),
            converted,
        )
    }
}
