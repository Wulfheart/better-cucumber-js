package com.wulfindustries.plugins.tables

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

/**
 * Hides errors and warnings in the Scenario tab's text ([ExpandedScenarioPanel.isScenarioFile]) that only come from it
 * being a fragment: an outline without Examples, flipped data tables as free text after a step ("Unexpected element"),
 * Backgrounds of a feature and a Rule in a row. Syntax colors stay, and so do undefined steps, unless the tab shows an
 * outline with its `<placeholders>`: those steps only match once an Examples row fills them in.
 */
class ExpandedScenarioHighlightFilter : HighlightInfoFilter {
    override fun accept(info: HighlightInfo, file: PsiFile?): Boolean =
        file == null || !ExpandedScenarioPanel.isScenarioFile(file) || info.severity < HighlightSeverity.WEAK_WARNING ||
            info.inspectionToolId == UNDEFINED_STEP && !ExpandedScenarioPanel.showsPlaceholders(file)

    private companion object {
        const val UNDEFINED_STEP = "CucumberUndefinedStep"
    }
}
