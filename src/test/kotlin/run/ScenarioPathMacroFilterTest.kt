package com.wulfindustries.plugins.run

import com.intellij.application.options.PathMacrosCollector
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class ScenarioPathMacroFilterTest : BasePlatformTestCase() {
    private val path = ScenarioPath(
        featurePath = "/project/a.feature",
        ruleName = "Rule \$ignore\$",
        scenarioName = "Outline \$ignore\$",
        occurrence = 0,
        row = ScenarioPath.Row(examplesIndex = 0, rowIndex = 0, cells = listOf("1", "\$ignore\$")),
    )

    private fun configuration(type: String = CucumberJsConfigurationType.ID): Element =
        Element("configuration").setAttribute("type", type).also(path::writeTo)

    fun testScenarioPathIsNotScannedForPathVariables() {
        assertDoesntContain(PathMacrosCollector.getMacroNames(configuration()), "ignore")
    }

    fun testOtherFieldsAreStillScanned() {
        val element = configuration()
        JDOMExternalizerUtil.writeField(element, "extra-args", "\$ignore\$")
        assertContainsElements(PathMacrosCollector.getMacroNames(element), "ignore")
    }

    fun testOtherConfigurationTypesAreStillScanned() {
        assertContainsElements(PathMacrosCollector.getMacroNames(configuration(type = "Other")), "ignore")
    }
}
