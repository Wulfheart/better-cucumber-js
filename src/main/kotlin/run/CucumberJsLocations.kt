package com.wulfindustries.plugins.run

import com.intellij.openapi.util.io.FileUtil

/**
 * Test location URLs shared by the test tree, gutter icons and run configurations.
 * They use the `file` protocol understood by [com.intellij.execution.testframework.sm.FileUrlProvider]; lines are 1-based.
 */
object CucumberJsLocations {
    const val PROTOCOL = "file"

    fun url(path: String, line: Int? = null): String {
        val base = "$PROTOCOL://${FileUtil.toSystemIndependentName(path)}"
        return if (line == null) base else "$base:$line"
    }
}
