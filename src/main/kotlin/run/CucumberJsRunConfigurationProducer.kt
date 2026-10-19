package com.wulfindustries.plugins.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class CucumberJsRunConfigurationProducer : LazyRunConfigurationProducer<CucumberJsRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = CucumberJsConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean = false // Stream C

    override fun isConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
    ): Boolean = false // Stream C
}
