package com.wulfindustries.plugins.run

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.wulfindustries.plugins.run.CucumberJsCommandLine.FeatureLocation

class ScenarioAnchorTest : BasePlatformTestCase() {
    // Anchors look feature files up by local path, so they must be real files
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private val text = """
        Feature: Anchors

          Scenario: Same name
            Given a step

          Rule: A rule

            Scenario: Same name
              Given a step

            Scenario Outline: Outline
              Given <a>

              Examples:
                | a |
                | 1 |
                | 2 |
    """.trimIndent() + "\n"

    private lateinit var path: String
    private lateinit var document: Document

    override fun setUp() {
        super.setUp()
        val file = myFixture.addFileToProject("anchors.feature", text)
        path = file.virtualFile.path
        document = PsiDocumentManager.getInstance(project).getDocument(file)!!
    }

    private fun anchor(line: Int): ScenarioAnchor = requireNotNull(ScenarioAnchor.create(project, path, line)) { "No anchor at $line" }

    private fun edit(change: Document.() -> Unit) {
        WriteCommandAction.runWriteCommandAction(project) { document.change() }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun lineStart(line: Int) = document.getLineStartOffset(line - 1)

    fun testOnlyScenariosAndRowsCanBeAnchored() {
        assertNull(ScenarioAnchor.create(project, path, 1))
        assertNull(ScenarioAnchor.create(project, path, 4))
        assertNull(ScenarioAnchor.create(project, path, 15))
        assertNotNull(ScenarioAnchor.create(project, path, 11))
    }

    fun testFollowsLinesAddedAbove() {
        val scenario = anchor(8)
        val row = anchor(17)
        edit { insertString(lineStart(3), "  # a comment\n\n") }
        assertEquals(10, scenario.line(project))
        assertEquals(19, row.line(project))
    }

    fun testFollowsRename() {
        val scenario = anchor(3)
        edit { replaceString(lineStart(3) + "  Scenario: ".length, lineStart(4) - 1, "Renamed") }
        assertEquals(3, scenario.line(project))
    }

    fun testStillFoundAfterWholeTextIsReplaced() {
        val inRule = anchor(8)
        val row = anchor(16)
        // As a reload from disk does; whether the markers survive this or not, the scenarios are found
        edit { setText("# header\n\n" + text) }
        assertEquals(10, inRule.line(project))
        assertEquals(18, row.line(project))
    }

    fun testDeletedScenarioIsGone() {
        val scenario = anchor(3)
        edit { deleteString(lineStart(3), lineStart(6)) }
        assertNull(scenario.line(project))
    }

    private fun pathAt(line: Int): ScenarioPath {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
        val offset = lineStart(line) + document.getText(TextRange(lineStart(line), lineStart(line + 1))).indexOfFirst { !it.isWhitespace() }
        return ScenarioPath.of(CucumberJsTargets.find(psiFile.findElementAt(offset)!!)!!.element)!!
    }

    private fun resolvedLine(path: ScenarioPath): Int? = path.resolve(project)?.let(CucumberJsTargets::lineOf)

    fun testPathTellsDuplicateNamesApartByRuleAndOrder() {
        val outside = pathAt(3)
        val inRule = pathAt(8)
        assertEquals(ScenarioPath(path, null, "Same name", 0, null), outside)
        assertEquals(ScenarioPath(path, "A rule", "Same name", 0, null), inRule)
        edit { insertString(lineStart(1), "@tag\n") }
        assertEquals(4, resolvedLine(outside))
        assertEquals(9, resolvedLine(inRule))
    }

    fun testPathFindsRowByItsCells() {
        val row = pathAt(17)
        assertEquals(ScenarioPath.Row(0, 1, listOf("2")), row.row)
        edit { setText(text.replace("| 1 |\n        | 2 |", "| 2 |\n        | 1 |")) }
        assertEquals(16, resolvedLine(row))
    }

    fun testFailedStepsMapToTheCurrentLinesOfTheirScenarios() {
        val root = SMTestProxy.SMRootTestProxy()
        val feature = SMTestProxy("Anchors", true, CucumberJsLocations.url(path))
        val cases = listOf(3, 8, 17).map { line ->
            SMTestProxy("case $line", true, CucumberJsLocations.url(path, line), CucumberMessagesTranslator.CASE_METAINFO, false)
        }
        val steps = cases.map { SMTestProxy("step", false, null).also(it::addChild) }
        root.addChild(feature)
        cases.forEach(feature::addChild)
        val anchors = ScenarioAnchors(project, root)
        Disposer.register(testRootDisposable, anchors)

        anchors.onSuiteStarted(cases.first())
        edit { insertString(lineStart(3), "\n") }

        assertEquals(
            listOf(FeatureLocation(path, listOf(4, 18))),
            anchors.locations(listOf(steps[2], steps[0], cases[0])),
        )
    }

    fun testPathFallsBackToRowIndexWhenCellsChanged() {
        val row = pathAt(17)
        edit { setText(text.replace("| 2 |", "| 3 |")) }
        assertEquals(17, resolvedLine(row))
    }
}
