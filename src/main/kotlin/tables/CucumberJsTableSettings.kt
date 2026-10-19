package com.wulfindustries.plugins.tables

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.wulfindustries.plugins.MyMessageBundle

@Service
@State(name = "CucumberJsTables", storages = [Storage("better-cucumber-js.xml")])
class CucumberJsTableSettings : SimplePersistentStateComponent<CucumberJsTableSettings.TablesState>(TablesState()) {
    class TablesState : BaseState() {
        var rainbowColumns by property(true)
        var highlightCurrentColumn by property(true)
    }

    companion object {
        fun getInstance(): CucumberJsTableSettings = service()
    }
}

/** Settings | Languages & Frameworks | Cucumber.js Tables. */
class CucumberJsTableConfigurable : BoundConfigurable(MyMessageBundle.message("tables.settings.name")) {
    private val settings get() = CucumberJsTableSettings.getInstance().state

    override fun createPanel() = panel {
        row {
            checkBox(MyMessageBundle.message("tables.settings.rainbow")).bindSelected(settings::rainbowColumns)
        }
        row {
            checkBox(MyMessageBundle.message("tables.settings.current.column")).bindSelected(settings::highlightCurrentColumn)
        }
    }

    override fun apply() {
        super.apply()
        for (project in ProjectManager.getInstance().openProjects) DaemonCodeAnalyzer.getInstance(project).restart(this)
        CurrentColumnHighlighter.refreshAll()
    }
}
