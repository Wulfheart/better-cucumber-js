package com.wulfindustries.plugins.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Follows the cucumber-js messages file while the process runs and injects the translated service messages into
 * the process output, where the SM test runner picks them up.
 *
 * Must be added to the handler before the test console attaches: on termination it drains the rest of the file
 * synchronously, and listeners run in registration order, so the test tree sees every event before it finalizes.
 */
class CucumberMessagesTailer(
    private val handler: ProcessHandler,
    private val messagesFile: Path,
    private val translator: CucumberMessagesTranslator,
) : ProcessListener {
    private val lock = Any()
    private var offset = 0L
    private val pending = StringBuilder()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        handler.addProcessListener(this)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                readAvailable()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        job?.cancel()
        readAvailable(flushPartialLine = true)
        try {
            Files.deleteIfExists(messagesFile)
        } catch (e: IOException) {
            LOG.warn("Cannot delete $messagesFile", e)
        }
    }

    private fun readAvailable(flushPartialLine: Boolean = false) {
        synchronized(lock) {
            readNewBytes(flushPartialLine)?.let(pending::append)
            var newline = pending.indexOf("\n")
            while (newline >= 0) {
                emit(pending.substring(0, newline))
                pending.delete(0, newline + 1)
                newline = pending.indexOf("\n")
            }
            if (flushPartialLine && pending.isNotBlank()) {
                emit(pending.toString())
                pending.setLength(0)
            }
        }
    }

    private fun readNewBytes(includePartialLine: Boolean): String? {
        if (!Files.exists(messagesFile)) return null
        return try {
            RandomAccessFile(messagesFile.toFile(), "r").use { file ->
                val length = file.length()
                if (length <= offset) return null
                val bytes = ByteArray((length - offset).toInt())
                file.seek(offset)
                file.readFully(bytes)
                // Consume only complete lines while running so a multi-byte character is never split
                val lastNewline = bytes.indexOfLast { it == '\n'.code.toByte() }
                val usable = if (includePartialLine) bytes.size else lastNewline + 1
                if (usable == 0) return null
                offset += usable
                String(bytes, 0, usable, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            LOG.warn("Cannot read $messagesFile", e)
            null
        }
    }

    private fun emit(line: String) {
        if (line.isBlank()) return
        val messages = try {
            translator.translate(line)
        } catch (e: RuntimeException) {
            if (e is ControlFlowException) throw e
            LOG.warn("Cannot translate Cucumber message: ${line.take(200)}", e)
            return
        }
        for (message in messages) {
            handler.notifyTextAvailable(message + "\n", ProcessOutputTypes.STDOUT)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
        val LOG = logger<CucumberMessagesTailer>()
    }
}
