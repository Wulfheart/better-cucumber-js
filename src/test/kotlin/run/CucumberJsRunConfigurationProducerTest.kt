package com.wulfindustries.plugins.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CucumberJsRunConfigurationProducerTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/features"

    private fun configureProject(withCucumber: Boolean = true) {
        if (withCucumber) myFixture.addFileToProject("app/node_modules/@cucumber/cucumber/package.json", "{}")
        myFixture.copyFileToProject("calculator.feature", "app/features/calculator.feature")
        myFixture.configureFromTempProjectFile("app/features/calculator.feature")
    }

    private fun elementAt(text: String): PsiElement {
        val offset = myFixture.file.text.indexOf(text)
        assertTrue("'$text' not found", offset >= 0)
        return myFixture.file.findElementAt(offset)!!
    }

    private fun createFrom(element: PsiElement): CucumberJsRunConfiguration? {
        val context = ConfigurationContext.createEmptyContextForLocation(PsiLocation(element))
        return CucumberJsRunConfigurationProducer().createConfigurationFromContext(context)
            ?.configuration as CucumberJsRunConfiguration?
    }

    private fun assertTarget(element: PsiElement, line: Int?, name: String) {
        val config = createFrom(element)!!
        val featureFile = myFixture.file.virtualFile
        assertEquals(featureFile.path, config.featurePath)
        assertEquals(line, config.line)
        assertEquals(featureFile.parent.parent.path, config.workingDir)
        assertEquals("${featureFile.parent.parent.path}/node_modules/@cucumber/cucumber", config.cucumberPackagePath)
        assertEquals(name, config.name)
        assertTrue(CucumberJsRunConfigurationProducer().isConfigurationFromContext(
            config, ConfigurationContext.createEmptyContextForLocation(PsiLocation(element))))
    }

    fun testFeature() {
        configureProject()
        assertTarget(elementAt("Feature:"), null, "Calculator")
    }

    fun testBackgroundRunsWholeFeature() {
        configureProject()
        assertTarget(elementAt("Given a calculator"), null, "Calculator")
    }

    fun testScenarioFromStepUsesKeywordLine() {
        configureProject()
        assertTarget(elementAt("Then the result is 2"), 7, "Adding two numbers")
    }

    fun testOutline() {
        configureProject()
        assertTarget(elementAt("Scenario Outline:"), 11, "Adding many numbers")
    }

    fun testExampleRow() {
        configureProject()
        assertTarget(elementAt("| 2 |"), 17, "Adding many numbers")
    }

    fun testHeaderRowRunsOutline() {
        configureProject()
        assertTarget(elementAt("| a |"), 11, "Adding many numbers")
    }

    fun testOtherLineIsNotSameConfiguration() {
        configureProject()
        val config = createFrom(elementAt("Scenario Outline:"))!!
        val other = ConfigurationContext.createEmptyContextForLocation(PsiLocation(elementAt("Feature:")))
        assertFalse(CucumberJsRunConfigurationProducer().isConfigurationFromContext(config, other))
    }

    fun testNoCucumberPackage() {
        configureProject(withCucumber = false)
        assertNull(createFrom(elementAt("Scenario Outline:")))
    }
}
