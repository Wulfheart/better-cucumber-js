package com.wulfindustries.plugins.tables

import com.wulfindustries.plugins.MyMessageBundle
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/** One column of an Examples row, shown as a line in the Scenario Inspector's Example Values tab. */
data class RecordLine(val column: Int, val header: String, val pinned: String?, val value: String) {
    val differs: Boolean get() = pinned != null && pinned != value
}

/** An Examples data row as `header: value` lines, optionally next to a pinned row. */
data class ExampleRecord(
    val title: String,
    val rowName: String,
    val pinnedName: String?,
    val lines: List<RecordLine>,
) {
    companion object {
        /**
         * Builds the record for [row]. Values of [pinned] are matched by column when it is in the same table,
         * otherwise by header name. With [differencesOnly], only the lines that differ from the pinned row remain.
         */
        fun of(row: GherkinTableRow, pinned: GherkinTableRow?, differencesOnly: Boolean): ExampleRecord? {
            val table = ExamplesTables.tableOf(row) ?: return null
            val headers = ExamplesTables.headers(table)
            val values = ExamplesTables.cells(row)
            val pinnedTable = pinned?.let(ExamplesTables::tableOf)
            val pinnedValues = pinnedTable?.let { pinnedTable ->
                val cells = ExamplesTables.cells(pinned)
                if (pinnedTable == table) {
                    cells
                } else {
                    val pinnedHeaders = ExamplesTables.headers(pinnedTable)
                    headers.map { header -> pinnedHeaders.indexOf(header).takeIf { it >= 0 }?.let(cells::getOrNull) }
                }
            }
            val lines = (0 until maxOf(headers.size, values.size)).map { column ->
                RecordLine(
                    column,
                    headers.getOrNull(column) ?: MyMessageBundle.message("tables.breadcrumb.column", column + 1),
                    pinnedValues?.let { it.getOrNull(column).orEmpty() },
                    values.getOrNull(column).orEmpty(),
                )
            }
            return ExampleRecord(
                title = ExamplesTables.describe(table.parent as GherkinExamplesBlock),
                rowName = rowName(row),
                pinnedName = pinned?.takeIf { pinnedTable != null }?.let { rowName(it, otherTable = pinnedTable != table) },
                lines = if (differencesOnly && pinnedValues != null) lines.filter { it.differs } else lines,
            )
        }

        private fun rowName(row: GherkinTableRow, otherTable: Boolean = false): String {
            val table = ExamplesTables.tableOf(row)!!
            val name = MyMessageBundle.message("tables.tool.window.example", ExamplesTables.rows(table).indexOf(row))
            if (!otherTable) return name
            return "$name (${ExamplesTables.describe(table.parent as GherkinExamplesBlock)})"
        }
    }
}
