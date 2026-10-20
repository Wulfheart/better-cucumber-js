package com.wulfindustries.plugins.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
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

    private fun edit(change: Document.() -> Unit) {
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.change() }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun isFrom(config: CucumberJsRunConfiguration, element: PsiElement): Boolean =
        CucumberJsRunConfigurationProducer().isConfigurationFromContext(
            config, ConfigurationContext.createEmptyContextForLocation(PsiLocation(element)))

    fun testScenarioKeepsItsPathButFeatureDoesNot() {
        configureProject()
        assertEquals("Adding two numbers", createFrom(elementAt("Then the result is 2"))!!.scenarioPath?.scenarioName)
        assertNull(createFrom(elementAt("Feature:"))!!.scenarioPath)
    }

    fun testMovedScenarioIsSameConfiguration() {
        configureProject()
        val config = createFrom(elementAt("Scenario Outline:"))!!
        edit { insertString(0, "# comment\n") }
        assertTrue(isFrom(config, elementAt("Scenario Outline:")))
        assertFalse(isFrom(config, elementAt("| 2 |")))
    }

    fun testOtherScenarioAtOldLineIsNotSameConfiguration() {
        configureProject()
        val config = createFrom(elementAt("Scenario: Adding two numbers"))!!
        // Removes "Adding two numbers" (lines 6-9), which moves the outline up to its line 7
        edit { deleteString(getLineStartOffset(5), getLineStartOffset(9)) }
        assertFalse(isFrom(config, elementAt("Scenario Outline:")))
    }

    private fun directory(path: String): PsiDirectory =
        PsiManager.getInstance(project).findDirectory(myFixture.findFileInTempDir(path))!!

    private fun assertDirectoryTarget(path: String, name: String) {
        val dir = directory(path)
        val config = createFrom(dir)!!
        val appPath = myFixture.findFileInTempDir("app").path
        assertEquals(dir.virtualFile.path, config.featurePath)
        assertNull(config.line)
        assertNull(config.scenarioPath)
        assertEquals(appPath, config.workingDir)
        assertEquals("$appPath/node_modules/@cucumber/cucumber", config.cucumberPackagePath)
        assertEquals(name, config.name)
        assertTrue(isFrom(config, dir))
    }

    fun testFeatureDirectory() {
        configureProject()
        assertDirectoryTarget("app/features", "features")
    }

    fun testDirectoryAboveFeatureDirectory() {
        configureProject()
        assertDirectoryTarget("app", "app")
    }

    fun testDirectoryWithoutFeatures() {
        configureProject()
        myFixture.addFileToProject("app/steps/steps.js", "")
        assertNull(createFrom(directory("app/steps")))
    }

    fun testFeatureFileIsNotSameAsItsDirectory() {
        configureProject()
        val dirConfig = createFrom(directory("app/features"))!!
        val featureConfig = createFrom(elementAt("Feature:"))!!
        assertFalse(isFrom(dirConfig, elementAt("Feature:")))
        assertFalse(isFrom(featureConfig, directory("app/features")))
    }

    fun testNoCucumberPackage() {
        configureProject(withCucumber = false)
        assertNull(createFrom(elementAt("Scenario Outline:")))
    }
}
