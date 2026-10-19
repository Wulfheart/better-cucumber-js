package com.wulfindustries.plugins.run

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.execution.ParametersListUtil

/**
 * Builds the cucumber-js arguments that follow the Node executable. Pure so it can be unit-tested.
 */
object CucumberJsCommandLine {
    const val BIN_RELATIVE_PATH = "bin/cucumber-js"

    /**
     * One argument. Path arguments are converted to the run target's path before [prefix] and [suffix] are applied.
     * [quotePath] wraps the converted path in double quotes, which cucumber-js needs for `--format <name>:<path>`
     * when the path contains a colon (e.g. a Windows drive letter).
     */
    data class Arg(
        val value: String,
        val isPath: Boolean = false,
        val prefix: String = "",
        val suffix: String = "",
        val quotePath: Boolean = false,
    ) {
        fun render(convertPath: (String) -> String = { it }): String {
            if (!isPath) return prefix + value + suffix
            val path = FileUtil.toSystemIndependentName(convertPath(value))
            return prefix + (if (quotePath) "\"$path\"" else path) + suffix
        }
    }

    fun arguments(
        cucumberPackagePath: String,
        featurePath: String,
        line: Int?,
        tags: String,
        profile: String,
        messagesFile: String,
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
        add(Arg("--format"))
        add(Arg(messagesFile, isPath = true, prefix = "message:", quotePath = true))
        if (extraArgs.isNotBlank()) {
            // Single quotes too, since that is how JSON options such as --world-parameters are usually written
            ParametersListUtil.parse(extraArgs.trim(), false, true).forEach { add(Arg(it)) }
        }
    }
}
