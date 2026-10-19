package com.wulfindustries.plugins.tables

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import java.awt.Component
import java.awt.Font
import java.util.UUID
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

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

/** Space between the tabs' text and the left and right edges of the tool window. */
internal const val HORIZONTAL_PADDING = 10

/** A toolbar with Run, Debug and Locate for the selected row, followed by [actions]. */
internal fun SimpleToolWindowPanel.installToolbar(project: Project, selection: ExampleRowSelection, vararg actions: AnAction) {
    val group = DefaultActionGroup()
    group.add(RunExampleAction(project, selection, DefaultRunExecutor.getRunExecutorInstance(), "tables.tool.window.run"))
    group.add(RunExampleAction(project, selection, DefaultDebugExecutor.getDebugExecutorInstance(), "tables.tool.window.debug"))
    group.add(LocateRowAction(selection))
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

/** Shows the selected row in the editor, with the icon of the Project view's "Select Opened File". */
private class LocateRowAction(private val selection: ExampleRowSelection) : DumbAwareAction(
    MyMessageBundle.lazyMessage("tables.tool.window.locate"),
    MyMessageBundle.lazyMessage("tables.tool.window.locate.description"),
    AllIcons.General.Locate,
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selection.row != null
    }

    override fun actionPerformed(e: AnActionEvent) = selection.locate()
}

/**
 * Shows the selected Examples row as `header | value` lines, optionally next to a pinned row.
 * Values can be edited in place. With "Jump to cell on selection", selecting a line moves the editor caret to that
 * cell. Each Scenario Outline keeps its own pinned row.
 */
private class ExampleValuesPanel(private val project: Project, private val selection: ExampleRowSelection) :
    SimpleToolWindowPanel(true, true), Disposable {
    private val model = RecordTableModel()
    private val table = JBTable(model)
    private val properties = PropertiesComponent.getInstance(project)

    /** The pinned row of each Scenario Outline. */
    private val pins = mutableMapOf<SmartPsiElementPointer<GherkinScenarioOutline>, SmartPsiElementPointer<GherkinTableRow>>()
    private var differencesOnly = false
    private var syncingSelection = false

    private var jumpOnSelection: Boolean
        get() = properties.getBoolean(JUMP_ON_SELECTION_KEY, false)
        set(value) = properties.setValue(JUMP_ON_SELECTION_KEY, value, false)

    init {
        table.setShowGrid(false)
        table.emptyText.text = MyMessageBundle.message("tables.tool.window.empty")
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.setDefaultRenderer(Any::class.java, RecordCellRenderer())
        table.setDefaultEditor(Any::class.java, LiveCellEditor())
        table.tableHeader.defaultRenderer = HeaderRenderer(table.tableHeader.defaultRenderer)
        table.selectionModel.addListSelectionListener { event ->
            if (event.valueIsAdjusting || syncingSelection || !jumpOnSelection) return@addListSelectionListener
            model.record?.lines?.getOrNull(table.selectedRow)?.let { selection.navigateTo(it.column) }
        }

        // A border on the scroll pane itself doesn't survive, so the padding comes from a panel around it
        val content = BorderLayoutPanel().withBorder(JBUI.Borders.empty(0, HORIZONTAL_PADDING))
            .addToCenter(ScrollPaneFactory.createScrollPane(table, true))
        content.background = table.background
        setContent(content)
        installToolbar(project, selection, PinRowAction(), DifferencesOnlyAction(), JumpOnSelectionAction())

        selection.addListener(::refresh)
        refresh()
    }

    private fun refresh() {
        // Rebuilding the model would throw the edit away
        if (table.isEditing) return selection.schedule()
        val record = runReadActionBlocking {
            selection.row?.element?.let { ExampleRecord.of(it, pinnedRow(), differencesOnly) }
        }
        table.tableHeader.isVisible = record != null
        model.update(record)
        syncingSelection = true
        try {
            val line = record?.lines?.indexOfFirst { it.column == selection.column } ?: -1
            if (line >= 0) table.selectionModel.setSelectionInterval(line, line) else table.clearSelection()
        } finally {
            syncingSelection = false
        }
    }

    private fun outlineOf(row: GherkinTableRow): GherkinScenarioOutline? =
        PsiTreeUtil.getParentOfType(row, GherkinScenarioOutline::class.java)

    /** The pinned row of the shown row's Scenario Outline. Call in a read action. */
    private fun pinnedRow(): GherkinTableRow? {
        pins.entries.removeIf { it.key.element == null || it.value.element == null }
        val outline = selection.row?.element?.let(::outlineOf) ?: return null
        return pins.entries.firstOrNull { it.key.element == outline }?.value?.element
    }

    override fun dispose() {}

    private inner class RecordTableModel : AbstractTableModel() {
        var record: ExampleRecord? = null
            private set
        private val hasPinned get() = record?.pinnedName != null

        fun update(record: ExampleRecord?) {
            val structureChanged = (record?.pinnedName != null) != hasPinned || record?.rowName != this.record?.rowName ||
                record?.title != this.record?.title ||
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

        // No setValueAt: LiveCellEditor has already written every change to the file
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == columnCount - 1
    }

    /** Writes the value to the feature file on every keystroke; Escape puts the original value back. */
    private inner class LiveCellEditor : DefaultCellEditor(JBTextField()) {
        private val field = component as JBTextField
        private var row: SmartPsiElementPointer<GherkinTableRow>? = null
        private var column = 0
        private var original = ""
        private var written = ""

        /** Merges the commands of one edit, so a single undo reverts it. */
        private var groupId = ""
        private var loading = false

        init {
            field.border = JBUI.Borders.empty(0, 1)
            field.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    if (!loading) write(field.text)
                }
            })
        }

        override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, rowIndex: Int, columnIndex: Int): Component {
            val line = model.record?.lines?.getOrNull(rowIndex)
            row = selection.row.takeIf { line != null }
            column = line?.column ?: 0
            original = line?.value.orEmpty()
            written = original
            groupId = UUID.randomUUID().toString()
            loading = true
            try {
                return super.getTableCellEditorComponent(table, value, isSelected, rowIndex, columnIndex)
            } finally {
                loading = false
            }
        }

        override fun cancelCellEditing() {
            write(original)
            super.cancelCellEditing()
        }

        private fun write(text: String) {
            val row = row ?: return
            if (text == written) return
            written = text
            val column = column
            val groupId = groupId
            // Not inside the text field's document event, which can't start a write command
            ApplicationManager.getApplication().invokeLater({ ExamplesTables.setCell(project, row, column, text, groupId) }, project.disposed)
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

    /** Bold column names with a line below. */
    private class HeaderRenderer(private val delegate: TableCellRenderer) : TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int,
        ): Component = delegate.getTableCellRendererComponent(table, value, selected, hasFocus, row, column).apply {
            font = table.font.deriveFont(Font.BOLD)
            (this as? JComponent)?.border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(4, 2),
            )
        }
    }

    private inner class PinRowAction : DumbAwareToggleAction(
        MyMessageBundle.lazyMessage("tables.tool.window.pin"),
        MyMessageBundle.lazyMessage("tables.tool.window.pin.description"),
        AllIcons.General.Pin_tab,
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = runReadActionBlocking { pinnedRow() != null }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            runReadActionBlocking {
                // The selection gets a new pointer when the caret moves to another row, so this one stays put
                val row = selection.row ?: return@runReadActionBlocking
                val outline = row.element?.let(::outlineOf) ?: return@runReadActionBlocking
                pins.keys.removeIf { it.element == outline }
                if (state) pins[SmartPointerManager.createPointer(outline)] = row
            }
            refresh()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = selection.row != null
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
            e.presentation.isEnabled = runReadActionBlocking { pinnedRow() != null }
        }
    }

    private inner class JumpOnSelectionAction : DumbAwareToggleAction(
        MyMessageBundle.lazyMessage("tables.tool.window.jump"),
        MyMessageBundle.lazyMessage("tables.tool.window.jump.description"),
        AllIcons.General.AutoscrollToSource,
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = jumpOnSelection

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            jumpOnSelection = state
        }
    }

    private companion object {
        const val JUMP_ON_SELECTION_KEY = "cucumberjs.exampleRow.jumpOnSelection"
    }
}
