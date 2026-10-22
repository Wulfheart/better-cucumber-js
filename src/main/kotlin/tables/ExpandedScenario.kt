package com.wulfindustries.plugins.tables

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.wulfindustries.plugins.MyMessageBundle
import com.wulfindustries.plugins.run.CucumberJsTargets
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinRule
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

/**
 * A scenario as cucumber-js runs it: the Backgrounds, then its steps. For an Examples row, those are the outline's
 * steps with every `<header>` replaced by the row's value; [substituted] are the ranges of [text] that came from the row.
 * Data tables flipped into `header: value` lines have their [keys] and [values], and [rowStarts] are the offsets of
 * their `# Row k` comments.
 */
data class ExpandedScenario(
    val text: String,
    val substituted: List<TextRange>,
    val rowStarts: List<Int> = emptyList(),
    val keys: List<TextRange> = emptyList(),
    val values: List<TextRange> = emptyList(),
) {
    companion object {
        private val LANGUAGE = Regex("""^\s*#\s*language:\s*\S+""")
        private val PLACEHOLDER = Regex("<([^<>\\n]+)>")
        private const val INDENT = "  "

        private const val NEWLINE = "⏎"

        /**
         * [target] as cucumber-js runs it: an Examples row with its values in the outline's placeholders, or a
         * Scenario, or a Scenario Outline with its placeholders left as written. Data tables wider than [maxWidth]
         * characters are flipped into `header: value` lines.
         */
        fun of(target: PsiElement, maxWidth: Int = Int.MAX_VALUE): ExpandedScenario? = when (target) {
            is GherkinTableRow -> ofRow(target, maxWidth)
            is GherkinScenarioOutline -> build(target, emptyMap(), null, maxWidth)
            is GherkinScenario -> target.takeUnless { it.isBackground }?.let { build(it, emptyMap(), null, maxWidth) }
            else -> null
        }

        private fun ofRow(row: GherkinTableRow, maxWidth: Int): ExpandedScenario? {
            val table = ExamplesTables.tableOf(row) ?: return null
            val rowIndex = ExamplesTables.rows(table).indexOf(row).takeIf { it > 0 } ?: return null
            val outline = PsiTreeUtil.getParentOfType(row, GherkinScenarioOutline::class.java) ?: return null
            val values = ExamplesTables.headers(table).zip(ExamplesTables.cells(row)).toMap()
            // `Examples: Fruit`, or `Examples` without a name
            val examples = ExamplesTables.examplesName(table.parent as GherkinExamplesBlock).removeSuffix(":")
            return build(outline, values, MyMessageBundle.message("tables.expanded.header", examples, rowIndex), maxWidth)
        }

        /** [scenario]'s text, with [values] in its placeholders and [header] as a comment on top. */
        private fun build(scenario: GherkinStepsHolder, values: Map<String, String>, header: String?, maxWidth: Int): ExpandedScenario {
            val out = Builder(values, keywordCheck(scenario), maxWidth)

            scenario.containingFile.text.lineSequence().takeWhile { it.isBlank() || it.trimStart().startsWith("#") }
                .firstOrNull(LANGUAGE::containsMatchIn)?.let { out.line(it.trim()) }
            header?.let { out.line("# $it") }

            // Without a Feature, Gherkin parses no steps, and the steps wouldn't resolve to their definitions
            val feature = PsiTreeUtil.getParentOfType(scenario, GherkinFeature::class.java)
            val featureKeyword = feature?.node?.findChildByType(GherkinTokenTypes.FEATURE_KEYWORD)?.text ?: "Feature"
            out.line("$featureKeyword: ${feature?.featureName.orEmpty()}".trimEnd())
            out.line("")

            for (background in backgrounds(scenario)) {
                reindent(background).forEach { out.line(if (it.isBlank()) it else INDENT + it) }
                out.line("")
            }

            // Not scenarioKeyword: with tags above the keyword, that is the first tag
            out.text("$INDENT${CucumberJsTargets.anchorOf(scenario).text}: ")
            out.substituted(scenario.scenarioName)
            out.line("")
            for (step in scenario.steps) out.step(reindent(step).map { INDENT + INDENT + it })
            return ExpandedScenario(out.result.toString().trimEnd() + "\n", out.ranges, out.rowStarts, out.flippedKeys, out.flippedValues)
        }

        /**
         * Whether a line would start with a Gherkin keyword (or a tag, comment, table or doc string) in [element]'s
         * language. Such a flipped table line would change how the text parses, so the steps after it wouldn't resolve.
         */
        private fun keywordCheck(element: PsiElement): (String) -> Boolean {
            val language = (element.containingFile as? GherkinFile)?.localeLanguage ?: "en"
            val provider = JsonGherkinKeywordProvider.getKeywordProvider(element)
            val keywords = provider.getAllKeywords(language)
            return { line ->
                val text = line.trimStart()
                listOf("@", "#", "|", "\"\"\"", "```").any(text::startsWith) || keywords.any { keyword ->
                    text.startsWith(keyword) && (!provider.isSpaceRequiredAfterKeyword(language, keyword) ||
                        text.length == keyword.length || text[keyword.length].let { it.isWhitespace() || it == ':' })
                }
            }
        }

        /** The feature's Background, then the enclosing Rule's: the order cucumber-js runs them in. */
        private fun backgrounds(scenario: GherkinStepsHolder): List<GherkinScenario> {
            val containers = listOfNotNull(
                PsiTreeUtil.getParentOfType(scenario, GherkinFeature::class.java),
                PsiTreeUtil.getParentOfType(scenario, GherkinRule::class.java),
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

    /** A table cell with its placeholders replaced; [ranges] are relative to [text]. */
    private class Cell(val text: String, val ranges: List<TextRange>)

    private class Builder(
        private val values: Map<String, String>,
        private val startsWithKeyword: (String) -> Boolean,
        private val maxWidth: Int = Int.MAX_VALUE,
    ) {
        val result = StringBuilder()
        val ranges = mutableListOf<TextRange>()
        val rowStarts = mutableListOf<Int>()
        val flippedKeys = mutableListOf<TextRange>()
        val flippedValues = mutableListOf<TextRange>()

        fun text(text: String) {
            result.append(text)
        }

        fun line(text: String) {
            result.append(text).append('\n')
        }

        /** Appends [text] with its placeholders replaced, using [escape] on the inserted values and [literal] on the rest. */
        fun substituted(text: String, escape: (String) -> String = { it }, literal: (String) -> String = { it }) {
            var last = 0
            for (match in PLACEHOLDER.findAll(text)) {
                val value = values[match.groupValues[1]] ?: continue
                result.append(literal(text.substring(last, match.range.first)))
                val start = result.length
                result.append(escape(value))
                ranges += TextRange(start, result.length)
                last = match.range.last + 1
            }
            result.append(literal(text.substring(last)))
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

        /** A data table, aligned, or flipped into `header: value` lines when the aligned table is wider than [maxWidth]. */
        private fun table(lines: List<String>) {
            val indent = lines.first().takeWhile { it == ' ' || it == '\t' }
            val cells = lines.map { splitCells(it.trim()) }
            // Each cell substituted on its own, with ranges relative to the cell, then laid out
            val rows = cells.map { row -> row.map { cell(it, ExamplesTables::escape) } }
            val widths = IntArray(rows.maxOf { it.size }) { column -> rows.maxOf { it.getOrNull(column)?.text?.length ?: 0 } }
            if (indent.length + widths.sumOf { it + 3 } + 1 > maxWidth) {
                val shown = cells.map { row -> row.map { cell(it, { value -> value.replace("\n", NEWLINE) }, ::unescape) } }
                if (flipped(indent, shown)) return
            }
            for (row in rows) {
                result.append(indent).append('|')
                row.forEachIndexed { column, cell ->
                    result.append(' ')
                    append(cell)
                    result.append(" ".repeat(widths[column] - cell.text.length)).append(" |")
                }
                result.append('\n')
            }
        }

        /**
         * Two columns as `key: value` lines, wider tables as one block of `header: value` lines per row. Returns
         * `false`, writing nothing, when flipping doesn't help or a line would start with a Gherkin keyword.
         */
        private fun flipped(indent: String, rows: List<List<Cell>>): Boolean {
            val columns = rows.maxOf { it.size }
            val keys = if (columns == 2) rows.map { it.first() } else rows.first()
            if (columns < 2 || columns > 2 && rows.size < 2 || keys.any { startsWithKeyword(it.text + ":") }) return false
            val keyWidth = keys.maxOf { it.text.length }

            fun line(key: Cell, value: Cell?) {
                result.append(indent)
                flippedKeys += append(key)
                result.append(':')
                if (!value?.text.isNullOrEmpty()) {
                    result.append(" ".repeat(keyWidth - key.text.length + 2))
                    flippedValues += append(value)
                }
                result.append('\n')
            }

            if (columns == 2) {
                for (row in rows) line(row.first(), row.getOrNull(1))
            } else {
                rows.drop(1).forEachIndexed { index, row ->
                    result.append(indent)
                    rowStarts += result.length
                    result.append("# ").append(MyMessageBundle.message("tables.expanded.row", index + 1)).append('\n')
                    keys.forEachIndexed { column, key -> line(key, row.getOrNull(column)) }
                }
            }
            return true
        }

        private fun cell(text: String, escape: (String) -> String, literal: (String) -> String = { it }): Cell {
            val cellBuilder = Builder(values, startsWithKeyword)
            cellBuilder.substituted(text, escape, literal)
            return Cell(cellBuilder.result.toString(), cellBuilder.ranges)
        }

        /** Appends [cell] and returns its range. */
        private fun append(cell: Cell): TextRange {
            val start = result.length
            result.append(cell.text)
            cell.ranges.forEach { ranges += it.shiftRight(start) }
            return TextRange(start, result.length)
        }

        private fun unescape(text: String): String = ExamplesTables.unescape(text).replace("\n", NEWLINE)

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
