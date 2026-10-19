package com.wulfindustries.plugins.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * Run/debug gutter icons on the leaf tokens that start a runnable block: the `Feature:` keyword,
 * scenario / outline keywords, and the leading pipe of each Examples data row.
 */
class CucumberJsRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        if (element.firstChild != null) return null
        val target = targetFor(element) ?: return null

        val fallback = if (target.isFeature) AllIcons.RunConfigurations.TestState.Run_run else AllIcons.RunConfigurations.TestState.Run
        val icon = getTestStateIcon(target.url, element.project, target.isFeature) ?: fallback
        val tooltip = when {
            target.isFeature -> MyMessageBundle.message("run.line.marker.run.feature", target.name)
            element.elementType == GherkinTokenTypes.PIPE -> MyMessageBundle.message("run.line.marker.run.example")
            else -> MyMessageBundle.message("run.line.marker.run.scenario", target.name)
        }
        return Info(icon, ExecutorAction.getActions(), { tooltip })
    }

    private fun targetFor(leaf: PsiElement): CucumberJsTarget? {
        val parent = leaf.parent ?: return null
        val isRunnableLeaf = when (leaf.elementType) {
            GherkinTokenTypes.FEATURE_KEYWORD -> parent is GherkinFeature
            in GherkinTokenTypes.SCENARIOS_KEYWORDS -> CucumberJsTargets.runnableScenario(parent) === parent
            GherkinTokenTypes.PIPE -> parent is GherkinTableRow && parent.firstChild === leaf &&
                CucumberJsTargets.exampleRow(parent) === parent
            else -> false
        }
        return if (isRunnableLeaf) CucumberJsTargets.find(parent) else null
    }
}
