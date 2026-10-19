package com.wulfindustries.plugins.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The Gherkin AST of the `gherkinDocument` messages of a run, indexed by AST node id. Pickles and their steps point
 * into it through `astNodeIds`, which is how the test tree finds rules, outlines, Examples tables, backgrounds and
 * step keywords.
 */
class GherkinIndex {
    enum class Kind { FEATURE, RULE, BACKGROUND, SCENARIO, EXAMPLES, ROW, STEP }

    class AstNode(
        val id: String,
        val kind: Kind,
        val keyword: String,
        val name: String,
        val line: Int?,
        val parent: AstNode?,
    ) {
        val children = mutableListOf<AstNode>()

        /** Cell values of an Examples row. */
        var cells: List<String> = emptyList()
            internal set

        /** The name, or the keyword for nameless nodes such as an unnamed `Examples:` table. */
        val label: String get() = name.ifBlank { keyword.trim() }

        /** 1-based position of an Examples row within its table. */
        val rowNumber: Int get() = (parent?.children?.indexOf(this) ?: 0) + 1

        /** True for a Scenario Outline with more than one Examples table that has rows. */
        val hasSeveralExamplesTables: Boolean
            get() = children.count { it.kind == Kind.EXAMPLES && it.children.isNotEmpty() } > 1
    }

    private val nodes = mutableMapOf<String, AstNode>()
    private val features = mutableMapOf<String, AstNode>()

    fun node(id: String): AstNode? = nodes[id]

    fun feature(uri: String): AstNode? = features[uri]

    fun add(document: JsonObject) {
        val uri = document.str("uri") ?: return
        val feature = document.obj("feature") ?: return
        val featureNode = AstNode("feature:$uri", Kind.FEATURE, feature.str("keyword").orEmpty(),
            feature.str("name").orEmpty(), feature.line(), null)
        features[uri] = featureNode
        addChildren(feature.array("children"), featureNode)
    }

    /** Feature and rule children: each holds exactly one of `background`, `scenario` or `rule`. */
    private fun addChildren(children: JsonArray?, parent: AstNode) {
        children?.forEach { element ->
            val child = element.asJsonObject
            child.obj("background")?.let { background ->
                add(background, Kind.BACKGROUND, parent)?.let { addSteps(background, it) }
            }
            child.obj("scenario")?.let { scenario ->
                val scenarioNode = add(scenario, Kind.SCENARIO, parent) ?: return@let
                addSteps(scenario, scenarioNode)
                scenario.array("examples")?.forEach { examples ->
                    val examplesObj = examples.asJsonObject
                    val examplesNode = add(examplesObj, Kind.EXAMPLES, scenarioNode) ?: return@forEach
                    examplesObj.array("tableBody")?.forEach { row ->
                        val rowObj = row.asJsonObject
                        add(rowObj, Kind.ROW, examplesNode)?.cells =
                            rowObj.array("cells")?.mapNotNull { it.asJsonObject.str("value") }.orEmpty()
                    }
                }
            }
            child.obj("rule")?.let { rule ->
                add(rule, Kind.RULE, parent)?.let { addChildren(rule.array("children"), it) }
            }
        }
    }

    private fun addSteps(owner: JsonObject, parent: AstNode) {
        owner.array("steps")?.forEach { add(it.asJsonObject, Kind.STEP, parent) }
    }

    private fun add(obj: JsonObject, kind: Kind, parent: AstNode): AstNode? {
        val id = obj.str("id") ?: return null
        val name = if (kind == Kind.STEP) obj.str("text") else obj.str("name")
        val node = AstNode(id, kind, obj.str("keyword").orEmpty(), name.orEmpty(), obj.line(), parent)
        parent.children += node
        nodes[id] = node
        return node
    }

    private fun JsonObject.line(): Int? = obj("location")?.get("line")?.asInt
}

internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
