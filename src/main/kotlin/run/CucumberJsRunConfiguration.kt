package com.wulfindustries.plugins.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.javascript.nodejs.debug.NodeDebugRunConfiguration
import com.intellij.javascript.nodejs.execution.AbstractNodeTargetRunProfile
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

// Contract fixed in Phase 0: the typed properties below are what the producer (stream C) sets.
// Stream B fills in persistence, validation, state and the editor.
class CucumberJsRunConfiguration(
    project: Project,
    factory: CucumberJsConfigurationFactory,
    name: String,
) : AbstractNodeTargetRunProfile(project, factory, name),
    NodeDebugRunConfiguration,
    SMRunnerConsolePropertiesProvider {

    var interpreterRef: NodeJsInterpreterRef = NodeJsInterpreterRef.createProjectRef()

    /** Directory of the `@cucumber/cucumber` package, e.g. `<project>/node_modules/@cucumber/cucumber`. */
    var cucumberPackagePath: String = ""
    var workingDir: String = ""
    var featurePath: String = ""

    /** 1-based line of the scenario or example row to run; `null` runs the whole feature. */
    var line: Int? = null
    var tags: String = ""
    var profile: String = ""
    var extraArgs: String = ""
    override var envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    override val interpreter: NodeJsInterpreter?
        get() = interpreterRef.resolve(project)

    override fun createConfigurationEditor(): SettingsEditor<out AbstractNodeTargetRunProfile> = TODO("Stream B")

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = TODO("Stream B")

    override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties = TODO("Stream B")
}
