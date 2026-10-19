package com.wulfindustries.plugins.steps

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class CucumberJsStepDefinitionCreatorTest : BasePlatformTestCase() {
    private val creator = CucumberJsStepDefinitionCreator()

    fun testSnippetParameters() {
        assertEquals(
            "When('I add {int} and {float} to {string} and {string}', function (int, float, string, string2) {\n" +
                "  // Write code here that turns the phrase above into concrete actions\n" +
                "  return 'pending'\n" +
                "})\n",
            snippet("When I add 3 and 1.5 to \"a\" and 'b'").render(typeScript = false, semicolons = false),
        )
    }

    fun testSnippetEscapesExpressionAndJsString() {
        assertEquals(
            "a \\(b\\) \\{c\\} d\\/e it's",
            snippet("Given a (b) {c} d/e it's").expression,
        )
        val rendered = snippet("Given a (b) it's").render(typeScript = false, semicolons = true)
        assertTrue(rendered, rendered.startsWith("Given('a \\\\(b\\\\) it\\'s', function () {"))
        assertTrue(rendered, rendered.endsWith("return 'pending';\n});\n"))
    }

    fun testSnippetKeywordOfAndStepAndTypeScriptTypes() {
        val file = myFixture.configureByText(
            "f.feature",
            "Feature: F\n  Scenario: S\n    When something\n    And a list:\n      | a |\n",
        )
        val snippet = CucumberJsSnippet.of(steps(file).last())
        assertEquals("When", snippet.keyword)
        assertEquals(listOf("When", "DataTable"), snippet.imports(typeScript = true))
        assertTrue(snippet.render(typeScript = true, semicolons = false).contains("function (dataTable: DataTable)"))
    }

    fun testSnippetKeywordInOtherLanguage() {
        val file = myFixture.configureByText(
            "f.feature",
            "# language: de\nFunktionalität: F\n  Szenario: S\n    Wenn etwas\n    Und noch etwas\n",
        )
        assertEquals("When", CucumberJsSnippet.of(steps(file).last()).keyword)
    }

    fun testAppendsToExistingFileAndImportsKeyword() {
        val steps = myFixture.addFileToProject(
            "features/step_definitions/steps.js",
            "const { Given } = require('@cucumber/cucumber')\n\nGiven('a calculator', function () {})\n",
        )
        val feature = myFixture.configureByText("f.feature", "Feature: F\n  Scenario: S\n    Then the result is 5\n")

        WriteCommandAction.runWriteCommandAction(project) {
            assertTrue(creator.createStepDefinition(steps(feature).single(), steps, false))
        }

        // Reformatted with the project's code style, which indents by 4 by default
        assertEquals(
            "const { Given, Then } = require('@cucumber/cucumber')\n\n" +
                "Given('a calculator', function () {})\n\n" +
                "Then('the result is {int}', function (int) {\n" +
                "    // Write code here that turns the phrase above into concrete actions\n" +
                "    return 'pending'\n" +
                "})\n",
            steps.text,
        )
    }

    fun testCreatesNewFile() {
        val dir = myFixture.addFileToProject("features/x.feature", "Feature: F\n").containingDirectory
        val created = WriteCommandAction.writeCommandAction(project).compute<PsiFile, RuntimeException> {
            creator.createStepDefinitionContainer(dir, "new_steps")
        }
        assertEquals("new_steps.js", created.name)
        assertEquals("const { Given, When, Then } = require('@cucumber/cucumber')\n", created.text)
    }

    fun testCreatesEsModuleFile() {
        myFixture.addFileToProject("package.json", """{ "type": "module" }""")
        val dir = myFixture.addFileToProject("features/x.feature", "Feature: F\n").containingDirectory
        val created = WriteCommandAction.writeCommandAction(project).compute<PsiFile, RuntimeException> {
            creator.createStepDefinitionContainer(dir, "new_steps")
        }
        assertEquals("import { Given, When, Then } from '@cucumber/cucumber'\n", created.text)
    }

    private fun snippet(stepLine: String): CucumberJsSnippet {
        val file = myFixture.configureByText("f.feature", "Feature: F\n  Scenario: S\n    $stepLine\n")
        return CucumberJsSnippet.of(steps(file).single())
    }

    private fun steps(file: PsiFile): List<GherkinStep> =
        PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).toList()
}
