package com.wulfindustries.plugins.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.javascript.debugger.CommandLineDebugConfigurator
import com.intellij.javascript.nodejs.NodeConsoleAdditionalFilter
import com.intellij.javascript.nodejs.NodeStackTraceFilter
import com.intellij.javascript.nodejs.execution.NodeBaseRunProfileState
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.NodeTargetRunOptions
import com.wulfindustries.plugins.run.CucumberJsCommandLine.FeatureLocation
import java.nio.file.Path

/**
 * @param locations what to run; the configuration's [CucumberJsRunConfiguration.currentLocation] unless given (used by
 * "Rerun Failed Tests")
 */
class CucumberJsRunProfileState(
    private val configuration: CucumberJsRunConfiguration,
    private val environment: ExecutionEnvironment,
    private val locations: List<FeatureLocation> = listOf(configuration.currentLocation()),
) : NodeBaseRunProfileState {
    private val project = configuration.project

    override fun startProcess(configurator: CommandLineDebugConfigurator?): ProcessHandler {
        val interpreter = configuration.interpreterRef.resolveNotNull(project)
        // No PTY: on Windows it can wrap long lines, which would break the NDJSON messages on stdout
        val options = NodeTargetRunOptions.of(false, configuration)
        val targetRun = NodeTargetRun(interpreter, project, configurator, options)
        val builder = targetRun.commandLineBuilder
        val workingDir = configuration.effectiveWorkingDir
        if (workingDir.isNotBlank()) {
            builder.setWorkingDirectory(targetRun.path(workingDir))
        }
        targetRun.envData = configuration.envData

        val arguments = CucumberJsCommandLine.arguments(
            cucumberPackagePath = configuration.cucumberPackagePath,
            locations = locations,
            tags = configuration.tags,
            profile = configuration.profile,
            extraArgs = configuration.extraArgs,
        )
        for (arg in arguments) {
            if (arg.isPath) {
                builder.addParameter(TargetValue.map(targetRun.path(arg.value)) { targetPath -> arg.render { targetPath } })
            } else {
                builder.addParameter(arg.render())
            }
        }

        return targetRun.startProcess()
    }

    override fun createExecutionResult(processHandler: ProcessHandler): ExecutionResult {
        val dir = Path.of(configuration.effectiveWorkingDir)
        val properties = configuration.createTestConsoleProperties(environment.executor)
        val console = SMTestRunnerConnectionUtil.createConsole(properties)
        console.addMessageFilter(NodeStackTraceFilter(project, dir))
        console.addMessageFilter(NodeConsoleAdditionalFilter(project, dir))
        ScenarioCountLabel.install(console.resultsViewer.statusLine, properties.scenarioCounts, processHandler)
        // The platform leaves creating it to us. Before attaching, so it sees the first scenario start
        val rerunFailedTests = properties.createRerunFailedTestsAction(console)
        val rerunInOtherMode = CucumberJsRerunInOtherModeAction.create(environment, configuration)
        ProcessTerminatedListener.attach(processHandler)
        console.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler).apply {
            setRestartActions(*listOfNotNull(rerunFailedTests, rerunInOtherMode).toTypedArray())
        }
    }
}
