package com.wulfindustries.plugins.tables

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Alarm
import com.wulfindustries.plugins.run.CucumberJsRunConfiguration
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/**
 * The Examples row the Example Row tool window shows, shared by its tabs. It follows the caret in `.feature` editors
 * and the example rows that get run; the caret anywhere else in a `.feature` file, or a run of anything but one
 * example row, clears it.
 */
class ExampleRowSelection(private val project: Project) : Disposable {
    private val alarm = Alarm(this)
    private val listeners = mutableListOf<() -> Unit>()

    /** The editor whose caret moved, to read once the delay is over; `null` when only the row's text changed. */
    private var caretEditor: Editor? = null

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
                val file = row?.virtualFile ?: return
                if (FileDocumentManager.getInstance().getFile(event.document) == file) schedule()
            }
        }, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                FileEditorManager.getInstance(project).selectedTextEditor?.let(::follow)
            }
        })
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
                ApplicationManager.getApplication().invokeLater({ followRun(env) }, project.disposed)
            }
        })
        FileEditorManager.getInstance(project).selectedTextEditor?.let(::follow)
    }

    /** Calls [listener] on the EDT after every change of the row, the column or the row's text. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** Notifies the listeners after a short delay, once the documents are committed. */
    fun schedule() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {
                caretEditor?.let(::readCaret)
                caretEditor = null
                notifyListeners()
            }
        }, 100)
    }

    /** Opens the selected row's file with the caret at [column], or at the row's first cell when it is `null`. */
    fun navigateTo(column: Int?) {
        val target = runReadActionBlocking {
            val element = row?.element ?: return@runReadActionBlocking null
            val file = element.containingFile?.virtualFile ?: return@runReadActionBlocking null
            val ranges = ExamplesTables.cellContentRanges(element)
            val range = column?.let(ranges::getOrNull) ?: ranges.firstOrNull()
            file to (range?.startOffset ?: element.textOffset)
        } ?: return
        if (column != null) this.column = column
        // Without focus, so the arrow keys keep moving through the tool window's lines
        OpenFileDescriptor(project, target.first, target.second).navigate(false)
    }

    /** Shows the selected row in the editor with the caret at the start of its line, like "Select Opened File". */
    fun locate() {
        val target = runReadActionBlocking {
            val element = row?.element ?: return@runReadActionBlocking null
            val file = element.containingFile ?: return@runReadActionBlocking null
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runReadActionBlocking null
            val virtualFile = file.virtualFile ?: return@runReadActionBlocking null
            virtualFile to document.getLineNumber(element.textOffset)
        } ?: return
        OpenFileDescriptor(project, target.first, target.second, 0).navigate(true)
    }

    private fun follow(editor: Editor) {
        if (editor.project != project) return
        if (FileDocumentManager.getInstance().getFile(editor.document)?.fileType != GherkinFileType.INSTANCE) return
        caretEditor = editor
        schedule()
    }

    private fun readCaret(editor: Editor) {
        if (project.isDisposed || editor.isDisposed) return
        runReadActionBlocking {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@runReadActionBlocking
            val offset = editor.caretModel.offset
            // The indent before a row belongs to the table, so the row's line is also found from its first character
            val position = ExamplesTables.positionAt(file, offset) ?: firstCharacterOfLine(editor, offset)?.let { ExamplesTables.positionAt(file, it) }
            val current = row?.element
            when {
                position == null -> select(null, null)
                !position.isHeader ->
                    select(if (position.row == current) row else SmartPointerManager.createPointer(position.row), position.column)
                // The header keeps the row of its own table, so the column can be picked there
                current != null && ExamplesTables.tableOf(current) == position.table -> column = position.column
                else -> select(null, null)
            }
        }
    }

    private fun firstCharacterOfLine(editor: Editor, offset: Int): Int? {
        val document = editor.document
        val line = document.getLineNumber(offset)
        val start = document.getLineStartOffset(line)
        val text = document.charsSequence.subSequence(start, document.getLineEndOffset(line))
        return text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 }?.let { start + it }
    }

    private fun followRun(env: ExecutionEnvironment) {
        val configuration = env.runProfile as? CucumberJsRunConfiguration
        val found = runReadActionBlocking {
            val line = configuration?.line ?: return@runReadActionBlocking null
            val path = configuration.scenarioPath?.takeIf { it.row != null } ?: return@runReadActionBlocking null
            path.copy(featurePath = configuration.featurePath).find(project, line) as? GherkinTableRow
        }
        // A caret move still waiting for the delay would replace the row that is being run
        alarm.cancelAllRequests()
        caretEditor = null
        when {
            found == null -> select(null, null)
            runReadActionBlocking { found != row?.element } -> select(SmartPointerManager.createPointer(found), null)
        }
        notifyListeners()
    }

    private fun select(row: SmartPsiElementPointer<GherkinTableRow>?, column: Int?) {
        this.row = row
        this.column = column
    }

    private fun notifyListeners() {
        if (project.isDisposed) return
        listeners.forEach { it() }
    }

    override fun dispose() {
        listeners.clear()
    }
}
