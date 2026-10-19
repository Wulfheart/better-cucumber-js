package com.wulfindustries.plugins.run

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.execution.ParametersListUtil

/**
 * Builds the cucumber-js arguments that follow the Node executable. Pure so it can be unit-tested.
 */
object CucumberJsCommandLine {
    const val BIN_RELATIVE_PATH = "bin/cucumber-js"

    /** One argument. Path arguments are converted to the run target's path before [suffix] is applied. */
    data class Arg(
        val value: String,
        val isPath: Boolean = false,
        val suffix: String = "",
    ) {
        fun render(convertPath: (String) -> String = { it }): String {
            if (!isPath) return value + suffix
            return FileUtil.toSystemIndependentName(convertPath(value)) + suffix
        }
    }

    /** A feature file to run, limited to the scenarios or example rows at [lines] (1-based) unless that is empty. */
    data class FeatureLocation(val path: String, val lines: List<Int> = emptyList())

    fun arguments(
        cucumberPackagePath: String,
        locations: List<FeatureLocation>,
        tags: String,
        profile: String,
        extraArgs: String,
    ): List<Arg> = buildList {
        add(Arg("$cucumberPackagePath/$BIN_RELATIVE_PATH", isPath = true))
        for (location in locations) {
            // cucumber-js takes several lines of one file as `path:12:34`
            add(Arg(location.path, isPath = true, suffix = location.lines.joinToString("") { ":$it" }))
        }
        if (tags.isNotBlank()) {
            add(Arg("--tags"))
            add(Arg(tags.trim()))
        }
        if (profile.isNotBlank()) {
            add(Arg("--profile"))
            add(Arg(profile.trim()))
        }
        if (extraArgs.isNotBlank()) {
            // Single quotes too, since that is how JSON options such as --world-parameters are usually written
            ParametersListUtil.parse(extraArgs.trim(), false, true).forEach { add(Arg(it)) }
        }
        // Last, so it wins over any stdout formatter from the user's config or extra args: cucumber-js uses the
        // last format without a target for stdout, and test events must share stdout with the steps' own output
        add(Arg("--format"))
        add(Arg("message"))
    }
}
