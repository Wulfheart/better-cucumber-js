package com.wulfindustries.plugins.tables

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.wulfindustries.plugins.run.CucumberJsRunConfiguration
import com.wulfindustries.plugins.run.ScenarioPath
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

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
            # Examples: Fruit › Example #2
            Feature: Shop

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
            "# language: de\n# Beispiele › Example #1\nFunktionalität: Laden\n\n  Szenariogrundriss: Kaufen\n    Angenommen ich kaufe Apfel\n",
            expanded.text,
        )
    }

    fun testHeaderRowHasNoExpansion() {
        val text = "Feature: F\n  Scenario Outline: S\n    Given <a>\n\n    Examples:\n      | a |\n      | 1 |\n"
        assertNull(ExpandedScenario.of(rowAt(text, "| a |")))
    }

    private val wideTables = """
        Feature: Shop
          Scenario Outline: Users
            Given the users:
              | name   | email                 | role  |
              | <name> | <name>@example.com    | admin |
              | bob    | bob\|x@example.com    |       |
            And the settings:
              | theme | <theme> |
              | font  | mono    |
            Then <name> is shown

            Examples:
              | name  | theme |
              | alice | dark  |
    """.trimIndent()

    fun testWideDataTablesAreFlipped() {
        val expanded = ExpandedScenario.of(rowAt(wideTables, "| alice"), maxWidth = 15)!!

        assertEquals(
            """
            # Examples › Example #1
            Feature: Shop

              Scenario Outline: Users
                Given the users:
                  # Row 1
                  name:   alice
                  email:  alice@example.com
                  role:   admin
                  # Row 2
                  name:   bob
                  email:  bob|x@example.com
                  role:
                And the settings:
                  theme:  dark
                  font:   mono
                Then alice is shown
            """.trimIndent() + "\n",
            expanded.text,
        )
        assertEquals(
            listOf("alice", "alice", "dark", "alice"),
            expanded.substituted.map { it.substring(expanded.text) },
        )
        assertEquals(listOf("# Row 1", "# Row 2"), expanded.rowStarts.map { expanded.text.substring(it).lineSequence().first() })
        assertEquals(
            listOf("name", "email", "role", "name", "email", "role", "theme", "font"),
            expanded.keys.map { it.substring(expanded.text) },
        )
        // The second row has no role, so no value range
        assertEquals(
            listOf("alice", "alice@example.com", "admin", "bob", "bob|x@example.com", "dark", "mono"),
            expanded.values.map { it.substring(expanded.text) },
        )
    }

    fun testTablesThatFitStayAligned() {
        val expanded = ExpandedScenario.of(rowAt(wideTables, "| alice"), maxWidth = 50)!!
        assertEmpty(expanded.rowStarts)
        assertEmpty(expanded.keys)

        assertTrue(expanded.text, "      | alice | alice@example.com  | admin |\n" in expanded.text)
        assertTrue(expanded.text, "      | theme | dark |\n" in expanded.text)
    }

    fun testTableWithKeywordHeaderIsNotFlipped() {
        val text = """
            Feature: F
              Scenario Outline: S
                Given the steps:
                  | Given | Examples | And then |
                  | <a>   | x        | y        |

                Examples:
                  | a |
                  | 1 |
        """.trimIndent()

        val expanded = ExpandedScenario.of(rowAt(text, "| 1 |"), maxWidth = 10)!!

        assertTrue(expanded.text, "| Given | Examples | And then |" in expanded.text)
    }

    fun testStepsOfTheScenarioFileResolve() {
        myFixture.addFileToProject(
            "steps.js",
            "const { Given, Then } = require('@cucumber/cucumber')\n" +
                "Given('the users:', function (table) {})\nThen('{word} is shown', function (name) {})\n",
        )
        val psi = scenarioFileFor(rowAt(wideTables, "| alice"))

        val definitions = PsiTreeUtil.findChildrenOfType(psi, GherkinStep::class.java).associate { step ->
            step.name to step.references.filterIsInstance<CucumberStepReference>().single()
                .multiResolve(false).map { it.element!!.text.substringBefore(", function") }
        }
        assertEquals(
            mapOf(
                "the users:" to listOf("Given('the users:'"),
                "the settings:" to emptyList(),
                "alice is shown" to listOf("Then('{word} is shown'"),
            ),
            definitions,
        )
    }

    fun testScenarioFileHidesErrorsButKeepsColors() {
        val row = rowAt(wideTables, "| alice")
        val filter = ExpandedScenarioHighlightFilter()
        fun info(severity: HighlightSeverity) =
            HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).severity(severity).range(0, 1).createUnconditionally()

        val scenario = scenarioFileFor(row)
        assertTrue(ExpandedScenarioPanel.isScenarioFile(scenario.viewProvider.virtualFile))
        assertFalse(ExpandedScenarioPanel.isScenarioFile(row.containingFile.virtualFile))
        assertFalse(filter.accept(info(HighlightSeverity.ERROR), scenario))
        assertFalse(filter.accept(info(HighlightSeverity.WARNING), scenario))
        assertTrue(filter.accept(info(HighlightSeverity.INFORMATION), scenario))
        assertTrue(filter.accept(info(HighlightSeverity.ERROR), row.containingFile))
    }

    /** The Scenario tab's in-memory file for [row], linked to [row]'s file. */
    private fun scenarioFileFor(row: GherkinTableRow): PsiFile {
        val file = ExpandedScenarioPanel.scenarioFile()
        val psi = PsiManager.getInstance(project).findFile(file)!!
        ExpandedScenarioPanel.linkTo(psi, row.containingFile)
        WriteAction.runAndWait<RuntimeException> {
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            document.setText(ExpandedScenario.of(row, maxWidth = 15)!!.text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        return psi
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
