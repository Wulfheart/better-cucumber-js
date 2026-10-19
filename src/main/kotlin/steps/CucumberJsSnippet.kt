package com.wulfindustries.plugins.steps

import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

/**
 * A step definition stub for a Gherkin step, shaped like cucumber-js's own snippets: `{int}`, `{float}` and `{string}`
 * for numbers and quoted text, parameters named after their types, and a pending body.
 */
class CucumberJsSnippet(
    val keyword: String,
    val expression: String,
    private val parameters: List<Pair<String, String>>,
) {
    /** The names the stub needs from `@cucumber/cucumber`. */
    fun imports(typeScript: Boolean): List<String> =
        listOf(keyword) + if (typeScript && parameters.any { it.second == DATA_TABLE_TYPE }) listOf(DATA_TABLE_TYPE) else emptyList()

    fun render(typeScript: Boolean, semicolons: Boolean): String {
        val end = if (semicolons) ";" else ""
        val params = parameters.joinToString(", ") { (name, type) -> if (typeScript) "$name: $type" else name }
        return "$keyword('${escapeJsString(expression)}', function ($params) {\n" +
            "  $BODY_COMMENT\n" +
            "  return 'pending'$end\n" +
            "})$end\n"
    }

    companion object {
        const val BODY_COMMENT = "// Write code here that turns the phrase above into concrete actions"
        private const val DATA_TABLE_TYPE = "DataTable"
        private val STEP_KEYWORDS = setOf("Given", "When", "Then")

        // Quoted text, an outline placeholder left unsubstituted, a decimal or an integer
        private val PARAMETER = Regex(""""[^"]*"|'[^']*'|<[^>\s]+>|(?<![\w.])-?\d*\.\d+(?![\w.])|(?<![\w.])-?\d+(?![\w.])""")

        fun of(step: GherkinStep): CucumberJsSnippet {
            val parameters = mutableListOf<Pair<String, String>>()
            val counts = mutableMapOf<String, Int>()
            fun parameter(name: String, type: String) {
                val count = counts.merge(name, 1, Int::plus)!!
                parameters += (if (count == 1) name else "$name$count") to type
            }

            val text = step.substitutedName ?: step.name
            val expression = StringBuilder()
            var last = 0
            for (match in PARAMETER.findAll(text)) {
                expression.append(escapeExpression(text.substring(last, match.range.first)))
                val value = match.value
                when {
                    value.startsWith('"') || value.startsWith('\'') -> parameter("string", "string").also { expression.append("{string}") }
                    value.startsWith('<') -> parameter(identifier(value.removeSurrounding("<", ">")), "string").also { expression.append("{}") }
                    '.' in value -> parameter("float", "number").also { expression.append("{float}") }
                    else -> parameter("int", "number").also { expression.append("{int}") }
                }
                last = match.range.last + 1
            }
            expression.append(escapeExpression(text.substring(last)))

            if (step.pystring != null) parameter("docString", "string")
            if (step.table != null) parameter("dataTable", DATA_TABLE_TYPE)
            return CucumberJsSnippet(keyword(step), expression.toString(), parameters)
        }

        /** `Given`, `When` or `Then`; `And`, `But`, `*` and their translations take the keyword of the step before. */
        private fun keyword(step: GherkinStep): String {
            val provider = JsonGherkinKeywordProvider.getKeywordProvider(step)
            val language = (step.containingFile as? GherkinFile)?.localeLanguage ?: "en"
            val steps = step.stepHolder?.steps?.toList() ?: listOf(step)
            for (candidate in steps.take(steps.indexOf(step) + 1).asReversed()) {
                val keyword = candidate.keyword?.text?.trim() ?: continue
                val base = provider.getBaseKeyword(language, keyword)
                if (base in STEP_KEYWORDS) return base!!
            }
            return "Given"
        }

        private fun identifier(name: String): String =
            name.replace(Regex("""\W+(\w?)""")) { it.groupValues[1].uppercase() }
                .replaceFirstChar { it.lowercase() }
                .let { if (it.isEmpty() || it[0].isDigit()) "arg$it" else it }

        /** Escapes what a Cucumber expression would read as a parameter, optional text or alternative. */
        private fun escapeExpression(text: String): String = text.replace(Regex("""([\\(){}/])"""), """\\$1""")

        private fun escapeJsString(text: String): String = text.replace("\\", "\\\\").replace("'", "\\'")
    }
}
