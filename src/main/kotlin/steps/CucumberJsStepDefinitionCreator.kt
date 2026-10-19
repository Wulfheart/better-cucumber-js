package com.wulfindustries.plugins.steps

import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/** Backs Gherkin's "Create step definition" quick fixes by appending cucumber-js snippets to JS/TS step files. */
class CucumberJsStepDefinitionCreator : StepDefinitionCreator {
    /** A file name without extension gets the extension of the project's existing step files, else `.js`. */
    override fun createStepDefinitionContainer(dir: PsiDirectory, name: String): PsiFile {
        val fileName = if ('.' in name) name else "$name.${if (usesTypeScript(dir)) "ts" else "js"}"
        val names = "{ Given, When, Then }"
        val header = if (isEsModule(fileName, dir.virtualFile)) "import $names from '$PACKAGE'\n"
        else "const $names = require('$PACKAGE')\n"
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
        val file = PsiFileFactory.getInstance(dir.project).createFileFromText(fileName, fileType, header)
        return dir.add(file) as PsiFile
    }

    /** Runs inside the quick fix's write command. */
    override fun createStepDefinition(step: GherkinStep, file: PsiFile, withTemplate: Boolean): Boolean {
        val project = file.project
        val documents = PsiDocumentManager.getInstance(project)
        val document = documents.getDocument(file) ?: return false
        val typeScript = file.fileType == TypeScriptFileType
        val snippet = CucumberJsSnippet.of(step)

        addImports(document, snippet.imports(typeScript))
        val text = document.text
        val separator = when {
            text.isBlank() || text.endsWith("\n\n") -> ""
            text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val start = text.length
        document.insertString(start, separator + snippet.render(typeScript, semicolons = usesSemicolons(text)))
        documents.commitDocument(document)
        CodeStyleManager.getInstance(project).reformatText(file, start, document.textLength)

        val caret = document.text.indexOf(CucumberJsSnippet.BODY_COMMENT, start).takeIf { it >= 0 } ?: document.textLength
        file.virtualFile?.let { OpenFileDescriptor(project, it, caret).navigate(true) }
        return true
    }

    /** Next to the existing step definitions, else in `step_definitions` beside the feature file. */
    override fun getDefaultStepDefinitionFolderPath(step: GherkinStep): String {
        val existing = CucumberJsSupportCode.of(step.project).files.firstNotNullOfOrNull { it.virtualFile?.parent }
        if (existing != null) return existing.path
        val featureDir = step.containingFile.originalFile.virtualFile?.parent
        return "${featureDir?.path ?: step.project.basePath}/$STEP_DEFINITIONS_DIR"
    }

    override fun getStepDefinitionFilePath(file: PsiFile): String {
        val virtualFile = file.virtualFile ?: return file.name
        val projectDir = file.project.guessProjectDir() ?: return virtualFile.path
        return VfsUtilCore.getRelativePath(virtualFile, projectDir) ?: virtualFile.path
    }

    override fun getDefaultStepFileName(step: GherkinStep): String =
        "${step.containingFile.originalFile.virtualFile?.nameWithoutExtension ?: step.containingFile.name.substringBefore('.')}_steps"

    /** Adds missing names to the file's `{ … }` import or require of `@cucumber/cucumber`. */
    private fun addImports(document: Document, names: List<String>) {
        val match = IMPORT.find(document.text) ?: return
        val group = match.groups[1] ?: match.groups[2] ?: return
        val present = group.value.split(',').map { it.trim().split(Regex("""\s+as\s+|\s*:\s*""")).first() }
        val missing = names.filter { it !in present }.distinct()
        if (missing.isEmpty()) return

        val inner = group.value
        val insertAt = group.range.first + inner.trimEnd().length
        val addition = if (inner.isBlank()) " ${missing.joinToString(", ")} "
        else (if (inner.trimEnd().endsWith(',')) " " else ", ") + missing.joinToString(", ")
        if (inner.isBlank()) document.replaceString(group.range.first, group.range.last + 1, addition)
        else document.insertString(insertAt, addition)
    }

    private fun usesTypeScript(dir: PsiDirectory): Boolean =
        CucumberJsSupportCode.of(dir.project).files.firstOrNull()?.fileType == TypeScriptFileType

    private fun isEsModule(fileName: String, dir: VirtualFile): Boolean {
        val extension = fileName.substringAfterLast('.')
        if (extension in setOf("ts", "mts", "mjs")) return true
        if (extension == "cjs") return false
        val packageJson = generateSequence(dir) { it.parent }.firstNotNullOfOrNull { it.findChild("package.json") }
            ?: return false
        return MODULE_TYPE.containsMatchIn(VfsUtilCore.loadText(packageJson))
    }

    /** Most statements end in a semicolon. */
    private fun usesSemicolons(text: String): Boolean {
        val lines = text.lines().map { it.trimEnd() }.filter { it.endsWith(";") || it.endsWith(")") }
        return lines.count { it.endsWith(";") } * 2 > lines.size
    }

    private companion object {
        const val PACKAGE = "@cucumber/cucumber"
        const val STEP_DEFINITIONS_DIR = "step_definitions"
        val IMPORT = Regex(
            """import\s*\{([^}]*)}\s*from\s*['"]@cucumber/cucumber['"]""" +
                """|(?:const|let|var)\s*\{([^}]*)}\s*=\s*require\(\s*['"]@cucumber/cucumber['"]\s*\)"""
        )
        val MODULE_TYPE = Regex(""""type"\s*:\s*"module"""")
    }
}
