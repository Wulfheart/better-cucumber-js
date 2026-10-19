package com.wulfindustries.plugins.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement

class CucumberJsRunConfigurationProducer : LazyRunConfigurationProducer<CucumberJsRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = CucumberJsConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val target = context.psiLocation?.let(CucumberJsTargets::find) ?: return false
        val workingDir = CucumberJsTargets.findWorkingDir(target.file) ?: return false
        val cucumberPackage = CucumberJsTargets.cucumberPackageDir(workingDir) ?: return false

        configuration.featurePath = target.file.path
        configuration.line = target.line
        configuration.scenarioPath = if (target.isFeature) null else ScenarioPath.of(target.element)
        configuration.workingDir = workingDir.path
        configuration.cucumberPackagePath = cucumberPackage.path
        configuration.name = target.name
        sourceElement.set(target.element)
        return true
    }

    override fun isConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val target = context.psiLocation?.let(CucumberJsTargets::find) ?: return false
        if (!FileUtil.pathsEqual(configuration.featurePath, target.file.path)) return false
        // By name where possible: the line is stale once lines above the scenario were added or removed
        val path = configuration.scenarioPath
        return if (path != null && !target.isFeature) path == ScenarioPath.of(target.element) else configuration.line == target.line
    }
}
