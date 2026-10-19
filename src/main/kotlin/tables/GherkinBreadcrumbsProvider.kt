package com.wulfindustries.plugins.tables

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import org.jetbrains.plugins.cucumber.psi.GherkinRule
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/**
 * Breadcrumbs for `.feature` files: Feature › Rule › Scenario › Examples › row › column,
 * so the position in a wide, soft-wrapped Examples table is always visible.
 */
class GherkinBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(GherkinLanguage.INSTANCE)

    override fun acceptElement(element: PsiElement): Boolean = when (element) {
        is GherkinFeature, is GherkinRule, is GherkinStepsHolder, is GherkinExamplesBlock -> true
        is GherkinTableRow, is GherkinTableCell -> ExamplesTables.tableOf(element) != null
        else -> false
    }

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is GherkinFeature -> element.featureName
        is GherkinRule -> element.ruleName
        is GherkinStepsHolder -> element.scenarioName.ifBlank { element.scenarioKeyword }
        is GherkinExamplesBlock -> ExamplesTables.examplesName(element)
        is GherkinTableRow -> rowName(element)
        is GherkinTableCell -> cellColumn(element)?.let { (header, _) -> header }.orEmpty()
        else -> ""
    }.ifBlank { element.text.lineSequence().first().trim() }

    override fun getElementTooltip(element: PsiElement): String? = when (element) {
        is GherkinTableCell -> cellColumn(element)?.let { (header, column) ->
            MyMessageBundle.message("tables.breadcrumb.cell", header, column + 1, ExamplesTables.unescape(element.text.trim()))
        }
        else -> null
    }

    private fun rowName(row: GherkinTableRow): String {
        val table = ExamplesTables.tableOf(row) ?: return ""
        val index = ExamplesTables.rows(table).indexOf(row)
        return if (index == 0) MyMessageBundle.message("tables.breadcrumb.header") else MyMessageBundle.message("tables.breadcrumb.row", index)
    }

    /** The header name and index of [cell]'s column. */
    private fun cellColumn(cell: GherkinTableCell): Pair<String, Int>? {
        val row = cell.parent as? GherkinTableRow ?: return null
        val table = ExamplesTables.tableOf(row) ?: return null
        val offset = cell.textRange.startOffset
        val column = ExamplesTables.cellSpans(row).indexOfFirst { offset in it.startOffset..it.endOffset }.takeIf { it >= 0 } ?: return null
        val header = ExamplesTables.headers(table).getOrNull(column) ?: MyMessageBundle.message("tables.breadcrumb.column", column + 1)
        return header to column
    }
}
