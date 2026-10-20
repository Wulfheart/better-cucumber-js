package com.wulfindustries.plugins.run

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.FileUrlProvider
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.wulfindustries.plugins.MyMessageBundle
import java.nio.file.Path

/** Console properties of one run. They hand the run's [scenarioCounts] from the translator to the console. */
class CucumberJsTestConsoleProperties(configuration: CucumberJsRunConfiguration, executor: Executor) :
    SMTRunnerConsoleProperties(configuration, MyMessageBundle.message("run.configuration.test.framework.name"), executor),
    SMCustomMessagesParsing {
    private val baseDir = Path.of(configuration.effectiveWorkingDir)

    val scenarioCounts = ScenarioCounts()

    init {
        // The translator pre-builds the tree with nodeId/parentNodeId messages
        isIdBasedTestTree = true
    }

    override fun getTestLocator(): SMTestLocator = FileUrlProvider.INSTANCE

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties,
    ): OutputToGeneralTestEventsConverter =
        CucumberJsTestEventsConverter(testFrameworkName, consoleProperties, CucumberMessagesTranslator(baseDir, scenarioCounts))

    override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction? =
        (consoleView as? SMTRunnerConsoleView)?.let { CucumberJsRerunFailedTestsAction(it, this) }
}
