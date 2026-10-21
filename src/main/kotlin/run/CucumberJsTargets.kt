package com.wulfindustries.plugins.run

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * What a gutter icon or context action runs: a whole feature file ([line] is `null`),
 * a scenario / scenario outline, or a single Examples row (1-based [line]).
 */
data class CucumberJsTarget(
    val element: PsiElement,
    val file: VirtualFile,
    val line: Int?,
    val name: String,
) {
    val isFeature: Boolean get() = line == null
    val url: String get() = CucumberJsLocations.url(file.path, line)
}

object CucumberJsTargets {
    private const val CUCUMBER_PACKAGE = "node_modules/@cucumber/cucumber"
    private const val CUCUMBER_DEPENDENCY = "\"@cucumber/cucumber\""
    private val JVM_BUILD_FILES = listOf("pom.xml", "build.gradle", "build.gradle.kts")

    /** Resolves the innermost runnable target that contains [element], or `null` outside a `.feature` file. */
    fun find(element: PsiElement): CucumberJsTarget? {
        val gherkinFile = element.containingFile as? GherkinFile ?: return null
        val file = gherkinFile.virtualFile ?: return null

        exampleRow(element)?.let { row ->
            val outline = PsiTreeUtil.getParentOfType(row, GherkinScenarioOutline::class.java)
            return CucumberJsTarget(row, file, lineOf(row), outline?.scenarioName.orEmpty())
        }
        runnableScenario(element)?.let { scenario ->
            return CucumberJsTarget(scenario, file, lineOf(scenario), scenario.scenarioName)
        }
        val feature = PsiTreeUtil.getParentOfType(element, GherkinFeature::class.java, false)
            ?: PsiTreeUtil.getChildOfType(gherkinFile, GherkinFeature::class.java)
        return CucumberJsTarget(feature ?: gherkinFile, file, null, feature?.featureName ?: file.nameWithoutExtension)
    }

    /** Nearest directory at or above [start] (a feature file or a directory) that has `node_modules/@cucumber/cucumber` installed. */
    fun findWorkingDir(start: VirtualFile): VirtualFile? {
        var dir = if (start.isDirectory) start else start.parent
        while (dir != null) {
            if (dir.findFileByRelativePath(CUCUMBER_PACKAGE)?.isDirectory == true) return dir
            dir = dir.parent
        }
        return null
    }

    /** Whether [dir] has a `.feature` file in it or any of its subdirectories, leaving out libraries and excluded folders. */
    fun containsFeatures(project: Project, dir: VirtualFile): Boolean {
        if (DumbService.isDumb(project)) return false
        val scope = GlobalSearchScope.projectScope(project)
            .intersectWith(GlobalSearchScopesCore.directoryScope(project, dir, true))
        return FileTypeIndex.containsFileOfType(GherkinFileType.INSTANCE, scope)
    }

    /**
     * Whether the feature file [file] belongs to Cucumber.js rather than another runner such as Cucumber for Java.
     * Decided by where it sits, so that every feature file has exactly one runner and one set of step definitions.
     * Walking up from it, the first of these decides: a `package.json` that declares `@cucumber/cucumber` or a
     * `node_modules/@cucumber/cucumber` install makes it ours, a JVM build file makes it not (e.g. `java/pom.xml`
     * below a workspace root that hoists `node_modules`). With none of them it's ours, as nothing claims it otherwise.
     * Uses only the VFS, so it also works while indexing.
     */
    fun isCucumberJsFeature(file: VirtualFile): Boolean {
        var dir = file.parent
        while (dir != null) {
            val current: VirtualFile = dir
            if (declaresCucumber(current.findChild("package.json"))) return true
            if (JVM_BUILD_FILES.any { current.findChild(it) != null }) return false
            if (current.findFileByRelativePath(CUCUMBER_PACKAGE)?.isDirectory == true) return true
            dir = current.parent
        }
        return true
    }

    private fun declaresCucumber(packageJson: VirtualFile?): Boolean =
        packageJson != null && !packageJson.isDirectory &&
            runCatching { CUCUMBER_DEPENDENCY in VfsUtilCore.loadText(packageJson) }.getOrDefault(false)

    fun cucumberPackageDir(workingDir: VirtualFile): VirtualFile? = workingDir.findFileByRelativePath(CUCUMBER_PACKAGE)

    /** The Examples data row containing [element], excluding the header row. */
    internal fun exampleRow(element: PsiElement): GherkinTableRow? {
        val row = PsiTreeUtil.getParentOfType(element, GherkinTableRow::class.java, false) ?: return null
        val examples = PsiTreeUtil.getParentOfType(row, GherkinExamplesBlock::class.java) ?: return null
        return row.takeIf { it in examples.table?.dataRows.orEmpty() }
    }

    /** The scenario or scenario outline containing [element]; `null` inside a Background or outside any scenario. */
    internal fun runnableScenario(element: PsiElement): GherkinStepsHolder? {
        val holder = PsiTreeUtil.getParentOfType(element, GherkinStepsHolder::class.java, false) ?: return null
        return when (holder) {
            is GherkinScenario -> holder.takeUnless { it.isBackground }
            is GherkinScenarioOutline -> holder
            else -> null
        }
    }

    /**
     * 1-based line cucumber-js uses for [element]. Scenarios are addressed by their keyword line,
     * which differs from the PSI start when tags precede the keyword.
     */
    internal fun lineOf(element: PsiElement): Int? {
        val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return null
        return document.getLineNumber(anchorOf(element).textRange.startOffset) + 1
    }

    /** The part of [element] that [lineOf] measures: a scenario's keyword, or the element itself. */
    internal fun anchorOf(element: PsiElement): PsiElement =
        element.node.findChildByType(GherkinTokenTypes.SCENARIOS_KEYWORDS)?.psi ?: element
}
