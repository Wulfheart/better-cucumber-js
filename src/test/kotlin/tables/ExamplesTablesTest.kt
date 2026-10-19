package com.wulfindustries.plugins.tables

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExamplesTablesTest : BasePlatformTestCase() {
    private val feature = """
        Feature: Tables
          Scenario Outline: Prices
            Given a <name> with data
              | inline | table |
            Examples: Cheap
              | name  | price | note       |
              | apple | 1     |            |
              | pear  | 2     | a \| b \n c |
              | short | 3 |
    """.trimIndent()

    private fun positionAtMarker(marker: String, delta: Int = 0): ExamplesTablePosition? {
        val file = myFixture.configureByText("a.feature", feature)
        val offset = feature.indexOf(marker) + delta
        assertTrue(marker, offset >= 0)
        return ExamplesTables.positionAt(file, offset)
    }

    fun testHeaderCell() {
        val position = positionAtMarker("price |")!!
        assertTrue(position.isHeader)
        assertEquals(1, position.column)
    }

    fun testDataRowAndColumnAfterEmptyCell() {
        val file = myFixture.configureByText("a.feature", feature)
        val apple = ExamplesTables.positionAt(file, feature.indexOf("apple"))!!
        assertEquals(1, apple.rowIndex)
        assertEquals(0, apple.column)

        // The empty note cell of the apple row is still column 2
        val emptyNote = feature.indexOf("|", feature.indexOf("1     |") + 7) - 3
        assertEquals(2, ExamplesTables.positionAt(file, emptyNote)!!.column)
    }

    fun testEscapedPipeStaysInItsCell() {
        val file = myFixture.configureByText("a.feature", feature)
        val position = ExamplesTables.positionAt(file, feature.indexOf("b \\n"))!!
        assertEquals(2, position.rowIndex)
        assertEquals(2, position.column)
        assertEquals(listOf("pear", "2", "a | b \n c"), ExamplesTables.cells(position.row))
        assertEquals(listOf("pear", "2", "a \\| b \\n c"), ExamplesTables.rawCells(position.row))
    }

    fun testEmptyCellValue() {
        val file = myFixture.configureByText("a.feature", feature)
        val position = ExamplesTables.positionAt(file, feature.indexOf("apple"))!!
        assertEquals(listOf("apple", "1", ""), ExamplesTables.cells(position.row))
        assertEquals(listOf("name", "price", "note"), ExamplesTables.headers(position.table))
    }

    fun testCaretOnPipeBelongsToTheCellOnItsLeft() {
        val position = positionAtMarker("| price")!!
        assertEquals(0, position.column)
        assertEquals(1, positionAtMarker("| price", 1)!!.column)
    }

    fun testIndentAndLineEndAreOnTheRowButNoColumn() {
        val file = myFixture.configureByText("a.feature", feature)
        val rowStart = feature.indexOf("| apple")
        assertNull(ExamplesTables.positionAt(file, rowStart - 1)?.column)
        val lineEnd = feature.indexOf('\n', rowStart)
        val atEnd = ExamplesTables.positionAt(file, lineEnd)!!
        assertEquals(1, atEnd.rowIndex)
        assertNull(atEnd.column)
    }

    fun testRaggedRow() {
        val position = positionAtMarker("short")!!
        assertEquals(3, position.rowIndex)
        assertEquals(listOf("short", "3"), ExamplesTables.cells(position.row))
    }

    fun testStepDataTablesAndStepsAreIgnored() {
        assertNull(positionAtMarker("inline"))
        assertNull(positionAtMarker("Given"))
    }

    fun testEscapeRoundTrip() {
        val value = "a | b\nc \\ d"
        assertEquals("a \\| b\\nc \\\\ d", ExamplesTables.escape(value))
        assertEquals(value, ExamplesTables.unescape(ExamplesTables.escape(value)))
    }

    fun testDescribe() {
        val position = positionAtMarker("apple")!!
        assertEquals("Prices › Examples: Cheap", ExamplesTables.describe(position.examples))
    }
}
