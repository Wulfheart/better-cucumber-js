package com.wulfindustries.plugins.steps

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import org.jetbrains.plugins.cucumber.MapParameterTypeManager
import org.jetbrains.plugins.cucumber.ParameterTypeManager
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.steps.AbstractCucumberExtension
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * Connects Gherkin steps to cucumber-js step definitions: navigation, completion, the undefined step inspection and
 * its "Create step definition" quick fixes. cucumber-js loads support code per run, not per module, so every module
 * sees the whole project's step definitions.
 */
class CucumberJsExtension : AbstractCucumberExtension() {
    override fun isStepLikeFile(child: PsiElement): Boolean =
        child is JSFile && child in CucumberJsSupportCode.of(child.project).files

    override fun isWritableStepLikeFile(child: PsiElement): Boolean {
        val file = child as? JSFile ?: return false
        val virtualFile = file.virtualFile ?: return false
        return isStepLikeFile(file) && virtualFile.isWritable &&
            ProjectFileIndex.getInstance(file.project).isInContent(virtualFile)
    }

    override fun getStepFileType(): BDDFrameworkType = BDDFrameworkType(JavaScriptFileType, "Cucumber.js")

    override fun getStepDefinitionCreator(): StepDefinitionCreator = CucumberJsStepDefinitionCreator()

    override fun loadStepsFor(module: Module): List<AbstractStepDefinition> =
        CucumberJsSupportCode.of(module.project).steps

    override fun getStepDefinitionContainers(featureFile: GherkinFile): Collection<PsiFile> =
        CucumberJsSupportCode.of(featureFile.project).files

    /** cucumber-js has always parsed Gherkin 6 (`Rule`, `Example`). */
    override fun isGherkin6Supported(module: Module): Boolean = true

    override fun getParameterTypeManager(definition: AbstractStepDefinition): ParameterTypeManager =
        (definition as? CucumberJsStepDefinition)?.parameterTypes ?: MapParameterTypeManager.DEFAULT
}
