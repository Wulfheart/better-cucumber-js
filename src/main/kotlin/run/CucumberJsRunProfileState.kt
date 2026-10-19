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
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Path

class CucumberJsRunProfileState(
    private val configuration: CucumberJsRunConfiguration,
    private val environment: ExecutionEnvironment,
) : NodeBaseRunProfileState {
    private val project = configuration.project

    private val workingDir: String
        get() = configuration.workingDir.ifBlank { File(configuration.featurePath).parent.orEmpty() }

    override fun startProcess(configurator: CommandLineDebugConfigurator?): ProcessHandler {
        val interpreter = configuration.interpreterRef.resolveNotNull(project)
        val options = NodeTargetRunOptions.of(NodeTargetRunOptions.shouldUsePtyForTestRunners(), configuration)
        val targetRun = NodeTargetRun(interpreter, project, configurator, options)
        val builder = targetRun.commandLineBuilder
        if (workingDir.isNotBlank()) {
            builder.setWorkingDirectory(targetRun.path(workingDir))
        }
        targetRun.envData = configuration.envData

        val messagesFile = FileUtil.createTempFile("cucumber-messages", ".ndjson", true).toPath()
        val arguments = CucumberJsCommandLine.arguments(
            cucumberPackagePath = configuration.cucumberPackagePath,
            featurePath = configuration.featurePath,
            line = configuration.line,
            tags = configuration.tags,
            profile = configuration.profile,
            messagesFile = messagesFile.toString(),
            extraArgs = configuration.extraArgs,
        )
        for (arg in arguments) {
            if (arg.isPath) {
                builder.addParameter(TargetValue.map(targetRun.path(arg.value)) { targetPath -> arg.render { targetPath } })
            } else {
                builder.addParameter(arg.render())
            }
        }

        val handler = targetRun.startProcess()
        // Registered before the console attaches so the final drain reaches the test tree before it finalizes
        CucumberMessagesTailer(handler, messagesFile, CucumberMessagesTranslator(Path.of(workingDir)))
            .start(CucumberJsRunService.getInstance(project).cs)
        return handler
    }

    override fun createExecutionResult(processHandler: ProcessHandler): ExecutionResult {
        val properties = configuration.createTestConsoleProperties(environment.executor)
        val console = SMTestRunnerConnectionUtil.createConsole(properties.testFrameworkName, properties)
        val dir = Path.of(workingDir)
        console.addMessageFilter(NodeStackTraceFilter(project, dir))
        console.addMessageFilter(NodeConsoleAdditionalFilter(project, dir))
        ProcessTerminatedListener.attach(processHandler)
        console.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }
}
