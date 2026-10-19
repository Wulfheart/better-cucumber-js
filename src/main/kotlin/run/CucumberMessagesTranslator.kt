package com.wulfindustries.plugins.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import java.nio.file.Path

/**
 * Translates Cucumber Messages NDJSON (cucumber-js `--format message`) into TeamCity service messages
 * for the SM test runner. One instance per run; feed it lines in order.
 *
 * Features become suites and pickles (scenarios, or single Scenario Outline example rows) become tests.
 *
 * @param baseDir directory that relative feature URIs in the messages are resolved against (the run's working dir)
 */
class CucumberMessagesTranslator(private val baseDir: Path) {
    private class Pickle(val name: String, val uri: String, val line: Int?)

    private class Attempt(val pickle: Pickle, val startMillis: Long) {
        var worstStatus = Status.PASSED
        var message: String? = null
        var details: String? = null
    }

    private enum class Status { PASSED, SKIPPED, PENDING, UNDEFINED, AMBIGUOUS, FAILED, UNKNOWN }

    private val featureNames = mutableMapOf<String, String>()
    private val astNodeLines = mutableMapOf<String, Int>()
    private val exampleRowValues = mutableMapOf<String, List<String>>()
    private val pickles = mutableMapOf<String, Pickle>()
    private val testCasePickles = mutableMapOf<String, String>()
    private val testStepTexts = mutableMapOf<String, String>()
    private val attempts = mutableMapOf<String, Attempt>()

    // Pickles whose testStarted was emitted and that await a final (non-retried) result
    private val runningPickles = mutableSetOf<Pickle>()
    private var openSuiteUri: String? = null

    /** Returns zero or more complete `##teamcity[...]` lines for one NDJSON line. */
    fun translate(ndjsonLine: String): List<String> {
        val envelope = parse(ndjsonLine) ?: return emptyList()
        val out = mutableListOf<ServiceMessageBuilder>()
        envelope.obj("gherkinDocument")?.let(::onGherkinDocument)
        envelope.obj("pickle")?.let(::onPickle)
        envelope.obj("testCase")?.let(::onTestCase)
        envelope.obj("testCaseStarted")?.let { onTestCaseStarted(it, out) }
        envelope.obj("testStepFinished")?.let(::onTestStepFinished)
        envelope.obj("testCaseFinished")?.let { onTestCaseFinished(it, out) }
        envelope.obj("testRunFinished")?.let { closeSuite(out) }
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

    private fun onGherkinDocument(doc: JsonObject) {
        val uri = doc.str("uri") ?: return
        val feature = doc.obj("feature") ?: return
        featureNames[uri] = feature.str("name").orEmpty()
        collectAstNodes(feature)
    }

    private fun collectAstNodes(node: JsonObject) {
        val id = node.str("id")
        val line = node.obj("location")?.get("line")?.asInt
        if (id != null && line != null) astNodeLines[id] = line
        node.array("cells")?.let { cells ->
            if (id != null) exampleRowValues[id] = cells.mapNotNull { it.asJsonObject.str("value") }
        }
        for ((key, value) in node.entrySet()) {
            if (key == "cells") continue
            when {
                value.isJsonObject -> collectAstNodes(value.asJsonObject)
                value.isJsonArray -> value.asJsonArray.filter { it.isJsonObject }.forEach { collectAstNodes(it.asJsonObject) }
            }
        }
    }

    private fun onPickle(pickle: JsonObject) {
        val id = pickle.str("id") ?: return
        val uri = pickle.str("uri") ?: return
        val astNodeIds = pickle.array("astNodeIds")?.map { it.asString }.orEmpty()
        // For Scenario Outline examples the last AST node is the example table row
        val lastNodeId = astNodeIds.lastOrNull()
        val line = pickle.obj("location")?.get("line")?.asInt ?: lastNodeId?.let(astNodeLines::get)
        var name = pickle.str("name").orEmpty()
        if (astNodeIds.size > 1) {
            exampleRowValues[lastNodeId]?.let { name = "$name (${it.joinToString(", ")})" }
        }
        pickles[id] = Pickle(name, uri, line)
        pickle.array("steps")?.forEach { step ->
            val stepObj = step.asJsonObject
            val stepId = stepObj.str("id")
            val text = stepObj.str("text")
            if (stepId != null && text != null) testStepTexts["pickleStep:$stepId"] = text
        }
    }

    private fun onTestCase(testCase: JsonObject) {
        val id = testCase.str("id") ?: return
        testCase.str("pickleId")?.let { testCasePickles[id] = it }
        testCase.array("testSteps")?.forEach { step ->
            val stepObj = step.asJsonObject
            val stepId = stepObj.str("id") ?: return@forEach
            stepObj.str("pickleStepId")?.let { pickleStepId ->
                testStepTexts["pickleStep:$pickleStepId"]?.let { testStepTexts[stepId] = it }
            }
        }
    }

    private fun onTestCaseStarted(started: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val id = started.str("id") ?: return
        val pickle = started.str("testCaseId")?.let(testCasePickles::get)?.let(pickles::get) ?: return
        attempts[id] = Attempt(pickle, started.obj("timestamp").toMillis())
        if (pickle in runningPickles) return // a retry of a test that is still shown as running

        if (openSuiteUri != pickle.uri) {
            closeSuite(out)
            openSuiteUri = pickle.uri
            out += ServiceMessageBuilder.testSuiteStarted(featureNames[pickle.uri] ?: pickle.uri)
                .addAttribute("locationHint", CucumberJsLocations.url(absolutePath(pickle.uri)))
        }
        runningPickles += pickle
        out += ServiceMessageBuilder.testStarted(pickle.name)
            .addAttribute("locationHint", CucumberJsLocations.url(absolutePath(pickle.uri), pickle.line))
    }

    private fun onTestStepFinished(finished: JsonObject) {
        val attempt = finished.str("testCaseStartedId")?.let(attempts::get) ?: return
        val result = finished.obj("testStepResult") ?: return
        val status = result.str("status")?.let { runCatching { Status.valueOf(it) }.getOrNull() } ?: Status.UNKNOWN
        if (status.ordinal <= attempt.worstStatus.ordinal) return
        attempt.worstStatus = status
        val stepText = finished.str("testStepId")?.let(testStepTexts::get)
        when (status) {
            Status.FAILED, Status.AMBIGUOUS, Status.UNKNOWN -> {
                val exception = result.obj("exception")
                val fullMessage = exception?.str("stackTrace") ?: result.str("message") ?: status.name
                val headline = exception?.let { e -> listOfNotNull(e.str("type"), e.str("message")).joinToString(": ") }
                    ?.takeIf { it.isNotBlank() }
                    ?: fullMessage
                attempt.message = headline.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: status.name
                attempt.details = fullMessage
            }
            Status.UNDEFINED -> attempt.message = "Undefined step: ${stepText ?: "?"}"
            Status.PENDING -> attempt.message = "Pending step: ${stepText ?: "?"}"
            Status.SKIPPED -> attempt.message = "Skipped"
            Status.PASSED -> Unit
        }
    }

    private fun onTestCaseFinished(finished: JsonObject, out: MutableList<ServiceMessageBuilder>) {
        val attempt = finished.str("testCaseStartedId")?.let(attempts::remove) ?: return
        if (finished.get("willBeRetried")?.asBoolean == true) return
        val pickle = attempt.pickle
        runningPickles -= pickle
        when (attempt.worstStatus) {
            Status.PASSED -> Unit
            Status.SKIPPED, Status.PENDING, Status.UNDEFINED ->
                out += ServiceMessageBuilder.testIgnored(pickle.name).addAttribute("message", attempt.message.orEmpty())
            Status.FAILED, Status.AMBIGUOUS, Status.UNKNOWN -> {
                val failed = ServiceMessageBuilder.testFailed(pickle.name).addAttribute("message", attempt.message.orEmpty())
                attempt.details?.let { failed.addAttribute("details", it) }
                out += failed
            }
        }
        val duration = (finished.obj("timestamp").toMillis() - attempt.startMillis).coerceAtLeast(0)
        out += ServiceMessageBuilder.testFinished(pickle.name).addAttribute("duration", duration.toString())
    }

    private fun closeSuite(out: MutableList<ServiceMessageBuilder>) {
        val uri = openSuiteUri ?: return
        openSuiteUri = null
        out += ServiceMessageBuilder.testSuiteFinished(featureNames[uri] ?: uri)
    }

    private fun absolutePath(uri: String): String {
        // cucumber-js reports Windows URIs with backslashes
        val path = uri.removePrefix("file://").replace('\\', '/')
        return baseDir.resolve(path).normalize().toString()
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject?.toMillis(): Long {
        if (this == null) return 0
        val seconds = get("seconds")?.asLong ?: 0
        val nanos = get("nanos")?.asLong ?: 0
        return seconds * 1000 + nanos / 1_000_000
    }
}
