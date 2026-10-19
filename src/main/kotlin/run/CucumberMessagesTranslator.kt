package com.wulfindustries.plugins.run

import java.nio.file.Path

/**
 * Translates Cucumber Messages NDJSON (cucumber-js `--format message`) into TeamCity service messages
 * for the SM test runner. One instance per run; feed it lines in order.
 *
 * @param baseDir directory that relative feature URIs in the messages are resolved against (the run's working dir)
 */
class CucumberMessagesTranslator(private val baseDir: Path) {
    /** Returns zero or more complete `##teamcity[...]` lines for one NDJSON line. */
    fun translate(ndjsonLine: String): List<String> = TODO("Stream A")
}
