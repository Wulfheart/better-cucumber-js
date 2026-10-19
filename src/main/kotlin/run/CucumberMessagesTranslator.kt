package com.wulfindustries.plugins.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.wulfindustries.plugins.MyMessageBundle
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64

/**
 * Translates Cucumber Messages NDJSON (cucumber-js `--format message`) into TeamCity service messages
 * for the SM test runner. One instance per run; feed it the messages in order.
 *
 * The tree is id-based (the console properties enable `isIdBasedTestTree`) and is built in full before the first
 * test case starts, so tests that have not run yet show up as pending:
 * Feature > Rule > Scenario > Background > steps, and Feature > Rule > Scenario Outline > Examples > row >
 * Background > steps. Rule, Examples (only for outlines with several tables) and Background are left out when absent.
 * Steps are the tests. Hooks get a node only when they do not pass, and each retry of a test case adds an
 * `Attempt N` suite under the scenario.
 *
 * Events are routed by `testCaseStartedId`, because `--parallel` interleaves the messages of different test cases.
 *
 * @param baseDir directory that relative feature URIs in the messages are resolved against (the run's working dir)
 */
class CucumberMessagesTranslator(private val baseDir: Path) {
    private enum class State { PENDING, RUNNING, FINISHED }

    private class Node(
        val id: Int,
        val parent: Node?,
        val name: String,
        val isSuite: Boolean,
        /** Finishes by itself once all its children have finished (feature, rule, outline, Examples and Background suites). */
        val autoFinish: Boolean,
    ) {
        val children = mutableListOf<Node>()
        var state = State.PENDING
    }

    private class PickleStep(val text: String, val astNodeId: String?)
    private class Pickle(val uri: String, val name: String, val astNodeIds: List<String>, val steps: Map<String, PickleStep>)
    private class TestStep(val id: String, val pickleStepId: String?, val hookId: String?)
    private class TestCase(val id: String, val pickleId: String, val steps: List<TestStep>)
    private class Hook(val type: String?, val uri: String?, val line: Int?)

    /** Step nodes of one attempt, keyed by test step id, plus the Background suite they may live in. */
    private class StepNodes(val byTestStepId: MutableMap<String, Node>, val background: Node?)

    private class CaseTree(val caseNode: Node, val firstAttempt: StepNodes)

    private class Failure(val node: Node, val message: String, val details: String?, val durationMillis: Long)

    private class Attempt(val testCase: TestCase, val caseNode: Node, val container: Node, val steps: StepNodes) {
        /** Suites that end with this attempt, innermost first. */
        val ownedSuites = listOfNotNull(steps.background, container.takeIf { it !== caseNode })
        val failures = mutableListOf<Failure>()
        var runningStep: Node? = null
    }

    private enum class Status { PASSED, SKIPPED, PENDING, UNDEFINED, AMBIGUOUS, FAILED, UNKNOWN }

    private val index = GherkinIndex()
    private val pickles = mutableMapOf<String, Pickle>()
    private val hooks = mutableMapOf<String, Hook>()
    private val testCases = mutableMapOf<String, TestCase>()

    private var treeBuilt = false
    private var lastNodeId = 0
    private val roots = mutableListOf<Node>()
    private val nodesByKey = mutableMapOf<String, Node>()
    private val caseTrees = mutableMapOf<String, CaseTree>()
    private val attempts = mutableMapOf<String, Attempt>()

    /**
     * Index where a Cucumber message starts in a stdout [line], or -1 if it has none. A message can follow output
     * that a step wrote without a trailing newline, so it is not always at the start of the line.
     */
    fun envelopeStart(line: String): Int = ENVELOPE_START.find(line)?.range?.first ?: -1

    /**
     * Wraps process output that is not a Cucumber message so it lands under the running step, or under the scenario
     * while a hook runs. Returns null when no test case is running, or when several are (`--parallel`), because then
     * there is no telling who wrote it.
     */
    fun outputMessage(text: String, isStdErr: Boolean): String? {
        val attempt = attempts.values.singleOrNull() ?: return null
        val node = attempt.runningStep ?: attempt.container
        val message = if (isStdErr) ServiceMessageBuilder.testStdErr(node.name) else ServiceMessageBuilder.testStdOut(node.name)
        return message.node(node).addAttribute("out", text).toString()
    }

    /** Returns zero or more complete `##teamcity[...]` lines for one NDJSON line. */
    fun translate(ndjsonLine: String): List<String> {
        val envelope = parse(ndjsonLine) ?: return emptyList()
        val out = mutableListOf<ServiceMessageBuilder>()
        envelope.obj("gherkinDocument")?.let(index::add)
        envelope.obj("pickle")?.let(::onPickle)
        envelope.obj("hook")?.let(::onHook)
        envelope.obj("testCase")?.let(::onTestCase)
        envelope.obj("testCaseStarted")?.let { onTestCaseStarted(it, out) }
        envelope.obj("testStepStarted")?.let { onTestStepStarted(it, out) }
        envelope.obj("testStepFinished")?.let { onTestStepFinished(it, out) }
        envelope.obj("attachment")?.let { onAttachment(it, out) }
        envelope.obj("testCaseFinished")?.let { onTestCaseFinished(it, out) }
        envelope.obj("testRunFinished")?.let { onTestRunFinished(out) }
        return out.map(ServiceMessageBuilder::toString)
    }

    private fun parse(line: String): JsonObject? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            JsonParser.parseString(trimmed).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun onPickle(pickle: JsonObject) {
        val id = pickle.str("id") ?: return
        val uri = pickle.str("uri") ?: return
        val steps = pickle.array("steps")?.mapNotNull { step ->
            val stepObj = step.asJsonObject
            val stepId = stepObj.str("id") ?: return@mapNotNull null
            stepId to PickleStep(stepObj.str("text").orEmpty(), stepObj.array("astNodeIds")?.firstOrNull()?.asString)
        }.orEmpty().toMap()
        val astNodeIds = pickle.array("astNodeIds")?.map { it.asString }.orEmpty()
        pickles[id] = Pickle(uri, pickle.str("name").orEmpty(), astNodeIds, steps)
    }

    private fun onHook(hook: JsonObject) {
        val id = hook.str("id") ?: return
        val source = hook.obj("sourceReference")
        hooks[id] = Hook(hook.str("type"), source?.str("uri"), source?.obj("location")?.get("line")?.asInt)
    }

    private fun onTestCase(testCase: JsonObject) {
        val id = testCase.str("id") ?: return
        val pickleId = testCase.str("pickleId") ?: return
        val steps = testCase.array("testSteps")?.mapNotNull { step ->
            val stepObj = step.asJsonObject
            stepObj.str("id")?.let { TestStep(it, stepObj.str("pickleStepId"), stepObj.str("hookId")) }
        }.orEmpty()
        testCases[id] = TestCase(id, pickleId, steps)
    }

    /**
     * Registers every test case that will run. cucumber-js emits all `testCase` messages before the first
     * `testCaseStarted`, also with `--parallel`. Pickles are no use here: there is one for every parsed scenario,
     * including the ones filtered out.
     */
    private fun buildTree(out: MutableList<ServiceMessageBuilder>) {
        if (treeBuilt) return
        treeBuilt = true
        for (testCase in testCases.values) {
            val pickle = pickles[testCase.pickleId] ?: continue
            val caseNode = caseNode(testCase.pickleId, pickle, out)
            caseTrees[testCase.id] = CaseTree(caseNode, addSteps(caseNode, testCase, pickle, out))
        }
        val stepCount = caseTrees.values.sumOf { it.firstAttempt.byTestStepId.size }
        out += ServiceMessageBuilder("testCount").addAttribute("count", stepCount.toString())
    }

    /** The suite of a scenario or example row, with its feature, rule, outline and Examples suites. */
    private fun caseNode(pickleId: String, pickle: Pickle, out: MutableList<ServiceMessageBuilder>): Node {
        val path = absolutePath(pickle.uri)
        val feature = index.feature(pickle.uri)
        var parent = register("feature:${pickle.uri}", null, feature?.name?.ifBlank { null } ?: pickle.uri, true,
            CucumberJsLocations.url(path), true, out)
        val scenario = pickle.astNodeIds.firstOrNull()?.let(index::node)
        scenario?.parent?.takeIf { it.kind == GherkinIndex.Kind.RULE }?.let { rule ->
            parent = register(rule.id, parent, rule.label, true, CucumberJsLocations.url(path, rule.line), true, out)
        }
        // For Scenario Outline examples the last AST node is the example table row
        val row = pickle.astNodeIds.getOrNull(1)?.let(index::node)?.takeIf { it.kind == GherkinIndex.Kind.ROW }
        if (scenario == null || row == null) {
            val name = pickle.name.ifBlank { scenario?.label.orEmpty() }
            return register("pickle:$pickleId", parent, name, true, CucumberJsLocations.url(path, scenario?.line), false, out,
                metainfo = CASE_METAINFO)
        }
        parent = register(scenario.id, parent, scenario.label, true, CucumberJsLocations.url(path, scenario.line), true, out)
        val examples = row.parent
        if (examples != null && scenario.hasSeveralExamplesTables) {
            parent = register(examples.id, parent, examples.label, true, CucumberJsLocations.url(path, examples.line), true, out)
        }
        return register("pickle:$pickleId", parent, rowName(row), true, CucumberJsLocations.url(path, row.line), false, out,
            metainfo = CASE_METAINFO)
    }

    private fun rowName(row: GherkinIndex.AstNode): String {
        val values = row.cells.joinToString(", ")
        return if (values.length <= MAX_ROW_NAME_LENGTH) values else MyMessageBundle.message("test.tree.example", row.rowNumber)
    }

    /** Adds a node for every pickle step of [testCase] under [parent]; background steps go into a Background suite. */
    private fun addSteps(parent: Node, testCase: TestCase, pickle: Pickle, out: MutableList<ServiceMessageBuilder>): StepNodes {
        val path = absolutePath(pickle.uri)
        val byTestStepId = mutableMapOf<String, Node>()
        var background: Node? = null
        for (testStep in testCase.steps) {
            val pickleStep = testStep.pickleStepId?.let(pickle.steps::get) ?: continue
            val astStep = pickleStep.astNodeId?.let(index::node)
            val astBackground = astStep?.parent?.takeIf { it.kind == GherkinIndex.Kind.BACKGROUND }
            val stepParent = if (astBackground == null) parent else {
                background ?: register(null, parent, MyMessageBundle.message("test.tree.background"), true,
                    CucumberJsLocations.url(path, astBackground.line), true, out).also { background = it }
            }
            val name = astStep?.keyword.orEmpty() + pickleStep.text
            byTestStepId[testStep.id] =
                register(null, stepParent, name, false, CucumberJsLocations.url(path, astStep?.line), false, out)
        }
        return StepNodes(byTestStepId, background)
    }

    /**
     * Adds a node to the tree, or returns the one already registered under [key]. Nodes are pending until [start]
     * unless [running] is set. [metainfo] ends up on the test proxy.
     */
    private fun register(
        key: String?,
        parent: Node?,
        name: String,
        isSuite: Boolean,
        locationHint: String?,
        autoFinish: Boolean,
        out: MutableList<ServiceMessageBuilder>,
        running: Boolean = false,
        metainfo: String? = null,
    ): Node {
        key?.let(nodesByKey::get)?.let { return it }
        val node = Node(++lastNodeId, parent, name, isSuite, autoFinish)
        parent?.children?.add(node) ?: roots.add(node)
        if (key != null) nodesByKey[key] = node
        if (running) markRunning(node)
        val message = if (isSuite) ServiceMessageBuilder.testSuiteStarted(name) else ServiceMessageBuilder.testStarted(name)
        message.node(node).addAttribute("running", running.toString())
        locationHint?.let { message.addAttribute("locationHint", it) }
        metainfo?.let { message.addAttribute("metainfo", it) }
        out += message
        return node
    }

    private fun start(node: Node, out: MutableList<ServiceMessageBuilder>) {
        if (node.state != State.PENDING) return
        markRunning(node)
        val message = if (node.isSuite) ServiceMessageBuilder.testSuiteStarted(node.name) else ServiceMessageBuilder.testStarted(node.name)
        out += message.node(node)
    }

    /** The SM runner starts pending ancestors along with a node, so mirror that here. */
    private fun markRunning(node: Node) {
        var current: Node? = node
        while (current != null && current.state == State.PENDING) {
            current.state = State.RUNNING
            current = current.parent
        }
    }

    private fun onTestCaseStarted(started: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        buildTree(out)
        val id = started.str("id") ?: return
        val testCaseId = started.str("testCaseId") ?: return
        val testCase = testCases[testCaseId] ?: return
        val tree = caseTrees[testCaseId] ?: return
        val pickle = pickles[testCase.pickleId] ?: return
        val attemptNumber = started.get("attempt")?.asInt ?: 0
        attempts[id] = if (attemptNumber == 0) {
            start(tree.caseNode, out)
            Attempt(testCase, tree.caseNode, tree.caseNode, tree.firstAttempt)
        } else {
            val name = MyMessageBundle.message("test.tree.attempt", attemptNumber + 1)
            val container = register(null, tree.caseNode, name, true, null, false, out, running = true)
            Attempt(testCase, tree.caseNode, container, addSteps(container, testCase, pickle, out))
        }
    }

    private fun onTestStepStarted(started: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val attempt = started.str("testCaseStartedId")?.let(attempts::get) ?: return
        attempt.runningStep = started.str("testStepId")?.let(attempt.steps.byTestStepId::get)?.also { start(it, out) }
    }

    private fun onTestStepFinished(finished: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val attempt = finished.str("testCaseStartedId")?.let(attempts::get) ?: return
        val testStepId = finished.str("testStepId") ?: return
        val result = finished.obj("testStepResult") ?: return
        val status = result.str("status")?.let { runCatching { Status.valueOf(it) }.getOrNull() } ?: Status.UNKNOWN
        attempt.runningStep = null
        val node = attempt.steps.byTestStepId[testStepId] ?: hookNode(attempt, testStepId, status, out) ?: return
        start(node, out)
        val duration = result.obj("duration").toMillis()
        when (status) {
            Status.PASSED -> finish(node, out, ServiceMessageBuilder.testFinished(node.name).addAttribute("duration", duration.toString()))
            Status.SKIPPED, Status.PENDING, Status.UNDEFINED -> ignore(node, statusMessage(status, node), out)
            // Reported when the test case finishes, because only then do we know whether it will be retried
            Status.FAILED, Status.AMBIGUOUS, Status.UNKNOWN -> {
                val exception = result.obj("exception")
                // cucumber-js 13 sends no message for ambiguous steps
                val fallback = if (status == Status.AMBIGUOUS) "Ambiguous step: ${node.name}" else status.name
                val fullMessage = exception?.str("stackTrace") ?: result.str("message") ?: fallback
                val headline = exception?.let { e -> listOfNotNull(e.str("type"), e.str("message")).joinToString(": ") }
                    ?.takeIf { it.isNotBlank() }
                    ?: fullMessage
                val message = headline.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: status.name
                attempt.failures += Failure(node, message, fullMessage, duration)
            }
        }
    }

    /** Hooks that pass or are skipped stay out of the tree; others get a node under the scenario or attempt. */
    private fun hookNode(attempt: Attempt, testStepId: String, status: Status, out: MutableList<ServiceMessageBuilder>): Node? {
        if (status == Status.PASSED || status == Status.SKIPPED) return null
        val hook = attempt.testCase.steps.firstOrNull { it.id == testStepId }?.hookId?.let(hooks::get)
        val name = MyMessageBundle.message(if (hook?.type == "AFTER_TEST_CASE") "test.tree.after.hook" else "test.tree.before.hook")
        val location = hook?.uri?.let { CucumberJsLocations.url(absolutePath(it), hook.line) }
        return register(null, attempt.container, name, false, location, false, out, running = true)
            .also { attempt.steps.byTestStepId[testStepId] = it }
    }

    private fun statusMessage(status: Status, node: Node): String = when (status) {
        Status.UNDEFINED -> "Undefined step: ${node.name}"
        Status.PENDING -> "Pending step: ${node.name}"
        else -> "Skipped"
    }

    /** `this.log` and `this.attach` output, shown as the step's stdout. Binary attachments get a one-line summary. */
    private fun onAttachment(attachment: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val attempt = attachment.str("testCaseStartedId")?.let(attempts::get) ?: return
        val node = attachment.str("testStepId")?.let(attempt.steps.byTestStepId::get) ?: attempt.container
        val body = attachment.str("body").orEmpty()
        val mediaType = attachment.str("mediaType").orEmpty()
        val isBase64 = attachment.str("contentEncoding") == "BASE64"
        val isText = mediaType.startsWith("text/") || mediaType == "application/json"
        val text = when {
            isText && isBase64 -> String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8)
            isText -> body
            else -> {
                val size = if (isBase64) Base64.getDecoder().decode(body).size else body.toByteArray().size
                val fileName = attachment.str("fileName")?.let { " $it" }.orEmpty()
                "Attachment: $mediaType$fileName ($size bytes)"
            }
        }
        out += ServiceMessageBuilder.testStdOut(node.name).node(node).addAttribute("out", text.removeSuffix("\n") + "\n")
    }

    private fun onTestCaseFinished(finished: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val attempt = finished.str("testCaseStartedId")?.let(attempts::remove) ?: return
        val willBeRetried = finished.get("willBeRetried")?.asBoolean == true
        for (failure in attempt.failures) {
            val node = failure.node
            if (willBeRetried) {
                failure.details?.let {
                    out += ServiceMessageBuilder.testStdErr(node.name).node(node).addAttribute("out", it.removeSuffix("\n") + "\n")
                }
                ignore(node, MyMessageBundle.message("test.tree.failed.retried", failure.message), out)
            } else {
                val failed = ServiceMessageBuilder.testFailed(node.name)
                    .addAttribute("message", failure.message)
                    .addAttribute("duration", failure.durationMillis.toString())
                failure.details?.let { failed.addAttribute("details", it) }
                finish(node, out, failed)
            }
        }
        for (node in attempt.steps.byTestStepId.values) finishLeftover(node, out)
        attempt.ownedSuites.forEach { finishSuite(it, out) }
        if (!willBeRetried) finishSuite(attempt.caseNode, out)
    }

    /** Whatever never ran is shown as not run, and suites still open are closed. */
    private fun onTestRunFinished(out: MutableList<ServiceMessageBuilder>) {
        buildTree(out)
        roots.toList().forEach { finishSubtree(it, out) }
    }

    private fun finishSubtree(node: Node, out: MutableList<ServiceMessageBuilder>) {
        node.children.toList().forEach { finishSubtree(it, out) }
        if (node.isSuite && node.state == State.RUNNING) finishSuite(node, out) else finishLeftover(node, out)
    }

    private fun finishLeftover(node: Node, out: MutableList<ServiceMessageBuilder>) {
        when (node.state) {
            State.PENDING -> ignore(node, MyMessageBundle.message("test.tree.not.run"), out)
            State.RUNNING -> finish(node, out, ServiceMessageBuilder.testFinished(node.name))
            State.FINISHED -> Unit
        }
    }

    private fun ignore(node: Node, message: String, out: MutableList<ServiceMessageBuilder>) {
        finish(node, out, ServiceMessageBuilder.testIgnored(node.name).addAttribute("message", message))
    }

    private fun finishSuite(node: Node, out: MutableList<ServiceMessageBuilder>) {
        if (node.state == State.FINISHED) return
        finish(node, out, ServiceMessageBuilder.testSuiteFinished(node.name))
    }

    /** Sends the message that ends [node], then finishes the suites around it that have nothing left to run. */
    private fun finish(node: Node, out: MutableList<ServiceMessageBuilder>, message: ServiceMessageBuilder) {
        if (node.state == State.FINISHED) return
        node.state = State.FINISHED
        out += message.node(node)
        var parent = node.parent
        while (parent != null && parent.autoFinish && parent.state == State.RUNNING &&
            parent.children.all { it.state == State.FINISHED }
        ) {
            parent.state = State.FINISHED
            out += ServiceMessageBuilder.testSuiteFinished(parent.name).node(parent)
            parent = parent.parent
        }
    }

    private fun ServiceMessageBuilder.node(node: Node): ServiceMessageBuilder =
        addAttribute("nodeId", node.id.toString()).addAttribute("parentNodeId", (node.parent?.id ?: ROOT_NODE_ID).toString())

    private fun absolutePath(uri: String): String {
        // cucumber-js reports Windows URIs with backslashes
        val path = uri.removePrefix("file://").replace('\\', '/')
        return baseDir.resolve(path).normalize().toString()
    }

    private fun JsonObject?.toMillis(): Long {
        if (this == null) return 0
        val seconds = get("seconds")?.asLong ?: 0
        val nanos = get("nanos")?.asLong ?: 0
        return seconds * 1000 + nanos / 1_000_000
    }

    companion object {
        /** Metainfo of scenario and example row suites, the unit that "Rerun Failed Tests" reruns. */
        const val CASE_METAINFO = "scenario"

        private const val ROOT_NODE_ID = 0
        private const val MAX_ROW_NAME_LENGTH = 60

        // Envelope keys from the Cucumber Messages schema
        private val ENVELOPE_KEYS = listOf(
            "attachment", "externalAttachment", "gherkinDocument", "hook", "meta", "parameterType", "parseError",
            "pickle", "source", "stepDefinition", "suggestion", "testCase", "testCaseFinished", "testCaseStarted",
            "testRunFinished", "testRunHookFinished", "testRunHookStarted", "testRunStarted", "testStepFinished",
            "testStepStarted", "undefinedParameterType",
        )
        private val ENVELOPE_START = Regex("""\{"(${ENVELOPE_KEYS.joinToString("|")})":\{""")
    }
}
