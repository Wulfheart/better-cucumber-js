package com.wulfindustries.plugins.run

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class CucumberJsRunLineMarkerTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/features"

    fun testMarkersOnFeatureScenariosAndExampleRowsOnly() {
        val file = myFixture.configureByFile("calculator.feature")
        assertInstanceOf(file, GherkinFile::class.java)
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        val contributor = CucumberJsRunLineMarkerContributor()

        val markers = PsiTreeUtil.collectElements(file) { it.firstChild == null }
            .filter { contributor.getInfo(it) != null }
            .map { document.getLineNumber(it.textRange.startOffset) + 1 to it.text }

        // Feature, @smoke scenario keyword (not its tag line), outline, two data rows (not the header), Example synonym.
        // Background (line 3) and steps get nothing.
        assertEquals(
            listOf(1 to "Feature", 7 to "Scenario", 11 to "Scenario Outline", 16 to "|", 17 to "|", 19 to "Example"),
            markers,
        )
    }

    fun testMarkerHasRunAndDebugActions() {
        val file = myFixture.configureByFile("calculator.feature")
        val featureKeyword = PsiTreeUtil.collectElements(file) { it.firstChild == null && it.text == "Feature" }.single()
        val info = CucumberJsRunLineMarkerContributor().getInfo(featureKeyword)!!
        assertTrue(info.actions.isNotEmpty())
        assertEquals("Run Feature 'Calculator'", info.tooltipProvider!!.apply(featureKeyword))
    }
}
