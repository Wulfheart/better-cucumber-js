package com.wulfindustries.plugins.tables

import com.intellij.execution.Executor
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.SmartPsiElementPointer
import com.wulfindustries.plugins.MyMessageBundle
import com.wulfindustries.plugins.run.CucumberJsRunConfigurationProducer
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/** Runs an Examples row the way its gutter icon does, through [CucumberJsRunConfigurationProducer]. */
object ExampleRowRunner {
    /** The matching existing run configuration, or a new one; `null` without a cucumber-js installation. */
    fun settingsFor(row: GherkinTableRow): RunnerAndConfigurationSettings? {
        val context = ConfigurationContext.createEmptyContextForLocation(PsiLocation(row))
        return RunConfigurationProducer.getInstance(CucumberJsRunConfigurationProducer::class.java)
            .findOrCreateConfigurationFromContext(context)
            ?.configurationSettings
    }

    fun run(project: Project, row: SmartPsiElementPointer<GherkinTableRow>, executor: Executor) {
        val settings = runReadActionBlocking { row.element?.let(::settingsFor) }
        if (settings == null) {
            ToolWindowManager.getInstance(project)
                .notifyByBalloon(TOOL_WINDOW_ID, MessageType.WARNING, MyMessageBundle.message("tables.tool.window.run.unavailable"))
            return
        }
        val runManager = RunManager.getInstance(project)
        if (runManager.findSettings(settings.configuration) == null) runManager.setTemporaryConfiguration(settings)
        ExecutionUtil.runConfiguration(settings, executor)
    }

    /** The id of the Example Row tool window in `plugin.xml`. */
    const val TOOL_WINDOW_ID = "Example Row"
}
