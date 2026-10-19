package com.wulfindustries.plugins.steps

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.MapParameterTypeManager

/**
 * The step definitions and custom parameter types in a project's JS/TS support code: calls of `Given`, `When`, `Then`,
 * `defineStep` and `defineParameterType` with literal arguments, in project files outside `node_modules`.
 */
class CucumberJsSupportCode private constructor(
    val steps: List<CucumberJsStepDefinition>,
    val parameterTypes: MapParameterTypeManager,
) {
    val files: List<PsiFile>
        get() = steps.mapNotNull { it.element?.containingFile }.distinct()

    companion object {
        private val STEP_FUNCTIONS = setOf("Given", "When", "Then", "defineStep")
        private const val DEFINE_PARAMETER_TYPE = "defineParameterType"
        private val KEY = Key.create<CachedValue<CucumberJsSupportCode>>("CucumberJsSupportCode")

        /** Rescanned after every PSI change; empty while indexing, without caching that. */
        fun of(project: Project): CucumberJsSupportCode {
            if (DumbService.isDumb(project)) return CucumberJsSupportCode(emptyList(), MapParameterTypeManager.DEFAULT)
            return CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
                CachedValueProvider.Result.create(scan(project), PsiModificationTracker.MODIFICATION_COUNT)
            }, false)
        }

        private fun scan(project: Project): CucumberJsSupportCode {
            val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
                GlobalSearchScope.projectScope(project),
                JavaScriptFileType,
                TypeScriptFileType,
            )
            // The word index holds identifiers, not the text of the '@cucumber/cucumber' import
            val files = mutableSetOf<JSFile>()
            for (word in STEP_FUNCTIONS + DEFINE_PARAMETER_TYPE) {
                PsiSearchHelper.getInstance(project).processAllFilesWithWord(word, scope, { file ->
                    if (file is JSFile && "/node_modules/" !in file.virtualFile.path) files += file
                    true
                }, true)
            }
            val calls = files.sortedBy { it.virtualFile.path }
                .flatMap { PsiTreeUtil.findChildrenOfType(it, JSCallExpression::class.java) }

            val regexps = mutableMapOf<String, String>()
            val declarations = mutableMapOf<String, SmartPsiElementPointer<PsiElement>>()
            for (call in calls.filter { calleeName(it) == DEFINE_PARAMETER_TYPE }) {
                val options = call.argumentList?.arguments?.firstOrNull() as? JSObjectLiteralExpression ?: continue
                val name = (options.findProperty("name")?.value as? JSLiteralExpression)?.stringValue ?: continue
                val regexp = parameterTypeRegexp(options.findProperty("regexp")?.value) ?: continue
                regexps[name] = regexp
                declarations[name] = SmartPointerManager.createPointer(call)
            }
            val parameterTypes = MapParameterTypeManager(CucumberUtil.STANDARD_PARAMETER_TYPES + regexps, declarations)

            val steps = calls.filter { calleeName(it) in STEP_FUNCTIONS && stepPattern(it) != null }
                .map { CucumberJsStepDefinition(it, parameterTypes) }
            return CucumberJsSupportCode(steps, parameterTypes)
        }

        /** `Given`, also when qualified as in `cucumber.Given`. */
        private fun calleeName(call: JSCallExpression): String? =
            (call.methodExpression as? JSReferenceExpression)?.referenceName

        /** The pattern a step definition call registers, or `null` when it isn't a literal. */
        fun stepPattern(call: JSCallExpression): StepPattern? =
            when (val argument = call.argumentList?.arguments?.firstOrNull()) {
                is JSStringTemplateExpression ->
                    if (argument.arguments.isEmpty()) StepPattern(argument.text.removeSurrounding("`"), isRegex = false)
                    else null
                is JSLiteralExpression -> when {
                    argument.isRegExpLiteral -> regexLiteral(argument)
                    argument.isQuotedLiteral -> argument.stringValue?.let { StepPattern(it, isRegex = false) }
                    else -> null
                }
                else -> null
            }

        fun callback(call: JSCallExpression): JSFunction? =
            call.argumentList?.arguments?.lastOrNull { it is JSFunction } as? JSFunction

        private fun regexLiteral(literal: JSLiteralExpression): StepPattern? {
            val text = literal.text
            val end = text.lastIndexOf('/').takeIf { it > 0 } ?: return null
            return StepPattern(text.substring(1, end), isRegex = true, ignoreCase = 'i' in text.substring(end + 1))
        }

        /** `regexp` is a regex, a string holding one, or an array of these, which cucumber-js treats as alternatives. */
        private fun parameterTypeRegexp(value: JSExpression?): String? = when (value) {
            is JSArrayLiteralExpression ->
                value.expressions.map { parameterTypeRegexp(it) ?: return null }.joinToString("|") { "(?:$it)" }
            is JSStringTemplateExpression -> null
            is JSLiteralExpression -> when {
                value.isRegExpLiteral -> regexLiteral(value)?.source
                value.isQuotedLiteral -> value.stringValue
                else -> null
            }
            else -> null
        }
    }
}

/** A string is always a Cucumber expression in cucumber-js; only regex literals are regular expressions. */
data class StepPattern(val source: String, val isRegex: Boolean, val ignoreCase: Boolean = false)
