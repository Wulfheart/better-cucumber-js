package com.wulfindustries.plugins.tables

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

class ScenarioSelectionTest : BasePlatformTestCase() {
    private lateinit var selection: ScenarioSelection
    private var notified = false

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile("calculator.feature")
        selection = ScenarioSelection(project)
        Disposer.register(testRootDisposable, selection)
        selection.addListener { notified = true }
    }

    fun testRowAtCaret() {
        moveCaretTo("| 2 |")
        assertInstanceOf(selection.target?.element, GherkinTableRow::class.java)
        assertSame(selection.target, selection.row)
    }

    fun testPlainScenarioAtCaret() {
        moveCaretTo("I have the number 2")
        assertEquals("Adding two numbers", (selection.target?.element as GherkinScenario).scenarioName)
        assertNull(selection.row)
    }

    fun testOutlineStepSelectsTheOutline() {
        moveCaretTo("I have the number <a>")
        assertInstanceOf(selection.target?.element, GherkinScenarioOutline::class.java)
        assertNull(selection.row)
    }

    fun testBackgroundAndFeatureSelectNothing() {
        moveCaretTo("I have the number 2")
        moveCaretTo("a calculator")
        assertNull(selection.target)
        moveCaretTo("I have the number 2")
        moveCaretTo("Feature: Calculator")
        assertNull(selection.target)
    }

    fun testCaretInTheScenarioTabKeepsTheTarget() {
        moveCaretTo("I have the number 2")
        val shown = selection.target
        val panel = ExpandedScenarioPanel(project, selection)
        Disposer.register(testRootDisposable, panel)
        notified = false
        // The tab's own editor is a Gherkin editor too; moving its caret must not clear what it shows
        val editor = EditorFactory.getInstance().allEditors
            .single { ExpandedScenarioPanel.isScenarioFile(FileDocumentManager.getInstance().getFile(it.document)!!) }
        editor.caretModel.moveToOffset(0)
        waitForSelection()
        assertSame(shown, selection.target)
    }

    private fun moveCaretTo(marker: String) {
        val offset = myFixture.file.text.indexOf(marker)
        assertTrue(marker, offset >= 0)
        notified = false
        myFixture.editor.caretModel.moveToOffset(offset)
        waitForSelection()
        assertTrue("selection not updated for $marker", notified)
    }

    /** Lets the selection's delayed caret handling run. */
    private fun waitForSelection() {
        val deadline = System.currentTimeMillis() + 2000
        while (!notified && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
    }

    override fun getTestDataPath(): String = "src/test/testData/features"
}
