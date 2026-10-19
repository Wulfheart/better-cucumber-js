package com.wulfindustries.plugins.run

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinRule
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/**
 * Keeps track of a scenario or example row of a run while its feature file is edited, so "Rerun Failed Tests" still
 * finds it after lines were added or removed above it.
 *
 * A [RangeMarker] on the scenario keyword or the row follows edits made in the IDE, renames included. Once the marker
 * is gone (the text it covered was replaced, for example by a reload from disk), the scenario is looked up again by
 * its [ScenarioPath]. Call everything in a read action.
 */
class ScenarioAnchor private constructor(private val marker: RangeMarker, private val path: ScenarioPath) {
    val featurePath: String get() = path.featurePath

    /** The current 1-based line of the scenario or row, or `null` once it is no longer in the file. */
    fun line(project: Project): Int? {
        if (marker.isValid) return marker.document.getLineNumber(marker.startOffset) + 1
        return path.resolve(project)?.let(CucumberJsTargets::lineOf)
    }

    fun dispose() = marker.dispose()

    companion object {
        /** Anchors the scenario or example row at [line] (1-based) of the feature file at [featurePath]. */
        fun create(project: Project, featurePath: String, line: Int): ScenarioAnchor? {
            val file = LocalFileSystem.getInstance().findFileByPath(featurePath) ?: return null
            val psiFile = PsiManager.getInstance(project).findFile(file) as? GherkinFile ?: return null
            val documents = PsiDocumentManager.getInstance(project)
            val document = documents.getDocument(psiFile) ?: return null
            if (documents.isUncommited(document) || line < 1 || line > document.lineCount) return null
            val lineStart = document.getLineStartOffset(line - 1)
            val offset = CharArrayUtil.shiftForward(document.charsSequence, lineStart, document.getLineEndOffset(line - 1), " \t")
            val target = psiFile.findElementAt(offset)?.let(CucumberJsTargets::find) ?: return null
            if (target.isFeature || target.line != line) return null
            val path = ScenarioPath.of(target.element) ?: return null
            return ScenarioAnchor(document.createRangeMarker(CucumberJsTargets.anchorOf(target.element).textRange), path)
        }
    }
}

/**
 * Where a scenario or example row sits in its feature file, by name rather than by line: rule, scenario name and
 * which of the scenarios with that name it is, plus the Examples table and cell values of a row.
 */
data class ScenarioPath(
    val featurePath: String,
    val ruleName: String?,
    val scenarioName: String,
    /** Position among the scenarios of the file with the same rule and name. */
    val occurrence: Int,
    val row: Row?,
) {
    data class Row(val examplesIndex: Int, val rowIndex: Int, val cells: List<String>)

    /** The scenario, outline or example row this path names in the current feature file. */
    fun resolve(project: Project): PsiElement? {
        val file = LocalFileSystem.getInstance().findFileByPath(featurePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) as? GherkinFile ?: return null
        val candidates = sameNamed(psiFile, ruleName, scenarioName)
        val holder = candidates.getOrNull(occurrence) ?: candidates.singleOrNull() ?: return null
        if (row == null) return holder
        val examples = (holder as? GherkinScenarioOutline)?.examplesBlocks?.getOrNull(row.examplesIndex) ?: return null
        val rows = examples.table?.dataRows.orEmpty()
        return rows.firstOrNull { cells(it) == row.cells } ?: rows.getOrNull(row.rowIndex)
    }

    companion object {
        /** The path of a scenario, scenario outline or Examples data row. */
        fun of(element: PsiElement): ScenarioPath? {
            val tableRow = element as? GherkinTableRow
            val holder = if (tableRow == null) element as? GherkinStepsHolder
            else PsiTreeUtil.getParentOfType(tableRow, GherkinScenarioOutline::class.java)
            holder ?: return null
            val file = holder.containingFile as? GherkinFile ?: return null
            val featurePath = file.virtualFile?.path ?: return null
            val ruleName = ruleName(holder)
            val occurrence = sameNamed(file, ruleName, holder.scenarioName).indexOf(holder)
            val row = tableRow?.let {
                val examples = PsiTreeUtil.getParentOfType(it, GherkinExamplesBlock::class.java) ?: return null
                val outline = holder as GherkinScenarioOutline
                Row(outline.examplesBlocks.indexOf(examples), examples.table?.dataRows.orEmpty().indexOf(it), cells(it))
            }
            return ScenarioPath(featurePath, ruleName, holder.scenarioName, occurrence, row)
        }

        private fun ruleName(holder: GherkinStepsHolder): String? =
            PsiTreeUtil.getParentOfType(holder, GherkinRule::class.java)?.ruleName

        /** Scenarios and outlines of [file] in the rule named [ruleName] with the name [scenarioName], in file order. */
        private fun sameNamed(file: GherkinFile, ruleName: String?, scenarioName: String): List<GherkinStepsHolder> =
            PsiTreeUtil.findChildrenOfType(file, GherkinStepsHolder::class.java).filter {
                CucumberJsTargets.runnableScenario(it) === it && it.scenarioName == scenarioName && ruleName(it) == ruleName
            }

        private fun cells(row: GherkinTableRow): List<String> = row.psiCells.map { it.text.trim() }
    }
}
