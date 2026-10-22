package com.wulfindustries.plugins.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.wulfindustries.plugins.MyMessageBundle
import com.wulfindustries.plugins.run.CucumberJsTargets
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinHighlighter
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Shows the selected row's scenario as cucumber-js runs it, placeholders replaced by the row's values. The text is an
 * in-memory feature file standing in for the row's own ([linkTo]), so its steps resolve: Ctrl+click opens the
 * step definition. Data tables wider than the viewer are flipped into `header: value` lines.
 */
internal class ExpandedScenarioPanel(private val project: Project, private val selection: ExampleRowSelection) :
    SimpleToolWindowPanel(true, true), Disposable {
    private val file = scenarioFile()
    private val document = FileDocumentManager.getInstance().getDocument(file)!!

    // Created before any text is set: PSI made later would parse the file's empty content, not the document
    private val psiFile = PsiManager.getInstance(project).findFile(file)!!
    private val editor = (EditorFactory.getInstance().createViewer(document, project) as EditorEx).apply {
        highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
        settings.isLineNumbersShown = false
        settings.isFoldingOutlineShown = false
        settings.isLineMarkerAreaShown = false
        settings.isUseSoftWraps = true
        settings.isUseCustomSoftWrapIndent = true
        settings.customSoftWrapIndent = SOFT_WRAP_INDENT
        settings.additionalLinesCount = 0
        settings.isCaretRowShown = false
        // Inside the editor, so the padding is painted in the editor's background
        contentComponent.border = JBUI.Borders.empty(0, HORIZONTAL_PADDING)
    }
    private val empty = JBPanelWithEmptyText().withEmptyText(MyMessageBundle.message("tables.tool.window.empty"))
    private val body = BorderLayoutPanel()

    /** The width the shown text was laid out for. */
    private var columns = Int.MAX_VALUE

    init {
        setContent(body)
        installToolbar(project, selection)
        selection.addListener(::refresh)
        // Tables flip or unflip when the viewer gets narrower or wider than them
        editor.component.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (visibleColumns() != columns) refresh()
            }
        })
        refresh()
    }

    /** How many characters fit across the viewer, or no limit before it's laid out. */
    private fun visibleColumns(): Int {
        val width = editor.scrollingModel.visibleArea.width
        val padding = editor.contentComponent.insets.let { it.left + it.right }
        return if (width <= 0) Int.MAX_VALUE else (width - padding) / EditorUtil.getPlainSpaceWidth(editor) - 1
    }

    private fun refresh() {
        columns = visibleColumns()
        val expanded = runReadActionBlocking {
            val row = selection.row?.element
            row?.let { ExpandedScenario.of(it, columns) }?.also { linkTo(psiFile, row.containingFile) }
        }
        body.removeAll()
        body.addToCenter(if (expanded == null) empty else editor.component)
        body.revalidate()
        body.repaint()
        if (expanded == null || expanded.text == document.text) return
        editor.inlayModel.getBlockElementsInRange(0, document.textLength).forEach(Disposer::dispose)
        WriteAction.run<RuntimeException> {
            document.setText(expanded.text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        editor.markupModel.removeAllHighlighters()
        // Flipped data tables read like tables: keys like header cells, values like cells, some space above each row
        highlight(expanded.keys, GherkinHighlighter.TABLE_HEADER_CELL, HighlighterLayer.SYNTAX)
        highlight(expanded.values, GherkinHighlighter.TABLE_CELL, HighlighterLayer.SYNTAX)
        highlight(expanded.substituted, TableColors.SUBSTITUTED_VALUE, HighlighterLayer.ADDITIONAL_SYNTAX)
        for (offset in expanded.rowStarts) editor.inlayModel.addBlockElement(offset, false, true, 0, RowGap)
    }

    private fun highlight(ranges: List<TextRange>, key: TextAttributesKey, layer: Int) {
        for (range in ranges) {
            editor.markupModel.addRangeHighlighter(key, range.startOffset, range.endOffset, layer, HighlighterTargetArea.EXACT_RANGE)
        }
    }

    /** Half a line of empty space above a `# Row k` comment. */
    private object RowGap : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0

        override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight / 2
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }

    companion object {
        private const val SOFT_WRAP_INDENT = 4
        private val SCENARIO_FILE = Key.create<Boolean>("cucumberjs.expandedScenarioFile")

        /** A new in-memory feature file for the expanded text, recognized by [isScenarioFile]. */
        fun scenarioFile(): LightVirtualFile =
            LightVirtualFile("scenario.feature", GherkinFileType.INSTANCE, "").apply { putUserData(SCENARIO_FILE, true) }

        /** Whether [file] shows an expanded scenario ([scenarioFile]) rather than a real feature file. */
        fun isScenarioFile(file: PsiFile): Boolean = isScenarioFile(file.viewProvider.virtualFile)

        fun isScenarioFile(file: VirtualFile): Boolean = file.getUserData(SCENARIO_FILE) == true

        /**
         * Makes the steps of the in-memory [file] resolve as if they were in [original]: Gherkin looks step definitions
         * up in the module of the file's context, and [CucumberJsTargets.isCucumberJsFeature] judges the light file's
         * original file. Call in a read action.
         */
        fun linkTo(file: PsiFile, original: PsiFile?) {
            (file.viewProvider.virtualFile as? LightVirtualFile)?.originalFile = original?.virtualFile
            file.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, original?.let { SmartPointerManager.createPointer(it) })
        }
    }
}
