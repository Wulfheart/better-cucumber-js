package com.wulfindustries.plugins.run

import com.intellij.openapi.application.PathMacroFilter
import org.jdom.Element

/**
 * Keeps the IDE from treating `$name$` in a stored [ScenarioPath] as a path variable. Scenario names and Examples
 * cells are copied from feature files, so a cell like `$ignore$` would otherwise be reported as an undefined path
 * variable when the project opens. The IDE still expands a path variable that does exist when it loads the
 * configuration; [ScenarioPath.resolve] then finds the row by its index.
 */
class ScenarioPathMacroFilter : PathMacroFilter() {
    override fun skipPathMacros(element: Element): Boolean {
        if (element.name != ScenarioPath.SCENARIO) return false
        val configuration = element.parentElement ?: return false
        return configuration.name == "configuration" && configuration.getAttributeValue("type") == CucumberJsConfigurationType.ID
    }
}
