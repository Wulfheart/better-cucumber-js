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
        return FileUtil.pathsEqual(configuration.featurePath, target.file.path) && configuration.line == target.line
    }
}
