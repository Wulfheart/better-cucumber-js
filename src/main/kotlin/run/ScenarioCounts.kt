package com.wulfindustries.plugins.run

import com.wulfindustries.plugins.MyMessageBundle

/**
 * How many scenarios and pickles of one run started, passed and failed, for the label next to the test runner's
 * status line. A pickle is what cucumber-js runs: a plain scenario has one, a Scenario Outline one per Examples row.
 * The outline counts as one scenario, finished with its last pickle. A retried pickle counts once, with its last
 * attempt. Written by the translator on the process output thread, read on the EDT.
 */
class ScenarioCounts {
    data class Tally(
        val total: Int = 0,
        val started: Int = 0,
        val finished: Int = 0,
        val passed: Int = 0,
        val failed: Int = 0,
    ) {
        /** Started and not finished yet; more than one with `--parallel`. */
        val running: Int get() = started - finished

        /** Skipped, pending, undefined or never ran. */
        val ignored: Int get() = total - passed - failed
    }

    data class Snapshot(
        val scenarios: Tally = Tally(),
        val pickles: Tally = Tally(),
        val done: Boolean = false,
    ) {
        /** Pickles not finished yet, including the ones running. */
        val left: Int get() = pickles.total - pickles.finished

        /**
         * `Scenarios: 4 of 4 · Pickles: 4 of 7 · 4 left` while running, counting the ones that run, then the outcome
         * in the platform's wording, e.g. `Scenarios failed: 1, passed: 3 of 4 · Pickles failed: 1, passed: 6 of 7`.
         */
        fun text(): String {
            val parts = listOf("scenarios" to scenarios, "pickles" to pickles).map { (kind, tally) ->
                if (done) MyMessageBundle.message("test.status.$kind.done", outcome(tally), tally.total)
                else MyMessageBundle.message("test.status.$kind.running", tally.finished + tally.running, tally.total)
            }
            val left = if (!done && left > 0) listOf(MyMessageBundle.message("test.status.counts.left", left)) else emptyList()
            return (parts + left).joinToString(" ${MyMessageBundle.message("test.status.separator")} ")
        }

        private fun outcome(tally: Tally): String = listOf(
            "test.status.counts.failed" to tally.failed,
            "test.status.counts.passed" to tally.passed,
            "test.status.counts.ignored" to tally.ignored,
        ).filter { it.second > 0 }.joinToString(", ") { MyMessageBundle.message(it.first, it.second) }
    }

    /** The pickles of one scenario. */
    private class Group(val pickles: Int) {
        var started = 0
        var finished = 0
        var failed = false
        var allPassed = true
    }

    private var groups = emptyMap<String, Group>()

    @Volatile
    var snapshot = Snapshot()
        private set

    /** Called after every change, on the thread that made it. */
    @Volatile
    var onChange: (() -> Unit)? = null

    /** [scenarioOfPickle] has the scenario's key for every pickle that will run; an outline's rows share one. */
    fun start(scenarioOfPickle: Collection<String>) = update {
        groups = scenarioOfPickle.groupingBy { it }.eachCount().mapValues { Group(it.value) }
        Snapshot(Tally(groups.size), Tally(scenarioOfPickle.size))
    }

    fun started(scenario: String) = update {
        val group = groups[scenario]
        val pickles = it.pickles.copy(started = it.pickles.started + 1)
        if (group == null || group.started++ > 0) it.copy(pickles = pickles)
        else it.copy(scenarios = it.scenarios.copy(started = it.scenarios.started + 1), pickles = pickles)
    }

    fun finished(scenario: String, failed: Boolean, passed: Boolean) = update {
        val pickles = it.pickles.counted(failed, passed)
        val group = groups[scenario] ?: return@update it.copy(pickles = pickles)
        group.failed = group.failed || failed
        group.allPassed = group.allPassed && passed && !failed
        if (++group.finished < group.pickles) it.copy(pickles = pickles)
        else it.copy(scenarios = it.scenarios.counted(group.failed, group.allPassed), pickles = pickles)
    }

    /** Whatever has not finished by now counts as ignored. */
    fun runFinished() = update { it.copy(done = true) }

    private fun Tally.counted(failed: Boolean, passed: Boolean): Tally {
        val counted = copy(finished = finished + 1)
        return when {
            failed -> counted.copy(failed = this.failed + 1)
            passed -> counted.copy(passed = this.passed + 1)
            else -> counted
        }
    }

    @Synchronized
    private fun update(change: (Snapshot) -> Snapshot) {
        val next = change(snapshot)
        if (next == snapshot) return
        snapshot = next
        onChange?.invoke()
    }
}
