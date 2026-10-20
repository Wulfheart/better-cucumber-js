package com.wulfindustries.plugins.run

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.Printable
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.GeneralIdBasedToSMTRunnerEventsConvertor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class CucumberJsTestEventsConverterTest : BasePlatformTestCase() {
    private class CollectingPrinter : Printer {
        val text = StringBuilder()
        override fun print(text: String, contentType: ConsoleViewContentType) {
            this.text.append(text)
        }
        override fun onNewAvailable(printable: Printable) = printable.printOn(this)
        override fun printHyperlink(text: String, info: HyperlinkInfo?) {
            this.text.append(text)
        }
        override fun mark() = Unit
    }

    fun testStepOutputAndAttachmentsLandUnderTheirStep() {
        val configuration = CucumberJsConfigurationType.getInstance().factory
            .createTemplateConfiguration(project) as CucumberJsRunConfiguration
        configuration.workingDir = "/project"
        val properties = configuration.createTestConsoleProperties(DefaultRunExecutor.getRunExecutorInstance())
        Disposer.register(testRootDisposable, properties)
        val converter = (properties as SMCustomMessagesParsing).createTestEventsConverter("Cucumber.js", properties)
        assertTrue(properties.isIdBasedTestTree)
        val root = SMTestProxy.SMRootTestProxy()
        val processor = GeneralIdBasedToSMTRunnerEventsConvertor(project, root, "Cucumber.js")
        Disposer.register(testRootDisposable, processor)
        converter.setProcessor(processor)
        converter.startTesting()

        // Captured from playground/js/features/output.feature, scenario "Logs and attachments"
        val lines = Files.readAllLines(Path.of("src/test/testData/messages/attachments.ndjson"))
        for (line in lines) {
            when {
                // Long messages can arrive split across output chunks
                line.startsWith("""{"gherkinDocument"""") -> "$line\n".chunked(100).forEach { converter.process(it, STDOUT) }
                // Output written without a trailing newline ends up on the same line as the next message
                line.startsWith("""{"testCaseFinished"""") -> converter.process("partial$line\n", STDOUT)
                else -> converter.process("$line\n", STDOUT)
            }
            if (line.startsWith("""{"testStepStarted"""")) converter.process("console line\n", STDOUT)
        }
        converter.flushBufferOnProcessTermination(0)
        converter.finishTesting()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("Step output", root.children.single().name)
        val scenario = root.allTests.single { it.name == "Logs and attachments" }
        assertTrue(scenario.isSuite)
        assertTrue(scenario.isPassed)
        assertEquals(4, scenario.children.size)
        for (proxy in root.allTests) {
            assertFalse("${proxy.name} is still running", proxy.isInProgress)
            assertFalse("${proxy.name} was terminated", proxy.wasTerminated())
        }

        // Console output and attachments land under the step that wrote them
        val logStep = scenario.children.first()
        assertEquals("Given the step logs \"hello from this.log\"", logStep.name)
        assertTrue(logStep.isPassed)
        val stepOutput = CollectingPrinter().also(logStep::printOn).text.toString()
        for (expected in listOf("console line", "hello from this.log")) {
            assertTrue("Missing '$expected' in:\n$stepOutput", expected in stepOutput)
        }
        val output = CollectingPrinter().also(scenario::printOn).text.toString()
        for (expected in listOf("with two lines", "Attachment: image/png (70 bytes)", "partial")) {
            assertTrue("Missing '$expected' in:\n$output", expected in output)
        }
        assertFalse("Raw messages leaked into the output:\n$output", "testStepStarted" in output)
    }

    private companion object {
        val STDOUT = ProcessOutputTypes.STDOUT
    }
}
