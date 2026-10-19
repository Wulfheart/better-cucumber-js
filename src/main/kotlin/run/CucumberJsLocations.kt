package com.wulfindustries.plugins.run

import com.intellij.openapi.util.io.FileUtil

/**
 * Test location URLs shared by the test tree, gutter icons and run configurations.
 * They use the `file` protocol understood by [com.intellij.execution.testframework.sm.FileUrlProvider]; lines and
 * columns are 1-based.
 */
object CucumberJsLocations {
    const val PROTOCOL = "file"

    /** [column] only counts together with a [line]. */
    fun url(path: String, line: Int? = null, column: Int? = null): String {
        val base = "$PROTOCOL://${FileUtil.toSystemIndependentName(path)}"
        return when {
            line == null -> base
            column == null -> "$base:$line"
            else -> "$base:$line:$column"
        }
    }

    /** The path and line of a [url], ignoring any column, or `null` for URLs of another protocol. */
    fun parse(url: String): Pair<String, Int?>? {
        val location = url.removePrefix("$PROTOCOL://").takeIf { it != url } ?: return null
        val line = LINE_SUFFIX.find(location) ?: return location to null
        return location.substring(0, line.range.first) to line.groupValues[1].toInt()
    }

    private val LINE_SUFFIX = Regex(""":(\d+)(?::\d+)?$""")
}
