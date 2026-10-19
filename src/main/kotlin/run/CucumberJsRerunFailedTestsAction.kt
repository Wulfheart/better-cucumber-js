package com.wulfindustries.plugins.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.wulfindustries.plugins.MyMessageBundle

/**
 * "Rerun Failed Tests" for a Cucumber.js run: reruns every scenario or example row with a failed step or hook, at the
 * line it is on now, so edits to the feature files since the run do not send cucumber-js to the wrong scenario.
 */
class CucumberJsRerunFailedTestsAction(
    consoleView: SMTRunnerConsoleView,
    properties: TestConsoleProperties,
) : AbstractRerunFailedTestsAction(consoleView) {
    private val anchors = ScenarioAnchors(properties.project, consoleView.resultsViewer.testsRootNode)

    init {
        init(properties)
        setModel(consoleView.resultsViewer)
        Disposer.register(consoleView, anchors)
        properties.project.messageBus.connect(anchors).subscribe(SMTRunnerEventsListener.TEST_STATUS, anchors)
    }

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile {
        val configuration = myConsoleProperties.configuration as CucumberJsRunConfiguration
        val project = configuration.project
        // The name lookup for scenarios whose marker is gone reads the PSI
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val locations = anchors.locations(getFailedTests(project))
        return object : MyRunProfile(configuration) {
            override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState {
                if (locations.isEmpty()) throw ExecutionException(MyMessageBundle.message("rerun.failed.none.found"))
                return CucumberJsRunProfileState(configuration, env, locations)
            }
        }
    }
}
