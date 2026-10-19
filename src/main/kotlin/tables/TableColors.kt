package com.wulfindustries.plugins.tables

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/** Colors for Examples tables. Defaults are in `colorSchemes/CucumberJsTables*.xml`. */
object TableColors {
    val CURRENT_COLUMN: TextAttributesKey =
        createTextAttributesKey("CUCUMBER_JS_TABLE_CURRENT_COLUMN", EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES)

    val COLUMNS: List<TextAttributesKey> = (1..6).map { createTextAttributesKey("CUCUMBER_JS_TABLE_COLUMN_$it") }

    fun column(index: Int): TextAttributesKey = COLUMNS[index % COLUMNS.size]
}
