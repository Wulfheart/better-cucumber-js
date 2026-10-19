package com.wulfindustries.plugins.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Alarm
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/**
 * The Examples row the Example Row tool window shows, shared by its tabs. It follows the caret in
 * `.feature` editors; outside a data row the last row stays, so it can still be compared, edited or run.
 */
class ExampleRowSelection(private val project: Project) : Disposable {
    private val alarm = Alarm(this)
    private val listeners = mutableListOf<() -> Unit>()

    var editor: Editor? = null
        private set
    var row: SmartPsiElementPointer<GherkinTableRow>? = null
        private set

    /** The column at the caret, also while the caret is in the header row. */
    var column: Int? = null
        private set

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = follow(event.editor)
        }, this)
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.document == editor?.document) schedule()
            }
        }, this)
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                FileEditorManager.getInstance(project).selectedTextEditor?.let(::follow)
            }
        })
        FileEditorManager.getInstance(project).selectedTextEditor?.let(::follow)
    }

    /** Calls [listener] on the EDT after every change of the row, the column or the row's text. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** Re-reads the caret position and notifies the listeners after a short delay. */
    fun schedule() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            val editor = editor ?: return@addRequest
            PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document, ::refresh)
        }, 100)
    }

    /** Moves the editor caret to [column] of the selected row. */
    fun navigateTo(column: Int) {
        val editor = editor?.takeUnless { it.isDisposed } ?: return
        val range = runReadActionBlocking { row?.element?.let(ExamplesTables::cellContentRanges)?.getOrNull(column) } ?: return
        this.column = column
        editor.caretModel.moveToOffset(range.startOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun follow(editor: Editor) {
        if (editor.project != project) return
        if (FileDocumentManager.getInstance().getFile(editor.document)?.fileType != GherkinFileType.INSTANCE) return
        this.editor = editor
        schedule()
    }

    private fun refresh() {
        if (project.isDisposed) return
        runReadActionBlocking {
            val editor = editor?.takeUnless { it.isDisposed } ?: return@runReadActionBlocking
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@runReadActionBlocking
            val position = ExamplesTables.positionAt(file, editor.caretModel.offset) ?: return@runReadActionBlocking
            if (!position.isHeader && position.row != row?.element) row = SmartPointerManager.createPointer(position.row)
            if (position.isHeader || position.row == row?.element) column = position.column
        }
        listeners.forEach { it() }
    }

    override fun dispose() {
        listeners.clear()
    }
}
