package com.wulfindustries.plugins.tables

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.wulfindustries.plugins.MyMessageBundle
import com.wulfindustries.plugins.run.CucumberJsTargets
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinRule
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/**
 * A Scenario Outline as cucumber-js runs it for one Examples row: the Backgrounds, then the outline's steps
 * with every `<header>` replaced by the row's value. [substituted] are the ranges of [text] that came from the row.
 */
data class ExpandedScenario(val text: String, val substituted: List<TextRange>) {
    companion object {
        private val LANGUAGE = Regex("""^\s*#\s*language:\s*\S+""")
        private val PLACEHOLDER = Regex("<([^<>\\n]+)>")
        private const val INDENT = "  "

        fun of(row: GherkinTableRow): ExpandedScenario? {
            val table = ExamplesTables.tableOf(row) ?: return null
            val rowIndex = ExamplesTables.rows(table).indexOf(row).takeIf { it > 0 } ?: return null
            val outline = PsiTreeUtil.getParentOfType(row, GherkinScenarioOutline::class.java) ?: return null
            val values = ExamplesTables.headers(table).zip(ExamplesTables.cells(row)).toMap()
            val out = Builder(values)

            row.containingFile.text.lineSequence().takeWhile { it.isBlank() || it.trimStart().startsWith("#") }
                .firstOrNull(LANGUAGE::containsMatchIn)?.let { out.line(it.trim()) }
            val examples = ExamplesTables.examplesName(table.parent as GherkinExamplesBlock)
            out.line("# " + MyMessageBundle.message("tables.expanded.header", examples, rowIndex))

            for (background in backgrounds(outline)) {
                reindent(background).forEach(out::line)
                out.line("")
            }

            // Not scenarioKeyword: with tags above the keyword, that is the first tag
            out.text("${CucumberJsTargets.anchorOf(outline).text}: ")
            out.substituted(outline.scenarioName)
            out.line("")
            for (step in outline.steps) out.step(reindent(step).map { INDENT + it })
            return ExpandedScenario(out.result.toString().trimEnd() + "\n", out.ranges)
        }

        /** The feature's Background, then the enclosing Rule's: the order cucumber-js runs them in. */
        private fun backgrounds(outline: GherkinScenarioOutline): List<GherkinScenario> {
            val containers = listOfNotNull(
                PsiTreeUtil.getParentOfType(outline, GherkinFeature::class.java),
                PsiTreeUtil.getParentOfType(outline, GherkinRule::class.java),
            )
            return containers.flatMap { container ->
                PsiTreeUtil.getChildrenOfTypeAsList(container, GherkinScenario::class.java).filter { it.isBackground }
            }
        }

        /** [element]'s lines, with the indentation of its first line removed from all of them. */
        private fun reindent(element: PsiElement): List<String> {
            val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)
            val start = element.textRange.startOffset
            val column = document?.let { start - it.getLineStartOffset(it.getLineNumber(start)) } ?: 0
            val lines = element.text.trimEnd().lines()
            return listOf(lines.first()) + lines.drop(1).map { line ->
                line.drop(line.takeWhile { it == ' ' || it == '\t' }.length.coerceAtMost(column))
            }
        }
    }

    private class Builder(private val values: Map<String, String>) {
        val result = StringBuilder()
        val ranges = mutableListOf<TextRange>()

        fun text(text: String) {
            result.append(text)
        }

        fun line(text: String) {
            result.append(text).append('\n')
        }

        /** Appends [text] with its placeholders replaced, using [escape] on the inserted values. */
        fun substituted(text: String, escape: (String) -> String = { it }) {
            var last = 0
            for (match in PLACEHOLDER.findAll(text)) {
                val value = values[match.groupValues[1]] ?: continue
                result.append(text, last, match.range.first)
                val start = result.length
                result.append(escape(value))
                ranges += TextRange(start, result.length)
                last = match.range.last + 1
            }
            result.append(text, last, text.length)
        }

        /** A step and its argument; substituted data tables are re-aligned. */
        fun step(lines: List<String>) {
            var inDocString: String? = null
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.trimStart()
                val delimiter = listOf("\"\"\"", "```").firstOrNull(trimmed::startsWith)
                when {
                    inDocString != null -> {
                        if (delimiter == inDocString) inDocString = null
                        substituted(line)
                        result.append('\n')
                    }
                    delimiter != null -> {
                        inDocString = delimiter
                        line(line)
                    }
                    trimmed.startsWith("|") -> {
                        val block = lines.drop(i).takeWhile { it.trimStart().startsWith("|") }
                        table(block)
                        i += block.size
                        continue
                    }
                    trimmed.startsWith("#") -> {}
                    else -> {
                        substituted(line)
                        result.append('\n')
                    }
                }
                i++
            }
        }

        private fun table(lines: List<String>) {
            val indent = lines.first().takeWhile { it == ' ' || it == '\t' }
            // Each cell substituted on its own, with ranges relative to the cell, then laid out aligned
            val rows = lines.map { line ->
                splitCells(line.trim()).map { cell ->
                    val cellBuilder = Builder(values)
                    cellBuilder.substituted(cell, ExamplesTables::escape)
                    cellBuilder.result.toString() to cellBuilder.ranges
                }
            }
            val widths = IntArray(rows.maxOf { it.size }) { column -> rows.maxOf { it.getOrNull(column)?.first?.length ?: 0 } }
            for (row in rows) {
                result.append(indent).append('|')
                row.forEachIndexed { column, (cell, cellRanges) ->
                    result.append(' ')
                    val start = result.length
                    result.append(cell)
                    cellRanges.forEach { ranges += it.shiftRight(start) }
                    result.append(" ".repeat(widths[column] - cell.length)).append(" |")
                }
                result.append('\n')
            }
        }

        /** The trimmed cells of a `| a | b |` line; escaped pipes stay in their cell. */
        private fun splitCells(line: String): List<String> {
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var i = 1
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '\\' && i + 1 < line.length -> { cell.append(c).append(line[i + 1]); i += 2; continue }
                    c == '|' -> { cells += cell.toString().trim(); cell.clear() }
                    else -> cell.append(c)
                }
                i++
            }
            return cells
        }
    }
}
