package com.wulfindustries.plugins.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CucumberJsRunProfileStateTest : BasePlatformTestCase() {
    fun testTestRunnerOffersRerunFailedTests() {
        val factory = CucumberJsConfigurationType.getInstance().factory
        val configuration = factory.createTemplateConfiguration(project) as CucumberJsRunConfiguration
        configuration.workingDir = FileUtil.getTempDirectory()
        val environment = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), configuration).build()

        val result = CucumberJsRunProfileState(configuration, environment).createExecutionResult(NopProcessHandler())
        Disposer.register(testRootDisposable, result.executionConsole)

        assertTrue((result as DefaultExecutionResult).restartActions.any { it is CucumberJsRerunFailedTestsAction })
    }
}
