package com.wulfindustries.plugins.tables

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/** Colors every column of an Examples table differently, so wrapped rows can still be read column by column. */
class RainbowColumnsAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val row = element as? GherkinTableRow ?: return
        if (!CucumberJsTableSettings.getInstance().state.rainbowColumns) return
        if (ExamplesTables.tableOf(row) == null) return
        ExamplesTables.cellContentRanges(row).forEachIndexed { column, range ->
            if (!range.isEmpty) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range)
                    .textAttributes(TableColors.column(column))
                    .create()
            }
        }
    }
}
