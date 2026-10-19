package com.wulfindustries.plugins.tables

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.smartReadAction
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
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class ExampleRowToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean = smartReadAction(project) {
        FileTypeIndex.containsFileOfType(GherkinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ExampleRowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Shows the Examples row at the caret as `header | value` lines, optionally next to a pinned row.
 * Values can be edited in place, and selecting a line moves the editor caret to that cell.
 */
private class ExampleRowPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    private val alarm = Alarm(this)
    private val model = RecordTableModel()
    private val table = JBTable(model)
    private val title = JBLabel().apply { border = JBUI.Borders.empty(4, 8) }

    private var editor: Editor? = null
    private var row: SmartPsiElementPointer<GherkinTableRow>? = null
    private var column: Int? = null
    private var pinned: SmartPsiElementPointer<GherkinTableRow>? = null
    private var differencesOnly = false
    private var syncingSelection = false

    init {
        table.setShowGrid(false)
        table.emptyText.text = MyMessageBundle.message("tables.tool.window.empty")
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.setDefaultRenderer(Any::class.java, RecordCellRenderer())
        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !syncingSelection) navigateToSelectedCell()
        }

        val content = JPanel(BorderLayout())
        content.add(title, BorderLayout.NORTH)
        content.add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER)
        setContent(content)

        val actions = DefaultActionGroup(PinRowAction(), DifferencesOnlyAction())
        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberJsExampleRow", actions, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)

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

    private fun follow(editor: Editor) {
        if (editor.project != project) return
        if (FileDocumentManager.getInstance().getFile(editor.document)?.fileType != GherkinFileType.INSTANCE) return
        this.editor = editor
        schedule()
    }

    private fun schedule() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            val editor = editor ?: return@addRequest
            PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document, ::refresh)
        }, 100)
    }

    private fun refresh() {
        if (project.isDisposed) return
        // Rebuilding the model would throw the edit away
        if (table.isEditing) return schedule()
        val record = runReadActionBlocking {
            editor?.takeUnless { it.isDisposed }?.let { editor ->
                val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                val position = file?.let { ExamplesTables.positionAt(it, editor.caretModel.offset) }
                // Outside a data row the last row stays, so it can still be compared or edited
                if (position != null && !position.isHeader) row = SmartPointerManager.createPointer(position.row)
                if (position != null && (position.isHeader || position.row == row?.element)) column = position.column
            }
            row?.element?.let { ExampleRecord.of(it, pinned?.element, differencesOnly) }
        }
        title.text = record?.let { "${it.title} › ${it.rowName}" }.orEmpty()
        model.update(record)
        syncingSelection = true
        try {
            val line = record?.lines?.indexOfFirst { it.column == column } ?: -1
            if (line >= 0) table.selectionModel.setSelectionInterval(line, line) else table.clearSelection()
        } finally {
            syncingSelection = false
        }
    }

    private fun navigateToSelectedCell() {
        val line = model.record?.lines?.getOrNull(table.selectedRow) ?: return
        val editor = editor?.takeUnless { it.isDisposed } ?: return
        val range = runReadActionBlocking { row?.element?.let(ExamplesTables::cellContentRanges)?.getOrNull(line.column) } ?: return
        column = line.column
        editor.caretModel.moveToOffset(range.startOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    override fun dispose() {}

    private inner class RecordTableModel : AbstractTableModel() {
        var record: ExampleRecord? = null
            private set
        private val hasPinned get() = record?.pinnedName != null

        fun update(record: ExampleRecord?) {
            val structureChanged = (record?.pinnedName != null) != hasPinned || record?.rowName != this.record?.rowName ||
                record?.pinnedName != this.record?.pinnedName
            this.record = record
            if (structureChanged) fireTableStructureChanged() else fireTableDataChanged()
        }

        override fun getRowCount(): Int = record?.lines?.size ?: 0

        override fun getColumnCount(): Int = if (hasPinned) 3 else 2

        override fun getColumnName(column: Int): String = when {
            column == 0 -> MyMessageBundle.message("tables.tool.window.column")
            hasPinned && column == 1 -> record!!.pinnedName!!
            else -> record?.rowName ?: MyMessageBundle.message("tables.tool.window.value")
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val line = record!!.lines[rowIndex]
            return when {
                columnIndex == 0 -> line.header
                hasPinned && columnIndex == 1 -> line.pinned.orEmpty()
                else -> line.value
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == columnCount - 1

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val line = record?.lines?.getOrNull(rowIndex) ?: return
            val text = value as? String ?: return
            if (text == line.value) return
            row?.let { ExamplesTables.setCell(project, it, line.column, text) }
        }
    }

    private inner class RecordCellRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            val line = model.record?.lines?.getOrNull(row)
            val text = (value as? String).orEmpty().replace("\n", "⏎")
            val attributes = when {
                column == 0 -> SimpleTextAttributes.GRAYED_ATTRIBUTES
                line?.differs == true -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
            }
            append(text, attributes)
        }
    }

    private inner class PinRowAction : DumbAwareToggleAction(
        MyMessageBundle.lazyMessage("tables.tool.window.pin"),
        MyMessageBundle.lazyMessage("tables.tool.window.pin.description"),
        AllIcons.General.Pin_tab,
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = pinned != null

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            // The same pointer is fine, the row it points to is not followed by the caret
            pinned = if (state) row else null
            refresh()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = row != null || pinned != null
        }
    }

    private inner class DifferencesOnlyAction : DumbAwareToggleAction(
        MyMessageBundle.lazyMessage("tables.tool.window.differences"),
        MyMessageBundle.lazyMessage("tables.tool.window.differences.description"),
        AllIcons.Actions.Diff,
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = differencesOnly

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            differencesOnly = state
            refresh()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = pinned != null
        }
    }
}
