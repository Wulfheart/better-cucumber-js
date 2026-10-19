package com.wulfindustries.plugins.steps

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.ParameterTypeManager
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** A `Given/When/Then(pattern, callback)` call. Gherkin steps resolve to the call itself. */
class CucumberJsStepDefinition(
    call: JSCallExpression,
    val parameterTypes: ParameterTypeManager,
) : AbstractStepDefinition(call) {
    private val stepPattern: StepPattern?
        get() = (element as? JSCallExpression)?.let(CucumberJsSupportCode::stepPattern)

    override fun getVariableNames(): List<String> =
        (element as? JSCallExpression)?.let(CucumberJsSupportCode::callback)
            ?.parameterVariables?.mapNotNull { it.name }.orEmpty()

    /** The pattern as written: the expression, or the source of a regex literal. */
    override fun getCucumberRegexFromElement(element: PsiElement?): String? =
        (element as? JSCallExpression)?.let(CucumberJsSupportCode::stepPattern)?.source

    /** The Java regex Gherkin matches step names against, with [Pattern.find] like cucumber-js's `RegExp.exec`. */
    override fun getCucumberRegex(): String? {
        val pattern = stepPattern ?: return null
        val regex = try {
            if (pattern.isRegex) pattern.source
            else CucumberUtil.buildRegexpFromCucumberExpression(pattern.source, parameterTypes)
        } catch (_: RuntimeException) {
            return NEVER_MATCHES
        }
        return try {
            Pattern.compile(regex)
            regex
        } catch (_: PatternSyntaxException) {
            NEVER_MATCHES
        }
    }

    override fun isCaseSensitive(): Boolean = stepPattern?.ignoreCase != true

    private companion object {
        const val NEVER_MATCHES = "(?!)"
    }
}
