package com.wulfindustries.plugins.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

class CucumberJsConfigurationType : ConfigurationTypeBase(
    ID,
    MyMessageBundle.message("run.configuration.type.name"),
    MyMessageBundle.message("run.configuration.type.description"),
    GherkinFileType.INSTANCE.icon,
) {
    init {
        addFactory(CucumberJsConfigurationFactory(this))
    }

    val factory: ConfigurationFactory
        get() = configurationFactories.single()

    companion object {
        const val ID = "CucumberJsConfigurationType"

        fun getInstance(): CucumberJsConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(CucumberJsConfigurationType::class.java)
    }
}

class CucumberJsConfigurationFactory(type: CucumberJsConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Cucumber.js"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CucumberJsRunConfiguration(project, this, MyMessageBundle.message("run.configuration.default.name"))
}
