package com.wulfindustries.plugins.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

/** Shows the selected row's scenario as cucumber-js runs it, placeholders replaced by the row's values. */
internal class ExpandedScenarioPanel(private val project: Project, private val selection: ExampleRowSelection) :
    SimpleToolWindowPanel(true, true), Disposable {
    private val document = EditorFactory.getInstance().createDocument("")
    private val editor = (EditorFactory.getInstance().createViewer(document, project) as EditorEx).apply {
        highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, GherkinFileType.INSTANCE)
        settings.isLineNumbersShown = false
        settings.isFoldingOutlineShown = false
        settings.isLineMarkerAreaShown = false
        settings.isUseSoftWraps = true
        settings.additionalLinesCount = 0
        settings.isCaretRowShown = false
    }
    private val empty = JBPanelWithEmptyText().withEmptyText(MyMessageBundle.message("tables.tool.window.empty"))
    private val body = BorderLayoutPanel()

    init {
        setContent(body)
        installToolbar(project, selection)
        selection.addListener(::refresh)
        refresh()
    }

    private fun refresh() {
        val expanded = runReadActionBlocking { selection.row?.element?.let(ExpandedScenario::of) }
        body.removeAll()
        body.addToCenter(if (expanded == null) empty else editor.component)
        body.revalidate()
        body.repaint()
        if (expanded == null || expanded.text == document.text) return

        WriteAction.run<RuntimeException> { document.setText(expanded.text) }
        editor.markupModel.removeAllHighlighters()
        for (range in expanded.substituted) {
            editor.markupModel.addRangeHighlighter(
                TableColors.SUBSTITUTED_VALUE, range.startOffset, range.endOffset,
                HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
            )
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
