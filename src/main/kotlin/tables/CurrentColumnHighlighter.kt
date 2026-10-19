package com.wulfindustries.plugins.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

/** Highlights the Examples table column under the caret in every row of the table, header included. */
class CurrentColumnHighlighter : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        if (FileDocumentManager.getInstance().getFile(editor.document)?.fileType != GherkinFileType.INSTANCE) return
        val tracker = Tracker(editor, project)
        editor.putUserData(TRACKER, tracker)
        tracker.schedule()
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.getUserData(TRACKER)?.let(Disposer::dispose)
        event.editor.putUserData(TRACKER, null)
    }

    companion object {
        private val TRACKER = Key.create<Tracker>("CucumberJs.CurrentColumnTracker")
        private const val DELAY_MS = 50

        /** Re-evaluates every open Gherkin editor, e.g. after the setting changed. */
        fun refreshAll() {
            for (editor in EditorFactory.getInstance().allEditors) editor.getUserData(TRACKER)?.schedule()
        }

        /** Updates [editor]'s highlighting now instead of after the delay. */
        @TestOnly
        fun updateNow(editor: Editor) {
            editor.getUserData(TRACKER)!!.update()
        }
    }

    private class Tracker(private val editor: Editor, private val project: Project) : CaretListener, DocumentListener, Disposable {
        private val alarm = Alarm(this)
        private var highlighters: List<RangeHighlighter> = emptyList()

        init {
            editor.caretModel.addCaretListener(this, this)
            editor.document.addDocumentListener(this, this)
        }

        override fun caretPositionChanged(event: CaretEvent) = schedule()

        override fun documentChanged(event: DocumentEvent) = schedule()

        fun schedule() {
            alarm.cancelAllRequests()
            alarm.addRequest({ PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document, ::update) }, DELAY_MS)
        }

        fun update() {
            if (editor.isDisposed || project.isDisposed) return
            val spans = runReadActionBlocking { currentColumnSpans() }
            highlighters.forEach { it.dispose() }
            highlighters = spans.map {
                editor.markupModel.addRangeHighlighter(
                    TableColors.CURRENT_COLUMN, it.startOffset, it.endOffset,
                    HighlighterLayer.SELECTION - 1, HighlighterTargetArea.EXACT_RANGE,
                )
            }
        }

        private fun currentColumnSpans() = run {
            if (!CucumberJsTableSettings.getInstance().state.highlightCurrentColumn) return@run emptyList()
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@run emptyList()
            val position = ExamplesTables.positionAt(file, editor.caretModel.offset) ?: return@run emptyList()
            val column = position.column ?: return@run emptyList()
            ExamplesTables.rows(position.table).mapNotNull { ExamplesTables.cellSpans(it).getOrNull(column) }
        }

        override fun dispose() {
            highlighters.forEach { it.dispose() }
            highlighters = emptyList()
        }
    }
}
