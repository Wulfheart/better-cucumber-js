package com.wulfindustries.plugins.run

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

// full-run, single-example and retry were captured with cucumber-js 13.3.0 (`--format message:<file>`) from a sample
// project whose features/calculator.feature has a passing, a failing and an undefined scenario plus a two-row
// Scenario Outline. The other fixtures were captured from the playground features of the same name.
class CucumberMessagesTranslatorTest {
    private val baseDir: Path = Path.of("").toAbsolutePath().resolve("sample-project")

    private fun lines(fixture: String): List<String> = Files.readAllLines(Path.of("src/test/testData/messages/$fixture"))

    private fun translate(lines: List<String>): List<ServiceMessage> {
        val translator = CucumberMessagesTranslator(baseDir)
        return lines.flatMap(translator::translate)
            .map { requireNotNull(ServiceMessage.parse(it)) { "Not a service message: $it" } }
    }

    private fun tree(fixture: String, suitesOnly: Boolean = false): String =
        TestTree(translate(lines(fixture))).render(suitesOnly)

    @Test
    fun fullRun() {
        assertEquals(
            """
            Calculator @calculator.feature
              Adding two numbers @calculator.feature:3
                Given I have the number 2 @calculator.feature:4 [passed]
                When I add 3 @calculator.feature:5 [passed]
                Then the result is 5 @calculator.feature:6 [passed]
              A failing addition @calculator.feature:8
                Given I have the number 2 @calculator.feature:9 [passed]
                When I add 2 @calculator.feature:10 [passed]
                Then the result is 5 @calculator.feature:11 [failed: AssertionError: Expected values to be strictly equal:]
              An undefined step @calculator.feature:13
                Given I have the number 1 @calculator.feature:14 [passed]
                When I divide by zero @calculator.feature:15 [ignored: Undefined step: When I divide by zero]
              Adding many numbers @calculator.feature:17
                1, 1, 2 @calculator.feature:24
                  Given I have the number 1 @calculator.feature:24:1 [passed]
                  When I add 1 @calculator.feature:24:1 [passed]
                  Then the result is 2 @calculator.feature:24:1 [passed]
                2, 2, 5 @calculator.feature:25
                  Given I have the number 2 @calculator.feature:25:1 [passed]
                  When I add 2 @calculator.feature:25:1 [passed]
                  Then the result is 5 @calculator.feature:25:1 [failed: AssertionError: Expected values to be strictly equal:]
            """.trimIndent(),
            tree("full-run.ndjson"),
        )
    }

    @Test
    fun wholeTreeIsBuiltBeforeAnythingRuns() {
        val messages = translate(lines("full-run.ndjson"))
        val firstRunning = messages.indexOfFirst {
            it.messageName in listOf("testSuiteStarted", "testStarted") && it.attributes["running"] != "false"
        }
        val prebuilt = messages.take(firstRunning).count { it.attributes["running"] == "false" }
        assertEquals(TestTree(messages).nodes().size - 1, prebuilt)
        val count = messages.first { it.messageName == "testCount" }
        assertEquals("14", count.attributes["count"])
    }

    @Test
    fun scenariosAndExampleRowsAreTaggedForRerun() {
        val tagged = translate(lines("full-run.ndjson"))
            .filter { it.attributes["metainfo"] == CucumberMessagesTranslator.CASE_METAINFO }
            .map { it.attributes["name"] }
        // The outline itself and the feature are not rerun units
        assertEquals(listOf("Adding two numbers", "A failing addition", "An undefined step", "1, 1, 2", "2, 2, 5"), tagged)
    }

    @Test
    fun failureCarriesStackTraceAndDuration() {
        val messages = translate(lines("full-run.ndjson"))
        val failed = messages.first { it.messageName == "testFailed" }
        assertTrue(failed.attributes["details"].orEmpty().contains("steps.js:6"))
        messages.filter { it.messageName in listOf("testFinished", "testFailed") }.forEach {
            assertTrue(it.attributes["duration"]!!.toLong() >= 0)
        }
    }

    @Test
    fun singleExampleRow() {
        assertEquals(
            """
            Calculator @calculator.feature
              Adding many numbers @calculator.feature:17
                2, 2, 5 @calculator.feature:25
                  Given I have the number 2 @calculator.feature:25:1 [passed]
                  When I add 2 @calculator.feature:25:1 [passed]
                  Then the result is 5 @calculator.feature:25:1 [failed: AssertionError: Expected values to be strictly equal:]
            """.trimIndent(),
            tree("single-example.ndjson"),
        )
    }

    @Test
    fun retriesAddAttemptsAndOnlyTheLastOneFails() {
        val messages = translate(lines("retry.ndjson"))
        assertEquals(
            """
            Calculator @calculator.feature
              A failing addition @calculator.feature:8
                Given I have the number 2 @calculator.feature:9 [passed]
                When I add 2 @calculator.feature:10 [passed]
                Then the result is 5 @calculator.feature:11 [ignored: Failed, retried: AssertionError: Expected values to be strictly equal:]
                Attempt 2
                  Given I have the number 2 @calculator.feature:9 [passed]
                  When I add 2 @calculator.feature:10 [passed]
                  Then the result is 5 @calculator.feature:11 [failed: AssertionError: Expected values to be strictly equal:]
            """.trimIndent(),
            TestTree(messages).render(),
        )
        val retried = TestTree(messages).nodes().first { it.result.orEmpty().startsWith("ignored: Failed, retried") }
        assertTrue(retried.output.contains("steps.js:6"))
    }

    @Test
    fun flakyScenarioThatPassesOnRetryHasNoFailure() {
        assertEquals(
            """
            Retries @flaky.feature
              Fails on the first attempt only @flaky.feature:6
                Then this step fails on the first attempt only @flaky.feature:7 [ignored: Failed, retried: Error: Flaky failure on attempt 1]
                Attempt 2
                  Then this step fails on the first attempt only @flaky.feature:7 [passed]
            """.trimIndent(),
            tree("flaky-retry.ndjson"),
        )
    }

    private fun scenarioCounts(lines: List<String>): ScenarioCounts.Snapshot {
        val counts = ScenarioCounts()
        val translator = CucumberMessagesTranslator(baseDir, counts)
        lines.forEach(translator::translate)
        return counts.snapshot
    }

    @Test
    fun countsScenariosAndExampleRows() {
        assertEquals(ScenarioCounts.Snapshot(total = 5, finished = 5, passed = 2, failed = 2, done = true),
            scenarioCounts(lines("full-run.ndjson")))
        assertEquals(ScenarioCounts.Snapshot(total = 1, finished = 1, passed = 0, failed = 1, done = true),
            scenarioCounts(lines("single-example.ndjson")))
    }

    @Test
    fun retriedScenarioCountsOnceWithItsLastAttempt() {
        assertEquals(ScenarioCounts.Snapshot(total = 1, finished = 1, passed = 0, failed = 1, done = true),
            scenarioCounts(lines("retry.ndjson")))
        assertEquals(ScenarioCounts.Snapshot(total = 1, finished = 1, passed = 1, failed = 0, done = true),
            scenarioCounts(lines("flaky-retry.ndjson")))
    }

    @Test
    fun scenariosThatNeverStartCountAsIgnored() {
        val all = lines("full-run.ndjson")
        val firstFinished = all.indexOfFirst { it.startsWith("""{"testCaseFinished"""") }
        val counts = scenarioCounts(all.take(firstFinished + 1) + all.last { it.startsWith("""{"testRunFinished"""") })
        assertEquals(ScenarioCounts.Snapshot(total = 5, finished = 1, passed = 1, failed = 0, done = true), counts)
        assertEquals(4, counts.ignored)
    }

    @Test
    fun scenarioCountText() {
        // The scenario that is running is counted
        assertEquals("Scenarios: 2 of 5", ScenarioCounts.Snapshot(total = 5, finished = 1, passed = 1).text())
        assertEquals("Scenarios: 5 of 5", ScenarioCounts.Snapshot(total = 5, finished = 5, passed = 5).text())
        assertEquals("Scenarios failed: 1, passed: 3, ignored: 1 of 5 scenarios",
            ScenarioCounts.Snapshot(total = 5, finished = 5, passed = 3, failed = 1, done = true).text())
        assertEquals("Scenarios passed: 5 of 5 scenarios",
            ScenarioCounts.Snapshot(total = 5, finished = 5, passed = 5, done = true).text())
        assertEquals("Scenarios failed: 1 of 1 scenario",
            ScenarioCounts.Snapshot(total = 1, finished = 1, failed = 1, done = true).text())
    }

    @Test
    fun rulesAndBackgrounds() {
        assertEquals(
            """
            Rules @rules.feature
              Scenario before any Rule @rules.feature:7
                Background @rules.feature:4
                  Given a calculator @rules.feature:5 [passed]
                Given the number 1 @rules.feature:8 [passed]
                When I add them @rules.feature:9 [passed]
                Then the result is 1 @rules.feature:10 [passed]
              Addition @rules.feature:12
                Scenario inside a Rule @rules.feature:16
                  Background @rules.feature:4
                    Given a calculator @rules.feature:5 [passed]
                    Given the number 10 @rules.feature:14 [passed]
                  Given the number 5 @rules.feature:17 [passed]
                  When I add them @rules.feature:18 [passed]
                  Then the result is 15 @rules.feature:19 [passed]
                Outline inside a Rule @rules.feature:21
                  1, 11 @rules.feature:28
                    Background @rules.feature:4
                      Given a calculator @rules.feature:5 [passed]
                      Given the number 10 @rules.feature:14 [passed]
                    Given the number 1 @rules.feature:28:1 [passed]
                    When I add them @rules.feature:28:1 [passed]
                    Then the result is 11 @rules.feature:28:1 [passed]
                  2, 12 @rules.feature:29
                    Background @rules.feature:4
                      Given a calculator @rules.feature:5 [passed]
                      Given the number 10 @rules.feature:14 [passed]
                    Given the number 2 @rules.feature:29:1 [passed]
                    When I add them @rules.feature:29:1 [passed]
                    Then the result is 12 @rules.feature:29:1 [passed]
              Multiplication @rules.feature:31
                Example inside a second Rule @rules.feature:33
                  Background @rules.feature:4
                    Given a calculator @rules.feature:5 [passed]
                  Given the number 3 @rules.feature:34 [passed]
                  And the number 4 @rules.feature:35 [passed]
                  When I multiply them @rules.feature:36 [passed]
                  Then the result is 12 @rules.feature:37 [passed]
            """.trimIndent(),
            tree("rules.ndjson"),
        )
    }

    @Test
    fun backgroundFinishesBeforeTheScenarioSteps() {
        val messages = translate(lines("rules.ndjson"))
        fun nodeId(name: String) = messages.first { it.attributes["running"] == "false" && it.attributes["name"] == name }
            .attributes["nodeId"]
        val background = nodeId("Background")
        val firstOwnStep = nodeId("Given the number 1")
        val backgroundFinished = messages.indexOfFirst {
            it.messageName == "testSuiteFinished" && it.attributes["nodeId"] == background
        }
        val stepStarted = messages.indexOfFirst {
            it.messageName == "testStarted" && it.attributes["running"] != "false" && it.attributes["nodeId"] == firstOwnStep
        }
        assertTrue("Background did not finish", backgroundFinished >= 0)
        assertTrue("Background finished at $backgroundFinished, after the step started at $stepStarted", backgroundFinished < stepStarted)
    }

    @Test
    fun outlineShapes() {
        // Examples tables get a node only when an outline has several; a row too wide for a name makes its whole table Example #k
        assertEquals(
            """
            Scenario Outlines @outlines.feature
              Add <a> and <b> @outlines.feature:4
                1, 2, 3 @outlines.feature:12
                2, 2, 4 @outlines.feature:13
                5, 5, 99 @outlines.feature:14
              Scenario Template and Scenarios keywords are aliases @outlines.feature:16
                8 @outlines.feature:23
              Several named and tagged Examples tables @outlines.feature:25
                Small numbers @outlines.feature:32
                  1, 1, 1 @outlines.feature:34
                  2, 3, 6 @outlines.feature:35
                Large numbers @outlines.feature:38
                  100, 100, 10000 @outlines.feature:40
                Examples @outlines.feature:42
                  0, 9, 0 @outlines.feature:44
              Placeholders inside step arguments @outlines.feature:46
                1, 2, 3, 9 @outlines.feature:58
              Duplicate rows produce identically named tests @outlines.feature:60
                1 @outlines.feature:67
                1 @outlines.feature:68
              Name without placeholders @outlines.feature:70
                3 @outlines.feature:77
                4 @outlines.feature:78
              No Examples at all (runs once with <n> unreplaced, so undefined) @outlines.feature:86
              A wide Examples row (every row of its table shown as Example #k in the test tree) @outlines.feature:89
                Example #1 @outlines.feature:96
                Example #2 @outlines.feature:97
            """.trimIndent(),
            tree("outlines.ndjson", suitesOnly = true),
        )
    }

    @Test
    fun failingHooksGetNodes() {
        assertEquals(
            """
            Failing hooks @hooks.feature
              Before hook fails @hooks.feature:5
                Given the number 1 @hooks.feature:6 [ignored: Skipped]
                And the number 2 @hooks.feature:7 [ignored: Skipped]
                Before hook @hooks.js:11 [failed: Error: Before hook failed on purpose]
              After hook fails @hooks.feature:10
                Given the number 1 @hooks.feature:11 [passed]
                And the number 2 @hooks.feature:12 [passed]
                After hook @hooks.js:15 [failed: Error: After hook failed on purpose]
            """.trimIndent(),
            tree("hooks.ndjson"),
        )
    }

    @Test
    fun statuses() {
        assertEquals(
            """
            Result statuses @statuses.feature
              Passed @statuses.feature:4
                Given a passing step @statuses.feature:5 [passed]
              Failed assertion @statuses.feature:7
                Given a passing step @statuses.feature:8 [passed]
                Then this step fails @statuses.feature:9 [failed: AssertionError: the answer should be 42]
                And a passing step @statuses.feature:10 [ignored: Skipped]
              Failed with a plain error @statuses.feature:12
                Then this step throws a plain error @statuses.feature:13 [failed: Error: Something went wrong]
              Pending @statuses.feature:15
                Given a passing step @statuses.feature:16 [passed]
                Then this step is pending @statuses.feature:17 [ignored: Pending step: Then this step is pending]
                And a passing step @statuses.feature:18 [ignored: Skipped]
              Undefined @statuses.feature:20
                Given a passing step @statuses.feature:21 [passed]
                Then this step has no definition @statuses.feature:22 [ignored: Undefined step: Then this step has no definition]
              Ambiguous @statuses.feature:24
                Then an ambiguous step @statuses.feature:25 [failed: Ambiguous step: Then an ambiguous step]
              Skipped @statuses.feature:27
                Given a passing step @statuses.feature:28 [passed]
                Then this step is skipped @statuses.feature:29 [ignored: Skipped]
              Slow @statuses.feature:31
                Then this step takes 1500 ms @statuses.feature:32 [passed]
            """.trimIndent(),
            tree("statuses.ndjson"),
        )
    }

    @Test
    fun testsThatNeverStartAreShownAsNotRun() {
        // A run that ends after the first test case, e.g. because it was stopped
        val all = lines("full-run.ndjson")
        val firstFinished = all.indexOfFirst { it.startsWith("""{"testCaseFinished"""") }
        val truncated = all.take(firstFinished + 1) + all.last { it.startsWith("""{"testRunFinished"""") }
        assertEquals(
            """
            Calculator @calculator.feature
              Adding two numbers @calculator.feature:3
                Given I have the number 2 @calculator.feature:4 [passed]
                When I add 3 @calculator.feature:5 [passed]
                Then the result is 5 @calculator.feature:6 [passed]
              A failing addition @calculator.feature:8 [ignored: Not run]
                Given I have the number 2 @calculator.feature:9 [ignored: Not run]
                When I add 2 @calculator.feature:10 [ignored: Not run]
                Then the result is 5 @calculator.feature:11 [ignored: Not run]
              An undefined step @calculator.feature:13 [ignored: Not run]
                Given I have the number 1 @calculator.feature:14 [ignored: Not run]
                When I divide by zero @calculator.feature:15 [ignored: Not run]
              Adding many numbers @calculator.feature:17 [ignored: Not run]
                1, 1, 2 @calculator.feature:24 [ignored: Not run]
                  Given I have the number 1 @calculator.feature:24:1 [ignored: Not run]
                  When I add 1 @calculator.feature:24:1 [ignored: Not run]
                  Then the result is 2 @calculator.feature:24:1 [ignored: Not run]
                2, 2, 5 @calculator.feature:25 [ignored: Not run]
                  Given I have the number 2 @calculator.feature:25:1 [ignored: Not run]
                  When I add 2 @calculator.feature:25:1 [ignored: Not run]
                  Then the result is 5 @calculator.feature:25:1 [ignored: Not run]
            """.trimIndent(),
            TestTree(translate(truncated)).render(),
        )
    }

    @Test
    fun attachmentsBecomeStepOutput() {
        // Captured from playground/js/features/output.feature, scenario "Logs and attachments"
        val output = TestTree(translate(lines("attachments.ndjson"))).nodes()
            .filter { it.output.isNotEmpty() }
            .map { "${it.name} | ${it.output}" }
        assertEquals(
            listOf(
                "Given the step logs \"hello from this.log\" | hello from this.log\n",
                "And the step attaches a text document | A plain text attachment\nwith two lines\n",
                "And the step attaches JSON | {\n  \"numbers\": [\n    1,\n    2,\n    3\n  ],\n  \"ok\": true\n}\n",
                "And the step attaches a PNG image | Attachment: image/png (70 bytes)\n",
            ),
            output,
        )
    }

    @Test
    fun findsMessagesAfterOutputWithoutNewline() {
        val translator = CucumberMessagesTranslator(baseDir)
        assertEquals(0, translator.envelopeStart("""{"testRunStarted":{"id":"1"}}"""))
        assertEquals(7, translator.envelopeStart("""partial{"testStepFinished":{"id":"1"}}"""))
        assertEquals(-1, translator.envelopeStart("""{"user":{"json":"logged by a step"}}"""))
        assertEquals(-1, translator.envelopeStart("plain output"))
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

/**
 * Applies id-based service messages the way the SM runner's id-based convertor does, and fails on the
 * transitions it would reject: unknown or finished parents, starting a node twice, nodes left unfinished.
 */
private class TestTree(messages: List<ServiceMessage>) {
    class Node(val name: String, val parent: Node?, val isSuite: Boolean, val location: String?) {
        val children = mutableListOf<Node>()
        var state = "pending"
        var result: String? = null
        val output = StringBuilder()
    }

    val root = Node("root", null, true, null).also { it.state = "running" }
    private val nodes = mutableMapOf("0" to root)

    init {
        for (message in messages) apply(message)
        nodes.values.filter { it.state != "finished" && it !== root }.forEach { error("Not finished: ${it.name}") }
    }

    private fun apply(message: ServiceMessage) {
        val attributes = message.attributes
        val id = attributes["nodeId"]
        when (message.messageName) {
            "testCount" -> return
            "testSuiteStarted", "testStarted" -> {
                val running = attributes["running"]?.toBooleanStrict() ?: true
                val existing = nodes[id!!]
                if (existing != null) {
                    check(running && existing.state == "pending") { "Cannot start ${existing.name} in state ${existing.state}" }
                    var current: Node? = existing
                    while (current != null && current.state == "pending") {
                        current.state = "running"
                        current = current.parent
                    }
                    return
                }
                val parent = checkNotNull(nodes[attributes["parentNodeId"]]) { "Unknown parent of ${attributes["name"]}" }
                check(parent.state != "finished") { "Parent ${parent.name} already finished" }
                val node = Node(attributes["name"]!!, parent, message.messageName == "testSuiteStarted", attributes["locationHint"])
                if (running) {
                    var current: Node? = node
                    while (current != null && current.state == "pending") {
                        current.state = "running"
                        current = current.parent
                    }
                }
                parent.children += node
                nodes[id] = node
            }
            "testStdOut", "testStdErr" -> node(id).output.append(attributes["out"])
            else -> {
                val node = node(id)
                check(node.state != "finished") { "${node.name} finished twice" }
                if (message.messageName != "testIgnored") check(node.state == "running") { "${node.name} finished without running" }
                node.state = "finished"
                node.result = when (message.messageName) {
                    "testFinished" -> "passed"
                    "testSuiteFinished" -> null
                    "testIgnored" -> "ignored: ${attributes["message"]}"
                    "testFailed" -> "failed: ${attributes["message"]}"
                    else -> error("Unexpected ${message.messageName}")
                }
            }
        }
    }

    private fun node(id: String?): Node = checkNotNull(nodes[id]) { "Unknown node $id" }

    fun nodes(): Collection<Node> = nodes.values

    /** One line per node: indented name, location file:line, and the result for tests or ignored suites. */
    fun render(suitesOnly: Boolean = false): String = buildString {
        fun visit(node: Node, depth: Int) {
            if (suitesOnly && !node.isSuite) return
            append("  ".repeat(depth)).append(node.name)
            node.location?.substringAfterLast('/')?.let { append(" @").append(it) }
            if (!suitesOnly) node.result?.let { append(" [").append(it).append(']') }
            append('\n')
            node.children.forEach { visit(it, depth + 1) }
        }
        root.children.forEach { visit(it, 0) }
    }.trimEnd()
}
