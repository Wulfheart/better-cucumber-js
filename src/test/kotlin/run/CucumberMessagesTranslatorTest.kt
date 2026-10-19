package com.wulfindustries.plugins.run

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

// Fixtures were captured with cucumber-js 13.3.0 (`--format message:<file>`) from a sample project whose
// features/calculator.feature has a passing, a failing and an undefined scenario plus a two-row Scenario Outline.
class CucumberMessagesTranslatorTest {
    private val baseDir: Path = Path.of("").toAbsolutePath().resolve("sample-project")
    private val featurePath = baseDir.resolve("features/calculator.feature").normalize().toString()
    private val featureUrl = CucumberJsLocations.url(featurePath)

    private fun lineUrl(line: Int) = CucumberJsLocations.url(featurePath, line)

    private fun translate(fixture: String): List<ServiceMessage> {
        val translator = CucumberMessagesTranslator(baseDir)
        return Files.readAllLines(Path.of("src/test/testData/messages/$fixture"))
            .flatMap(translator::translate)
            .map { requireNotNull(ServiceMessage.parse(it)) { "Not a service message: $it" } }
    }

    /** Compact rendering: message name, test name, location hint, failure/ignore message. */
    private fun render(message: ServiceMessage): String {
        val attributes = message.attributes
        return listOfNotNull(
            message.messageName,
            attributes["name"],
            attributes["locationHint"],
            attributes["message"],
        ).joinToString(" | ")
    }

    @Test
    fun fullRun() {
        val messages = translate("full-run.ndjson")
        assertEquals(
            listOf(
                "testSuiteStarted | Calculator | $featureUrl",
                "testStarted | Adding two numbers | ${lineUrl(3)}",
                "testFinished | Adding two numbers",
                "testStarted | A failing addition | ${lineUrl(8)}",
                "testFailed | A failing addition | AssertionError: Expected values to be strictly equal:",
                "testFinished | A failing addition",
                "testStarted | An undefined step | ${lineUrl(13)}",
                "testIgnored | An undefined step | Undefined step: I divide by zero",
                "testFinished | An undefined step",
                "testStarted | Adding many numbers (1, 1, 2) | ${lineUrl(24)}",
                "testFinished | Adding many numbers (1, 1, 2)",
                "testStarted | Adding many numbers (2, 2, 5) | ${lineUrl(25)}",
                "testFailed | Adding many numbers (2, 2, 5) | AssertionError: Expected values to be strictly equal:",
                "testFinished | Adding many numbers (2, 2, 5)",
                "testSuiteFinished | Calculator",
            ),
            messages.map(::render),
        )
    }

    @Test
    fun failureCarriesStackTraceAndDuration() {
        val messages = translate("full-run.ndjson")
        val failed = messages.first { it.messageName == "testFailed" }
        assertTrue(failed.attributes["details"].orEmpty().contains("steps.js:6"))
        messages.filter { it.messageName == "testFinished" }.forEach {
            assertTrue(it.attributes["duration"]!!.toLong() >= 0)
        }
    }

    @Test
    fun singleExampleRow() {
        assertEquals(
            listOf(
                "testSuiteStarted | Calculator | $featureUrl",
                "testStarted | Adding many numbers (2, 2, 5) | ${lineUrl(25)}",
                "testFailed | Adding many numbers (2, 2, 5) | AssertionError: Expected values to be strictly equal:",
                "testFinished | Adding many numbers (2, 2, 5)",
                "testSuiteFinished | Calculator",
            ),
            translate("single-example.ndjson").map(::render),
        )
    }

    @Test
    fun retriedAttemptsReportOnlyTheFinalResult() {
        assertEquals(
            listOf(
                "testSuiteStarted | Calculator | $featureUrl",
                "testStarted | A failing addition | ${lineUrl(8)}",
                "testFailed | A failing addition | AssertionError: Expected values to be strictly equal:",
                "testFinished | A failing addition",
                "testSuiteFinished | Calculator",
            ),
            translate("retry.ndjson").map(::render),
        )
    }

    @Test
    fun ignoresNonJsonAndUnknownLines() {
        val translator = CucumberMessagesTranslator(baseDir)
        assertEquals(emptyList<String>(), translator.translate(""))
        assertEquals(emptyList<String>(), translator.translate("console.log output"))
        assertEquals(emptyList<String>(), translator.translate("{not json"))
        assertEquals(emptyList<String>(), translator.translate("""{"somethingNew":{}}"""))
    }
}
