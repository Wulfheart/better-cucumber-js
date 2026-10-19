package com.wulfindustries.plugins.run

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.wulfindustries.plugins.run.CucumberJsCommandLine.FeatureLocation

/**
 * [ScenarioAnchor]s for the scenarios and example rows of one test run, i.e. the suites tagged with
 * [CucumberMessagesTranslator.CASE_METAINFO]. They are created as soon as the first scenario starts, when the whole tree
 * is already registered and its lines still match the files cucumber-js parsed.
 *
 * Subscribed to the project-wide [com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener.TEST_STATUS],
 * so it ignores suites outside of [root].
 */
class ScenarioAnchors(private val project: Project, private val root: SMTestProxy) : SMTRunnerEventsAdapter(), Disposable {
    private val anchors = mutableMapOf<AbstractTestProxy, ScenarioAnchor>()
    private var disposed = false

    @Synchronized
    override fun onSuiteStarted(suite: SMTestProxy) {
        if (disposed || !isCase(suite) || suite in anchors) return
        if (generateSequence(suite) { it.parent }.last() !== root) return
        runReadActionBlocking {
            for (case in root.allTests) {
                if (!isCase(case) || case in anchors) continue
                val (path, line) = case.locationUrl?.let(CucumberJsLocations::parse) ?: continue
                line?.let { ScenarioAnchor.create(project, path, it) }?.let { anchors[case] = it }
            }
        }
    }

    /**
     * Where the scenarios and example rows that [tests] belong to are now, one entry per feature file. Scenarios that
     * were deleted since are left out; those that could not be anchored keep the line they had in the run.
     */
    @Synchronized
    fun locations(tests: List<AbstractTestProxy>): List<FeatureLocation> = runReadActionBlocking {
        tests.mapNotNull(::caseOf).distinct().mapNotNull { case ->
            val anchor = anchors[case]
            if (anchor != null) {
                anchor.line(project)?.let { anchor.featurePath to it }
            } else {
                case.locationUrl?.let(CucumberJsLocations::parse)?.let { (path, line) -> line?.let { path to it } }
            }
        }.groupBy({ it.first }, { it.second }).map { (path, lines) -> FeatureLocation(path, lines.distinct().sorted()) }
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        anchors.values.forEach(ScenarioAnchor::dispose)
        anchors.clear()
    }

    private fun caseOf(test: AbstractTestProxy): AbstractTestProxy? = generateSequence(test) { it.parent }.firstOrNull(::isCase)

    private fun isCase(test: AbstractTestProxy): Boolean =
        (test as? SMTestProxy)?.metainfo == CucumberMessagesTranslator.CASE_METAINFO
}
