package com.wulfindustries.plugins.tables

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.wulfindustries.plugins.run.CucumberJsRunConfiguration
import com.wulfindustries.plugins.run.ScenarioPath
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

class ExpandedScenarioTest : BasePlatformTestCase() {
    private fun rowAt(text: String, marker: String): GherkinTableRow {
        val file = myFixture.configureByText("a.feature", text)
        return ExamplesTables.positionAt(file, text.indexOf(marker))!!.row
    }

    fun testExpandsStepsTablesDocStringsAndName() {
        val text = """
            Feature: Shop
              Background:
                Given a shop

              Rule: Orders
                Background:
                  Given a customer

                @tagged
                Scenario Outline: Ordering <item>
                  Given I order <amount> <item> and <unknown>
                  # a comment
                  And the basket:
                    | item   | amount   |
                    | <item> | <amount> |
                  Then the receipt says:
                    ""${'"'}
                    <amount> x <item>
                    ""${'"'}

                  Examples: Fruit
                    | item          | amount |
                    | apple         | 3      |
                    | a \| b        | 12     |
        """.trimIndent()

        val expanded = ExpandedScenario.of(rowAt(text, "a \\| b"))!!

        assertEquals(
            """
            # Examples: Fruit, Example #2
            Background:
              Given a shop

            Background:
              Given a customer

            Scenario Outline: Ordering a | b
              Given I order 12 a | b and <unknown>
              And the basket:
                | item   | amount |
                | a \| b | 12     |
              Then the receipt says:
                ""${'"'}
                12 x a | b
                ""${'"'}
            """.trimIndent() + "\n",
            expanded.text,
        )
        assertEquals(
            listOf("a | b", "12", "a | b", "a \\| b", "12", "12", "a | b"),
            expanded.substituted.map { it.substring(expanded.text) },
        )
    }

    fun testLanguageCommentIsCarriedOver() {
        val text = """
            # language: de
            Funktionalität: Laden
              Szenariogrundriss: Kaufen
                Angenommen ich kaufe <ding>

                Beispiele:
                  | ding  |
                  | Apfel |
        """.trimIndent()

        val expanded = ExpandedScenario.of(rowAt(text, "Apfel"))!!

        assertEquals(
            "# language: de\n# Beispiele:, Example #1\nSzenariogrundriss: Kaufen\n  Angenommen ich kaufe Apfel\n",
            expanded.text,
        )
    }

    fun testHeaderRowHasNoExpansion() {
        val text = "Feature: F\n  Scenario Outline: S\n    Given <a>\n\n    Examples:\n      | a |\n      | 1 |\n"
        assertNull(ExpandedScenario.of(rowAt(text, "| a |")))
    }

    fun testRunnerCreatesTheRowsRunConfiguration() {
        myFixture.addFileToProject("app/node_modules/@cucumber/cucumber/package.json", "{}")
        myFixture.copyFileToProject("calculator.feature", "app/features/calculator.feature")
        val file = myFixture.configureFromTempProjectFile("app/features/calculator.feature")
        val row = ExamplesTables.positionAt(file, file.text.indexOf("| 2 |"))!!.row

        val settings = ExampleRowRunner.settingsFor(row)!!

        val configuration = settings.configuration as CucumberJsRunConfiguration
        assertEquals(17, configuration.line)
        assertEquals(ScenarioPath.of(row), configuration.scenarioPath)
    }

    override fun getTestDataPath(): String = "src/test/testData/features"
}
