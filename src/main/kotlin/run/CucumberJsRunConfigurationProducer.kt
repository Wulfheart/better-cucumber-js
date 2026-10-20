package com.wulfindustries.plugins.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement

class CucumberJsRunConfigurationProducer : LazyRunConfigurationProducer<CucumberJsRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = CucumberJsConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        featureDirectory(context)?.let { dir ->
            if (!setupLocation(configuration, dir.virtualFile)) return false
            configuration.line = null
            configuration.scenarioPath = null
            configuration.name = dir.name
            return true
        }
        val target = context.psiLocation?.let(CucumberJsTargets::find) ?: return false
        if (!setupLocation(configuration, target.file)) return false
        configuration.line = target.line
        configuration.scenarioPath = if (target.isFeature) null else ScenarioPath.of(target.element)
        configuration.name = target.name
        // sourceElement stays the clicked element. The platform ranks configurations by source element first and
        // drops any whose source is an ancestor of another's, before shouldReplace is asked. Cucumber for Java keeps
        // the clicked leaf, so setting the enclosing scenario here would make ours disappear from the gutter.
        return true
    }

    /**
     * Cucumber for Java accepts every feature file in a module with cucumber-java on its classpath. Replace it (and any
     * other runner) where the feature's steps are implemented in JS/TS.
     */
    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        val element = self.sourceElement
        return element !is PsiDirectory && CucumberJsTargets.stepLanguage(element) == StepLanguage.JS
    }

    /**
     * Step back where the feature's steps are implemented in another language, e.g. Java below a directory that also
     * has `@cucumber/cucumber` installed, so that runner runs them. Directories and undefined steps keep both entries.
     */
    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        val element = self.sourceElement
        return element is PsiDirectory || CucumberJsTargets.stepLanguage(element) != StepLanguage.OTHER
    }

    override fun isConfigurationFromContext(
        configuration: CucumberJsRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        featureDirectory(context)?.let { dir ->
            return configuration.line == null && FileUtil.pathsEqual(configuration.featurePath, dir.virtualFile.path)
        }
        val target = context.psiLocation?.let(CucumberJsTargets::find) ?: return false
        if (!FileUtil.pathsEqual(configuration.featurePath, target.file.path)) return false
        // By name where possible: the line is stale once lines above the scenario were added or removed
        val path = configuration.scenarioPath
        return if (path != null && !target.isFeature) path == ScenarioPath.of(target.element) else configuration.line == target.line
    }

    /** The directory selected in the Project view, if it has feature files somewhere below it. */
    private fun featureDirectory(context: ConfigurationContext): PsiDirectory? {
        val dir = context.psiLocation as? PsiDirectory ?: return null
        return dir.takeIf { CucumberJsTargets.containsFeatures(dir.project, dir.virtualFile) }
    }

    /** Points [configuration] at [featurePath] (a feature file or directory) and the cucumber-js installation above it. */
    private fun setupLocation(configuration: CucumberJsRunConfiguration, featurePath: VirtualFile): Boolean {
        val workingDir = CucumberJsTargets.findWorkingDir(featurePath) ?: return false
        val cucumberPackage = CucumberJsTargets.cucumberPackageDir(workingDir) ?: return false
        configuration.featurePath = featurePath.path
        configuration.workingDir = workingDir.path
        configuration.cucumberPackagePath = cucumberPackage.path
        return true
    }
}
