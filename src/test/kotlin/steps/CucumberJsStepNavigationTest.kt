package com.wulfindustries.plugins.steps

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

class CucumberJsStepNavigationTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/steps"

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("", "")
    }

    fun testStringStep() = assertResolvesTo("Given a calculator", "Given('a calculator'")

    fun testCucumberExpressionParameters() {
        assertResolvesTo("Given the number 42", "Given('the number {int}'")
        assertResolvesTo("When I say \"hello there\"", "When('I say {string}'")
        assertUndefined("Given the number forty-two")
    }

    fun testRegexLiterals() {
        assertResolvesTo("Then the result is 7", "Then(/^the result is (\\d+)$/")
        assertUndefined("Then the result is 7 or so")
        // Unanchored, like RegExp.exec in cucumber-js
        assertResolvesTo("Then something unanchored happens", "Then(/unanchored/")
        assertResolvesTo("Then SHOUTING", "Then(/^shouting$/i")
    }

    fun testTemplateLiteral() = assertResolvesTo("Given a template step", "Given(`a template step`")

    fun testCustomParameterTypes() {
        assertResolvesTo("Given a green ball", "Given('a {color} ball'")
        assertUndefined("Given a purple ball")
        assertResolvesTo("Given a small box", "Given('a {size} box'")
        assertResolvesTo("Given a huge box", "Given('a {size} box'")
    }

    fun testOptionalsAlternativesAndLiteralText() {
        assertResolvesTo("Given I have 1 cucumber in my belly", "Given('I have {int} cucumber(s) in my belly/stomach'")
        assertResolvesTo("Given I have 3 cucumbers in my stomach", "Given('I have {int} cucumber(s) in my belly/stomach'")
        assertResolvesTo("Given the file a.txt", "Given('the file a.txt'")
        assertUndefined("Given the file aXtxt")
    }

    fun testTypeScriptStep() = assertResolvesTo("When I use TypeScript", "When('I use TypeScript'")

    fun testAmbiguousStepResolvesToBoth() {
        assertEquals(
            setOf("Then('an ambiguous step'", "Then(/^an? ambiguous step$/"),
            definitionsOf("Then an ambiguous step").toSet(),
        )
    }

    fun testAndStepAndOutlinePlaceholders() {
        myFixture.configureByText(
            "outline.feature",
            """
            Feature: F
              Scenario Outline: S
                Given a calculator
                And the number <n>

                Examples:
                  | n |
                  | 5 |
            """.trimIndent(),
        )
        assertEquals(listOf("Given('the number {int}'"), definitionsOf(step("the number <n>")))
    }

    fun testIgnoresNodeModules() = assertUndefined("Given a step from node_modules")

    fun testOnlyUndefinedStepsAreReported() {
        myFixture.enableInspections(CucumberStepInspection::class.java)
        myFixture.configureByText(
            "inspected.feature",
            """
            Feature: F
              Scenario: S
                Given a calculator
                When I use TypeScript
                Then nobody implemented this
            """.trimIndent(),
        )
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING).map { it.text }
        assertEquals(listOf("nobody implemented this"), warnings)
    }

    private fun assertResolvesTo(stepLine: String, definitionStart: String) =
        assertEquals(stepLine, listOf(definitionStart), definitionsOf(stepLine))

    private fun assertUndefined(stepLine: String) = assertEquals(stepLine, emptyList<String>(), definitionsOf(stepLine))

    private fun definitionsOf(stepLine: String): List<String> {
        myFixture.configureByText("test.feature", "Feature: F\n  Scenario: S\n    $stepLine\n")
        return definitionsOf(PsiTreeUtil.findChildOfType(myFixture.file, GherkinStep::class.java)!!)
    }

    private fun definitionsOf(step: GherkinStep): List<String> {
        val reference = step.references.filterIsInstance<CucumberStepReference>().single()
        return reference.multiResolve(false).mapNotNull { it.element?.text?.substringBefore(", function") }
    }

    private fun step(name: String): GherkinStep =
        PsiTreeUtil.findChildrenOfType(myFixture.file, GherkinStep::class.java).single { it.name == name }
}
