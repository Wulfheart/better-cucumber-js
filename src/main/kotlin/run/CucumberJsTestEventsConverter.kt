package com.wulfindustries.plugins.run

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key

/**
 * Turns the Cucumber messages that cucumber-js writes to stdout (`--format message`) into test events.
 *
 * The messages share stdout with the steps' own output, in the order they happened, so console output written
 * between a test's start and finish messages lands under that test. Lines that are not messages pass through.
 */
class CucumberJsTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
    private val translator: CucumberMessagesTranslator,
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    // Messages can be longer than one output chunk, so stdout is split into lines here
    private val stdout = StringBuilder()

    override fun processConsistentText(text: String, outputType: Key<*>) {
        if (outputType != ProcessOutputTypes.STDOUT) {
            super.processConsistentText(text, outputType)
            return
        }
        synchronized(stdout) {
            stdout.append(text)
            var newline = stdout.indexOf("\n")
            while (newline >= 0) {
                processStdoutLine(stdout.substring(0, newline + 1))
                stdout.delete(0, newline + 1)
                newline = stdout.indexOf("\n")
            }
        }
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        synchronized(stdout) {
            if (stdout.isNotEmpty()) {
                processStdoutLine(stdout.toString())
                stdout.setLength(0)
            }
        }
        super.flushBufferOnProcessTermination(exitCode)
    }

    private fun processStdoutLine(line: String) {
        val start = translator.envelopeStart(line)
        if (start < 0) {
            super.processConsistentText(line, ProcessOutputTypes.STDOUT)
            return
        }
        if (start > 0) {
            super.processConsistentText(line.substring(0, start) + "\n", ProcessOutputTypes.STDOUT)
        }
        val messages = try {
            translator.translate(line.substring(start))
        } catch (e: RuntimeException) {
            if (e is ControlFlowException) throw e
            LOG.warn("Cannot translate Cucumber message: ${line.take(200)}", e)
            return
        }
        for (message in messages) {
            super.processConsistentText(message + "\n", ProcessOutputTypes.STDOUT)
        }
    }

    private companion object {
        val LOG = logger<CucumberJsTestEventsConverter>()
    }
}
