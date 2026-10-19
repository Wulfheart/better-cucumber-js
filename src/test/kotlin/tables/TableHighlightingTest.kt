package com.wulfindustries.plugins.tables

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell

class TableHighlightingTest : BasePlatformTestCase() {
    private val feature = """
        Feature: Shop
          Scenario Outline: Ordering
            Given I order <amount> <item>
              | inline |
            Examples: Fruit
              | item  | amount | note |
              | apple | 3      |      |
              | pear  | 12     | ripe |
    """.trimIndent()

    private fun configure() = myFixture.configureByText("a.feature", feature)

    private fun textOf(range: TextRange) = range.substring(myFixture.editor.document.text)

    fun testRainbowColumnsColorExamplesCellsByColumn() {
        configure()
        val colored = myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey in TableColors.COLUMNS }
            .map { feature.substring(it.startOffset, it.endOffset) to TableColors.COLUMNS.indexOf(it.forcedTextAttributesKey) }
            .toSet()

        assertEquals(
            setOf("item" to 0, "amount" to 1, "note" to 2, "apple" to 0, "3" to 1, "pear" to 0, "12" to 1, "ripe" to 2),
            colored,
        )
    }

    fun testRainbowColumnsCanBeTurnedOff() {
        val state = CucumberJsTableSettings.getInstance().state
        state.rainbowColumns = false
        try {
            configure()
            assertEmpty(myFixture.doHighlighting().filter { it.forcedTextAttributesKey in TableColors.COLUMNS })
        } finally {
            state.rainbowColumns = true
        }
    }

    private fun currentColumnTexts(): List<String> {
        CurrentColumnHighlighter.updateNow(myFixture.editor)
        return myFixture.editor.markupModel.allHighlighters
            .filter { it.textAttributesKey == TableColors.CURRENT_COLUMN }
            .sortedBy { it.startOffset }
            .map { textOf(it.textRange) }
    }

    fun testCurrentColumnIsHighlightedInEveryRow() {
        configure()
        myFixture.editor.caretModel.moveToOffset(feature.indexOf("12"))
        assertEquals(listOf(" amount ", " 3      ", " 12     "), currentColumnTexts())

        // Empty cells count as a column too
        myFixture.editor.caretModel.moveToOffset(feature.indexOf("|      |") + 3)
        assertEquals(listOf(" note ", "      ", " ripe "), currentColumnTexts())

        myFixture.editor.caretModel.moveToOffset(feature.indexOf("inline"))
        assertEmpty(currentColumnTexts())
    }

    fun testHighlighterIsInstalledForFeatureEditors() {
        val file = configure()
        val editor = (FileEditorManager.getInstance(project).getSelectedEditor(file.virtualFile) as TextEditor).editor
        editor.caretModel.moveToOffset(feature.indexOf("pear"))
        // Throws if no tracker was installed by the editor factory listener
        CurrentColumnHighlighter.updateNow(editor)
    }

    fun testBreadcrumbs() {
        val file = configure()
        val provider = BreadcrumbsProvider.EP_NAME.extensionList.single { it is GherkinBreadcrumbsProvider }
        val leaf = file.findElementAt(feature.indexOf("ripe"))!!
        val crumbs = generateSequence(leaf) { it.parent }.filter(provider::acceptElement).map(provider::getElementInfo).toList().reversed()
        assertEquals(listOf("Shop", "Ordering", "Examples: Fruit", "row 2", "note"), crumbs)

        val cell = generateSequence(leaf) { it.parent }.first { it is GherkinTableCell }
        assertEquals("note (column 3): ripe", provider.getElementTooltip(cell))
    }

    fun testRecordComparesWithPinnedRow() {
        val file = configure()
        val apple = ExamplesTables.positionAt(file, feature.indexOf("apple"))!!.row
        val pear = ExamplesTables.positionAt(file, feature.indexOf("pear"))!!.row

        val record = ExampleRecord.of(pear, apple, differencesOnly = false)!!
        assertEquals("Ordering › Examples: Fruit", record.title)
        assertEquals("row 2", record.rowName)
        assertEquals("row 1", record.pinnedName)
        assertEquals(
            listOf(RecordLine(0, "item", "apple", "pear"), RecordLine(1, "amount", "3", "12"), RecordLine(2, "note", "", "ripe")),
            record.lines,
        )

        val same = ExampleRecord.of(pear, pear, differencesOnly = true)!!
        assertEmpty(same.lines)
        assertNull(ExampleRecord.of(pear, null, differencesOnly = true)!!.lines.firstOrNull()?.pinned)
    }

    fun testSetCellEscapesRealignsAndUndoes() {
        val file = configure()
        val apple = ExamplesTables.positionAt(file, feature.indexOf("apple"))!!.row

        ExamplesTables.setCell(project, SmartPointerManager.createPointer(apple), 2, "very | ripe")

        val expected = feature
            .replace("| item  | amount | note |", "| item  | amount | note         |")
            .replace("| apple | 3      |      |", "| apple | 3      | very \\| ripe |")
            .replace("| pear  | 12     | ripe |", "| pear  | 12     | ripe         |")
        assertEquals(expected, myFixture.editor.document.text)

        val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(file.virtualFile)
        UndoManager.getInstance(project).undo(textEditor)
        assertEquals(feature, myFixture.editor.document.text)
    }
}
