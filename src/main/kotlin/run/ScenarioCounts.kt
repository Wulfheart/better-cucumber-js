package com.wulfindustries.plugins.run

import com.wulfindustries.plugins.MyMessageBundle

/**
 * How many scenarios and example rows of one run passed and failed, for the label next to the test runner's status
 * line. A retried scenario counts once, with its last attempt. Written by the translator on the process output
 * thread, read on the EDT.
 */
class ScenarioCounts {
    data class Snapshot(
        val total: Int = 0,
        val finished: Int = 0,
        val passed: Int = 0,
        val failed: Int = 0,
        val done: Boolean = false,
    ) {
        /** Scenarios that were skipped, pending, undefined or never ran. */
        val ignored: Int get() = total - passed - failed

        /**
         * `Scenarios: 2 of 5` while the second one runs, then the outcome in the platform's wording, e.g.
         * `Scenarios failed: 1, passed: 3, ignored: 1 of 5 scenarios`.
         */
        fun text(): String {
            if (!done) return MyMessageBundle.message("test.status.scenarios.running", minOf(finished + 1, total), total)
            val parts = listOf(
                "test.status.scenarios.failed" to failed,
                "test.status.scenarios.passed" to passed,
                "test.status.scenarios.ignored" to ignored,
            ).filter { it.second > 0 }.joinToString(", ") { MyMessageBundle.message(it.first, it.second) }
            return MyMessageBundle.message("test.status.scenarios.done", parts, total)
        }
    }

    @Volatile
    var snapshot = Snapshot()
        private set

    /** Called after every change, on the thread that made it. */
    @Volatile
    var onChange: (() -> Unit)? = null

    fun start(total: Int) = update { Snapshot(total) }

    fun finished(failed: Boolean, passed: Boolean) = update {
        val counted = it.copy(finished = it.finished + 1)
        when {
            failed -> counted.copy(failed = it.failed + 1)
            passed -> counted.copy(passed = it.passed + 1)
            else -> counted
        }
    }

    /** Whatever has not finished by now counts as ignored. */
    fun runFinished() = update { it.copy(done = true) }

    @Synchronized
    private fun update(change: (Snapshot) -> Snapshot) {
        val next = change(snapshot)
        if (next == snapshot) return
        snapshot = next
        onChange?.invoke()
    }
}
