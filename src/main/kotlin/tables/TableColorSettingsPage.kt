package com.wulfindustries.plugins.tables

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import javax.swing.Icon

class TableColorSettingsPage : ColorSettingsPage {
    private val tags: Map<String, TextAttributesKey> =
        TableColors.COLUMNS.withIndex().associate { (i, key) -> "c${i + 1}" to key } +
            ("current" to TableColors.CURRENT_COLUMN) + ("substituted" to TableColors.SUBSTITUTED_VALUE)

    override fun getDisplayName(): String = MyMessageBundle.message("tables.settings.name")

    override fun getIcon(): Icon? = GherkinFileType.INSTANCE.icon

    override fun getHighlighter(): SyntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(GherkinLanguage.INSTANCE, null, null)

    override fun getDemoText(): String = """
        Scenario Outline: Ordering <item>
          Given I order <amount> <item>
          # Example Row tool window, Scenario tab: Given I order <substituted>3</substituted> <substituted>apple</substituted>
          Examples:
            | <c1>item</c1>   | <c2>amount</c2> | <current>   price   </current>| <c4>currency</c4> | <c5>shop</c5>   | <c6>note</c6>  | <c1>express</c1> |
            | <c1>apple</c1>  | <c2>3</c2>      | <current>   1.20    </current>| <c4>EUR</c4>      | <c5>Berlin</c5> | <c6>ripe</c6>  | <c1>yes</c1>     |
            | <c1>pear</c1>   | <c2>12</c2>     | <current>   0.80    </current>| <c4>EUR</c4>      | <c5>Hamburg</c5>| <c6>green</c6> | <c1>no</c1>      |
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tags

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> =
        TableColors.COLUMNS.mapIndexed { i, key -> AttributesDescriptor(MyMessageBundle.message("tables.color.column", i + 1), key) }
            .plus(AttributesDescriptor(MyMessageBundle.message("tables.color.current.column"), TableColors.CURRENT_COLUMN))
            .plus(AttributesDescriptor(MyMessageBundle.message("tables.color.substituted.value"), TableColors.SUBSTITUTED_VALUE))
            .toTypedArray()

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
