package com.wulfindustries.plugins.run

import org.junit.Assert.assertEquals
import org.junit.Test

class CucumberJsCommandLineTest {
    private fun render(
        line: Int? = null,
        tags: String = "",
        profile: String = "",
        extraArgs: String = "",
        messagesFile: String = "C:\\Temp\\cucumber-messages.ndjson",
    ): List<String> = CucumberJsCommandLine.arguments(
        cucumberPackagePath = "C:\\project\\node_modules\\@cucumber\\cucumber",
        featurePath = "C:\\project\\features\\calculator.feature",
        line = line,
        tags = tags,
        profile = profile,
        messagesFile = messagesFile,
        extraArgs = extraArgs,
    ).map { it.render() }

    @Test
    fun wholeFeature() {
        assertEquals(
            listOf(
                "C:/project/node_modules/@cucumber/cucumber/bin/cucumber-js",
                "C:/project/features/calculator.feature",
                "--format",
                "message:\"C:/Temp/cucumber-messages.ndjson\"",
            ),
            render(),
        )
    }

    @Test
    fun scenarioLineIsAppendedToFeaturePath() {
        assertEquals("C:/project/features/calculator.feature:12", render(line = 12)[1])
    }

    @Test
    fun messagesTargetIsQuotedSoTheDriveLetterColonIsNotTakenAsSeparator() {
        // cucumber-js splits `<formatter>:<target>` at the first unquoted colon; an unquoted
        // `message:C:/...` writes nothing on Windows, the quoted form writes the file
        val formatArgs = render().dropWhile { it != "--format" }
        assertEquals(listOf("--format", "message:\"C:/Temp/cucumber-messages.ndjson\""), formatArgs)
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
                "--format",
                "message:\"C:/Temp/cucumber-messages.ndjson\"",
                "--fail-fast",
                "--world-parameters",
                "{\"a\": 1}",
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
            messagesFile = "/local/tmp/m.ndjson",
            extraArgs = "",
        ).map { arg -> arg.render { it.replace("/local", "/remote") } }

        assertEquals(
            listOf(
                "/remote/pkg/bin/cucumber-js",
                "/remote/a.feature:7",
                "--tags",
                "@x",
                "--format",
                "message:\"/remote/tmp/m.ndjson\"",
            ),
            converted,
        )
    }
}
