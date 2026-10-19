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

    fun arguments(
        cucumberPackagePath: String,
        featurePath: String,
        line: Int?,
        tags: String,
        profile: String,
        extraArgs: String,
    ): List<Arg> = buildList {
        add(Arg("$cucumberPackagePath/$BIN_RELATIVE_PATH", isPath = true))
        add(Arg(featurePath, isPath = true, suffix = line?.let { ":$it" }.orEmpty()))
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
