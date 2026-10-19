package com.wulfindustries.plugins.tables

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * Where an offset sits in an Examples table. [rowIndex] is 0 for the header row and k for the k-th data row,
 * [column] is 0-based and `null` when the offset is on the row but outside its cells (indent or after the last pipe).
 */
data class ExamplesTablePosition(
    val examples: GherkinExamplesBlock,
    val table: GherkinTable,
    val row: GherkinTableRow,
    val rowIndex: Int,
    val column: Int?,
) {
    val isHeader: Boolean get() = rowIndex == 0
}

/**
 * Column-aware view of Scenario Outline Examples tables. Columns are the spans between pipes, not
 * [GherkinTableRow.getPsiCells], because an empty cell has no PSI and would shift every later column.
 */
object ExamplesTables {
    private val PIPES = TokenSet.create(GherkinTokenTypes.PIPE)

    /** The Examples table [element] is in, or `null` for step data tables and everything else. */
    fun tableOf(element: PsiElement): GherkinTable? {
        val table = PsiTreeUtil.getParentOfType(element, GherkinTable::class.java, false) ?: return null
        return table.takeIf { it.parent is GherkinExamplesBlock }
    }

    fun rows(table: GherkinTable): List<GherkinTableRow> = listOfNotNull(table.headerRow) + table.dataRows

    fun positionAt(file: PsiFile, offset: Int): ExamplesTablePosition? {
        // At the end of a row the caret sits before the line break, which belongs to the table, not the row
        val element = file.findElementAt(offset)?.takeUnless { offset > 0 && it.text.startsWith("\n") && it.textRange.startOffset == offset }
            ?: file.findElementAt(offset - 1)
            ?: return null
        val row = PsiTreeUtil.getParentOfType(element, GherkinTableRow::class.java, false) ?: return null
        val table = tableOf(row) ?: return null
        val examples = table.parent as GherkinExamplesBlock
        val rowIndex = rows(table).indexOf(row).takeIf { it >= 0 } ?: return null
        val column = cellSpans(row).indexOfFirst { offset >= it.startOffset && offset <= it.endOffset }.takeIf { it >= 0 }
        return ExamplesTablePosition(examples, table, row, rowIndex, column)
    }

    /** The text between each pair of neighbouring pipes, including the padding, in document offsets. */
    fun cellSpans(row: GherkinTableRow): List<TextRange> {
        val pipes = row.node.getChildren(PIPES).map { it.textRange }
        return pipes.zipWithNext { left, right -> TextRange(left.endOffset, right.startOffset) }
    }

    /** [cellSpans] without the padding; an empty cell gives an empty range in the middle of its span. */
    fun cellContentRanges(row: GherkinTableRow): List<TextRange> {
        val rowStart = row.textRange.startOffset
        val text = row.text
        return cellSpans(row).map { span ->
            val content = span.shiftLeft(rowStart).substring(text)
            val start = content.indexOfFirst { !it.isWhitespace() }
            if (start < 0) {
                TextRange.from(span.startOffset + span.length / 2, 0)
            } else {
                val end = content.indexOfLast { !it.isWhitespace() } + 1
                TextRange(span.startOffset + start, span.startOffset + end)
            }
        }
    }

    /** The raw text of each cell, escapes included. */
    fun rawCells(row: GherkinTableRow): List<String> {
        val rowStart = row.textRange.startOffset
        val text = row.text
        return cellContentRanges(row).map { it.shiftLeft(rowStart).substring(text) }
    }

    /** The cell values as cucumber-js sees them, with `\|`, `\n` and `\\` unescaped. */
    fun cells(row: GherkinTableRow): List<String> = rawCells(row).map(::unescape)

    fun headers(table: GherkinTable): List<String> = table.headerRow?.let(::cells).orEmpty()

    /** "Outline name › Examples name", for titles. */
    fun describe(examples: GherkinExamplesBlock): String {
        val outline = PsiTreeUtil.getParentOfType(examples, GherkinScenarioOutline::class.java)?.scenarioName.orEmpty()
        val name = examplesName(examples)
        return listOf(outline, name).filter { it.isNotBlank() }.joinToString(" › ")
    }

    /** The Examples block's keyword line, e.g. `Examples: Negative numbers`. */
    fun examplesName(examples: GherkinExamplesBlock): String {
        // Starts at the keyword, because tags above it are part of the block
        val keyword = examples.node.findChildByType(GherkinTokenTypes.EXAMPLES_KEYWORD) ?: return ""
        val text = examples.text.substring(keyword.startOffset - examples.textRange.startOffset)
        return text.lineSequence().first().trim()
    }

    /**
     * Replaces the value in [column] of [row] with [value], escaped, and re-aligns the table.
     * Runs as one undoable command, merged with the earlier commands of the same [groupId].
     */
    fun setCell(project: Project, rowPointer: SmartPsiElementPointer<GherkinTableRow>, column: Int, value: String, groupId: String? = null) {
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.writeCommandAction(project).withName(MyMessageBundle.message("tables.edit.command"))
            .withGroupId(groupId).run<RuntimeException> {
            val row = rowPointer.element ?: return@run
            val file = row.containingFile
            val document = documents.getDocument(file) ?: return@run
            val table = tableOf(row) ?: return@run
            val span = cellSpans(row).getOrNull(column) ?: return@run
            val tableRange = document.createRangeMarker(table.textRange)
            document.replaceString(span.startOffset, span.endOffset, " ${escape(value)} ")
            documents.commitDocument(document)
            CodeStyleManager.getInstance(project).reformatText(file, tableRange.startOffset, tableRange.endOffset)
            tableRange.dispose()
        }
    }

    fun unescape(raw: String): String = buildString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    '|' -> { append('|'); i += 2; continue }
                    'n' -> { append('\n'); i += 2; continue }
                    '\\' -> { append('\\'); i += 2; continue }
                }
            }
            append(c)
            i++
        }
    }

    fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "\\n").replace("\n", "\\n")
}
