package com.wulfindustries.plugins.tables

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
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
        val selection = ExampleRowSelection(project)
        Disposer.register(toolWindow.disposable, selection)
        val contents = ContentFactory.getInstance()
        val values = ExampleValuesPanel(project, selection)
        val scenario = ExpandedScenarioPanel(project, selection)
        for ((panel, name) in listOf(values to "tables.tool.window.tab.values", scenario to "tables.tool.window.tab.scenario")) {
            val content = contents.createContent(panel, MyMessageBundle.message(name), false)
            content.setDisposer(panel)
            toolWindow.contentManager.addContent(content)
        }
    }
}

/** A toolbar with Run and Debug for the selected row, followed by [actions]. */
internal fun SimpleToolWindowPanel.installToolbar(project: Project, selection: ExampleRowSelection, vararg actions: AnAction) {
    val group = DefaultActionGroup()
    group.add(RunExampleAction(project, selection, DefaultRunExecutor.getRunExecutorInstance(), "tables.tool.window.run"))
    group.add(RunExampleAction(project, selection, DefaultDebugExecutor.getDebugExecutorInstance(), "tables.tool.window.debug"))
    if (actions.isNotEmpty()) group.add(Separator.getInstance())
    actions.forEach(group::add)
    val toolbar = ActionManager.getInstance().createActionToolbar("CucumberJsExampleRow", group, true)
    toolbar.targetComponent = this
    setToolbar(toolbar.component)
}

private class RunExampleAction(
    private val project: Project,
    private val selection: ExampleRowSelection,
    private val executor: Executor,
    textKey: String,
) : DumbAwareAction(MyMessageBundle.lazyMessage(textKey), MyMessageBundle.lazyMessage("$textKey.description"), executor.icon) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selection.row != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        selection.row?.let { ExampleRowRunner.run(project, it, executor) }
    }
}

/**
 * Shows the selected Examples row as `header | value` lines, optionally next to a pinned row.
 * Values can be edited in place, and selecting a line moves the editor caret to that cell.
 */
private class ExampleValuesPanel(private val project: Project, private val selection: ExampleRowSelection) :
    SimpleToolWindowPanel(true, true), Disposable {
    private val model = RecordTableModel()
    private val table = JBTable(model)
    private val title = JBLabel().apply { border = JBUI.Borders.empty(4, 8) }

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
            if (event.valueIsAdjusting || syncingSelection) return@addListSelectionListener
            model.record?.lines?.getOrNull(table.selectedRow)?.let { selection.navigateTo(it.column) }
        }

        val content = JPanel(BorderLayout())
        content.add(title, BorderLayout.NORTH)
        content.add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER)
        setContent(content)
        installToolbar(project, selection, PinRowAction(), DifferencesOnlyAction())

        selection.addListener(::refresh)
        refresh()
    }

    private fun refresh() {
        // Rebuilding the model would throw the edit away
        if (table.isEditing) return selection.schedule()
        val record = runReadActionBlocking {
            selection.row?.element?.let { ExampleRecord.of(it, pinned?.element, differencesOnly) }
        }
        title.text = record?.let { "${it.title} › ${it.rowName}" }.orEmpty()
        model.update(record)
        syncingSelection = true
        try {
            val line = record?.lines?.indexOfFirst { it.column == selection.column } ?: -1
            if (line >= 0) table.selectionModel.setSelectionInterval(line, line) else table.clearSelection()
        } finally {
            syncingSelection = false
        }
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
            selection.row?.let { ExamplesTables.setCell(project, it, line.column, text) }
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
            // The selection gets a new pointer when the caret moves to another row, so this one stays put
            pinned = if (state) selection.row else null
            refresh()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = selection.row != null || pinned != null
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
