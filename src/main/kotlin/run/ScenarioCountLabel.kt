package com.wulfindustries.plugins.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.testframework.ui.TestStatusLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout

/**
 * Shows [ScenarioCounts] next to the platform's "Tests passed: …" text above the console. The platform redraws its
 * own text on every event, so this is a label of our own in the status line's free center.
 */
object ScenarioCountLabel {
    fun install(statusLine: TestStatusLine, counts: ScenarioCounts, processHandler: ProcessHandler) {
        val label = JBLabel().apply {
            border = JBUI.Borders.emptyLeft(12)
            isVisible = false
        }
        statusLine.add(label, BorderLayout.CENTER)
        counts.onChange = {
            ApplicationManager.getApplication().invokeLater({
                val snapshot = counts.snapshot
                label.text = snapshot.text()
                label.isVisible = snapshot.total > 0
            }, ModalityState.any())
        }
        // A stopped run never reports testRunFinished
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) = counts.runFinished()
        })
    }
}
