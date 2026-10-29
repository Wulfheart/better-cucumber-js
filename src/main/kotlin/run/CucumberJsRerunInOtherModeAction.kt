package com.wulfindustries.plugins.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.wulfindustries.plugins.MyMessageBundle
import java.util.function.Supplier

/**
 * Reruns a Cucumber.js run in the other mode: with the debugger after a plain run, without it after a debug run. After
 * "Rerun Failed Tests" it reruns those same scenarios. Shift+click on "Rerun Failed Tests" offers the same choice for
 * the failed scenarios of this run.
 */
class CucumberJsRerunInOtherModeAction private constructor(
    private val environment: ExecutionEnvironment,
    private val configuration: CucumberJsRunConfiguration,
    private val executor: Executor,
    textKey: String,
) : DumbAwareAction(MyMessageBundle.lazyMessage(textKey), null as Supplier<String>?, { executor.icon }) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = runner() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val runner = runner() ?: return
        // No content to reuse: the run goes to the other mode's tool window, so this tab stays as it is
        val builder = ExecutionEnvironmentBuilder(environment).executor(executor).runner(runner).contentToReuse(null)
        try {
            builder.buildAndExecute()
        } catch (e: ExecutionException) {
            ExecutionUtil.handleExecutionError(environment, e)
        }
    }

    // Looked up for the configuration, as "Rerun Failed Tests" does: after that, the environment's profile wraps it
    private fun runner(): ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, configuration)

    companion object {
        /** `null` for executors other than Run and Debug, such as coverage, which have no obvious counterpart. */
        fun create(environment: ExecutionEnvironment, configuration: CucumberJsRunConfiguration): CucumberJsRerunInOtherModeAction? =
            when (environment.executor.id) {
                DefaultRunExecutor.EXECUTOR_ID -> CucumberJsRerunInOtherModeAction(
                    environment, configuration, DefaultDebugExecutor.getDebugExecutorInstance(), "rerun.with.debugger",
                )
                DefaultDebugExecutor.EXECUTOR_ID -> CucumberJsRerunInOtherModeAction(
                    environment, configuration, DefaultRunExecutor.getRunExecutorInstance(), "rerun.without.debugger",
                )
                else -> null
            }
    }
}
