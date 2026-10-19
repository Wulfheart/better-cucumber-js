package com.wulfindustries.plugins.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.testframework.sm.FileUrlProvider
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.javascript.nodejs.debug.NodeDebugRunConfiguration
import com.intellij.javascript.nodejs.execution.AbstractNodeTargetRunProfile
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.psi.PsiDocumentManager
import com.wulfindustries.plugins.MyMessageBundle
import com.wulfindustries.plugins.run.CucumberJsCommandLine.FeatureLocation
import org.jdom.Element
import java.io.File
import java.nio.file.Path

class CucumberJsRunConfiguration(
    project: Project,
    factory: CucumberJsConfigurationFactory,
    name: String,
) : AbstractNodeTargetRunProfile(project, factory, name),
    NodeDebugRunConfiguration,
    SMRunnerConsolePropertiesProvider {

    var interpreterRef: NodeJsInterpreterRef = NodeJsInterpreterRef.createProjectRef()

    /** Directory of the `@cucumber/cucumber` package, e.g. `<project>/node_modules/@cucumber/cucumber`. */
    var cucumberPackagePath: String = ""
    var workingDir: String = ""
    var featurePath: String = ""

    /** 1-based line of the scenario or example row to run; `null` runs the whole feature. */
    var line: Int? = null

    /** The scenario or example row at [line] by name, to find it again once lines above it were added or removed. */
    var scenarioPath: ScenarioPath? = null
    var tags: String = ""
    var profile: String = ""
    var extraArgs: String = ""
    override var envData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    override val interpreter: NodeJsInterpreter?
        get() = interpreterRef.resolve(project)

    /** [workingDir], or the feature file's directory when that is blank. */
    val effectiveWorkingDir: String
        get() = workingDir.ifBlank { File(featurePath).parent.orEmpty() }

    /**
     * What to run: the feature file, limited to the line the scenario or example row is on now. Updates [line] when
     * the scenario has moved since the configuration was created or last run.
     */
    fun currentLocation(): FeatureLocation {
        val line = line ?: return FeatureLocation(featurePath, emptyList())
        val path = scenarioPath ?: return FeatureLocation(featurePath, listOf(line))
        if (ApplicationManager.getApplication().isDispatchThread) {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val current = runReadActionBlocking {
            path.copy(featurePath = featurePath).find(project, line)?.let { element ->
                CucumberJsTargets.lineOf(element)?.let { it to ScenarioPath.of(element) }
            }
        }
        if (current != null) {
            this.line = current.first
            scenarioPath = current.second
        }
        return FeatureLocation(featurePath, listOf(current?.first ?: line))
    }

    override fun createConfigurationEditor(): SettingsEditor<out AbstractNodeTargetRunProfile> =
        CucumberJsSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        CucumberJsRunProfileState(this, environment)

    override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties {
        val baseDir = Path.of(effectiveWorkingDir)
        return object : SMTRunnerConsoleProperties(this, MyMessageBundle.message("run.configuration.test.framework.name"), executor),
            SMCustomMessagesParsing {
            init {
                // The translator pre-builds the tree with nodeId/parentNodeId messages
                isIdBasedTestTree = true
            }

            override fun getTestLocator(): SMTestLocator = FileUrlProvider.INSTANCE

            override fun createTestEventsConverter(
                testFrameworkName: String,
                consoleProperties: TestConsoleProperties,
            ): OutputToGeneralTestEventsConverter =
                CucumberJsTestEventsConverter(testFrameworkName, consoleProperties, CucumberMessagesTranslator(baseDir))

            override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction? =
                (consoleView as? SMTRunnerConsoleView)?.let { CucumberJsRerunFailedTestsAction(it, this) }
        }
    }

    override fun checkConfiguration() {
        if (interpreter == null) {
            throw RuntimeConfigurationError(MyMessageBundle.message("run.configuration.error.no.interpreter"))
        }
        if (!File(cucumberPackagePath, CucumberJsCommandLine.BIN_RELATIVE_PATH).isFile) {
            throw RuntimeConfigurationError(MyMessageBundle.message("run.configuration.error.no.package", cucumberPackagePath))
        }
        if (workingDir.isNotBlank() && !File(workingDir).isDirectory) {
            throw RuntimeConfigurationError(MyMessageBundle.message("run.configuration.error.no.working.dir", workingDir))
        }
        if (!File(featurePath).isFile) {
            throw RuntimeConfigurationError(MyMessageBundle.message("run.configuration.error.no.feature", featurePath))
        }
    }

    override fun suggestedName(): String? {
        if (featurePath.isBlank()) return null
        val fileName = File(featurePath).name
        return line?.let { "$fileName:$it" } ?: fileName
    }

    override fun readConfiguration(element: Element) {
        super.readConfiguration(element)
        interpreterRef = JDOMExternalizerUtil.readField(element, INTERPRETER)
            ?.let(NodeJsInterpreterRef::create)
            ?: NodeJsInterpreterRef.createProjectRef()
        cucumberPackagePath = JDOMExternalizerUtil.readField(element, PACKAGE, "")
        workingDir = JDOMExternalizerUtil.readField(element, WORKING_DIR, "")
        featurePath = JDOMExternalizerUtil.readField(element, FEATURE, "")
        line = JDOMExternalizerUtil.readField(element, LINE)?.toIntOrNull()
        scenarioPath = line?.let { ScenarioPath.readFrom(element, featurePath) }
        tags = JDOMExternalizerUtil.readField(element, TAGS, "")
        profile = JDOMExternalizerUtil.readField(element, PROFILE, "")
        extraArgs = JDOMExternalizerUtil.readField(element, EXTRA_ARGS, "")
        envData = EnvironmentVariablesData.readExternal(element)
    }

    override fun writeConfiguration(element: Element) {
        super.writeConfiguration(element)
        JDOMExternalizerUtil.writeField(element, INTERPRETER, interpreterRef.referenceName)
        JDOMExternalizerUtil.writeField(element, PACKAGE, cucumberPackagePath)
        JDOMExternalizerUtil.writeField(element, WORKING_DIR, workingDir)
        JDOMExternalizerUtil.writeField(element, FEATURE, featurePath)
        line?.let {
            JDOMExternalizerUtil.writeField(element, LINE, it.toString())
            scenarioPath?.writeTo(element)
        }
        JDOMExternalizerUtil.writeField(element, TAGS, tags)
        JDOMExternalizerUtil.writeField(element, PROFILE, profile)
        JDOMExternalizerUtil.writeField(element, EXTRA_ARGS, extraArgs)
        envData.writeExternal(element)
    }

    private companion object {
        const val INTERPRETER = "node-interpreter"
        const val PACKAGE = "cucumber-package"
        const val WORKING_DIR = "working-dir"
        const val FEATURE = "feature-path"
        const val LINE = "line"
        const val TAGS = "tags"
        const val PROFILE = "profile"
        const val EXTRA_ARGS = "extra-args"
    }
}
