package com.wulfindustries.plugins.run

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.wulfindustries.plugins.MyMessageBundle
import javax.swing.JComponent

class CucumberJsSettingsEditor(private val project: Project) : SettingsEditor<CucumberJsRunConfiguration>() {
    private val interpreterField = NodeJsInterpreterField(project, false)
    private val packageField = directoryField()
    private val workingDirField = directoryField()
    private val featureField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
    }
    private val lineField = JBTextField()
    private val tagsField = JBTextField()
    private val profileField = JBTextField()
    private val extraArgsField = JBTextField()
    private val envField = EnvironmentVariablesTextFieldWithBrowseButton()

    private fun directoryField() = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }

    override fun createEditor(): JComponent = panel {
        row(MyMessageBundle.message("run.configuration.editor.interpreter")) { cell(interpreterField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.package")) { cell(packageField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.working.dir")) { cell(workingDirField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.feature.path")) { cell(featureField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.line")) { cell(lineField).columns(6) }
        row(MyMessageBundle.message("run.configuration.editor.tags")) { cell(tagsField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.profile")) { cell(profileField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.extra.args")) { cell(extraArgsField).align(AlignX.FILL) }
        row(MyMessageBundle.message("run.configuration.editor.env")) { cell(envField).align(AlignX.FILL) }
    }

    override fun resetEditorFrom(configuration: CucumberJsRunConfiguration) {
        interpreterField.interpreterRef = configuration.interpreterRef
        packageField.text = configuration.cucumberPackagePath
        workingDirField.text = configuration.workingDir
        featureField.text = configuration.featurePath
        lineField.text = configuration.line?.toString().orEmpty()
        tagsField.text = configuration.tags
        profileField.text = configuration.profile
        extraArgsField.text = configuration.extraArgs
        envField.data = configuration.envData
    }

    override fun applyEditorTo(configuration: CucumberJsRunConfiguration) {
        configuration.interpreterRef = interpreterField.interpreterRef
        configuration.cucumberPackagePath = packageField.text.trim()
        configuration.workingDir = workingDirField.text.trim()
        val featurePath = featureField.text.trim()
        val line = lineField.text.trim().toIntOrNull()?.takeIf { it > 0 }
        // A hand-edited location no longer names the scenario the configuration was created for
        if (featurePath != configuration.featurePath || line != configuration.line) configuration.scenarioPath = null
        configuration.featurePath = featurePath
        configuration.line = line
        configuration.tags = tagsField.text
        configuration.profile = profileField.text
        configuration.extraArgs = extraArgsField.text
        configuration.envData = envField.data
    }
}
