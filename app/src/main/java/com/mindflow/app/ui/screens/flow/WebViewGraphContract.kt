package com.mindflow.app.ui.screens.flow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mindflow.app.data.wiki.ConceptGraphNode

internal data class WebGraphPayload(
    val version: Int = 1,
    val centerNodeId: String,
    val nodes: List<WebGraphNode>,
    val edges: List<WebGraphEdge>,
)

internal data class WebGraphNode(
    val id: String,
    val label: String,
    val displayLabel: String,
    val accentColor: String,
    val isCenter: Boolean,
    val isSuggested: Boolean,
    val isReturnNode: Boolean,
    val emphasis: Int,
    val xFraction: Double,
    val yFraction: Double,
)

internal data class WebGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relationType: String,
    val confidence: Double,
    val isSuggested: Boolean,
)

internal sealed interface GraphBridgeEvent {
    data object ViewportReady : GraphBridgeEvent
    data class NodeClick(val conceptId: String) : GraphBridgeEvent
    data class RenderError(val message: String) : GraphBridgeEvent
    data class Invalid(val reason: String) : GraphBridgeEvent
}

private data class WebGraphPosition(
    val xFraction: Double,
    val yFraction: Double,
)

private const val FullLabelNeighborCount = 2
private const val ShortLabelNeighborCount = 3

private val WebGraphCenterPosition = WebGraphPosition(
    xFraction = 0.5,
    yFraction = 0.5,
)

private fun Color.toWebHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private val WebGraphToneCenter = Color(0xFF158CFF)
private val WebGraphNeighborPalette = listOf(
    Color(0xFF45B7FF),
    Color(0xFF5FD6A7),
    Color(0xFFFFB458),
    Color(0xFFB38BFF),
    Color(0xFFFF8D8D),
    Color(0xFF63D7E8),
)

private fun webAccentForNeighbor(index: Int, emphasis: Int): Color {
    val base = WebGraphNeighborPalette[index % WebGraphNeighborPalette.size]
    return when {
        emphasis >= 2 -> base
        emphasis >= 1 -> lerp(base, Color.White, 0.18f)
        else -> lerp(base, Color.White, 0.56f)
    }
}

private fun abbreviatedGraphLabel(label: String): String {
    val normalized = label.trim()
    if (normalized.isBlank()) return ""
    if (normalized.length <= 4) return normalized
    return normalized.take(4)
}

private fun escapeJson(value: String): String = buildString(value.length + 8) {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

private fun String.asJsonString(): String = '"' + escapeJson(this) + '"'

private fun extractJsonStringField(raw: String, fieldName: String): String? {
    val pattern = Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    val match = pattern.find(raw) ?: return null
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
}

private fun buildWebNeighborPositions(neighborCount: Int): List<WebGraphPosition> {
    if (neighborCount <= 0) return emptyList()
    val anchored = listOf(
        WebGraphPosition(0.51, 0.2),
        WebGraphPosition(0.24, 0.38),
        WebGraphPosition(0.76, 0.37),
        WebGraphPosition(0.34, 0.7),
        WebGraphPosition(0.67, 0.76),
        WebGraphPosition(0.18, 0.56),
        WebGraphPosition(0.84, 0.57),
        WebGraphPosition(0.28, 0.17),
        WebGraphPosition(0.72, 0.18),
        WebGraphPosition(0.46, 0.88),
        WebGraphPosition(0.57, 0.13),
        WebGraphPosition(0.13, 0.74),
        WebGraphPosition(0.88, 0.78),
        WebGraphPosition(0.58, 0.92),
    )
    return List(neighborCount) { index ->
        anchored.getOrElse(index) {
            val shellIndex = index - anchored.size
            val isLeft = shellIndex % 2 == 0
            val row = shellIndex / 2
            WebGraphPosition(
                xFraction = if (isLeft) 0.16 else 0.84,
                yFraction = (0.28 + (row * 0.12)).coerceAtMost(0.9),
            )
        }
    }
}

private fun reorderGraphNodesForReturnPath(
    directGraphNodes: List<ConceptGraphNode>,
    suggestedGraphNodes: List<ConceptGraphNode>,
    returnNodeId: String?,
): List<ConceptGraphNode> {
    if (returnNodeId.isNullOrBlank()) return directGraphNodes + suggestedGraphNodes
    val returnNode = (directGraphNodes + suggestedGraphNodes).firstOrNull { it.conceptId == returnNodeId }
        ?: return directGraphNodes + suggestedGraphNodes
    return buildList {
        val leadingDirect = directGraphNodes.take(2)
        addAll(leadingDirect)
        if (returnNode.conceptId !in leadingDirect.map { it.conceptId }) {
            add(returnNode)
        }
        directGraphNodes.forEach { node ->
            if (node.conceptId == returnNodeId || node.conceptId in leadingDirect.map { it.conceptId }) return@forEach
            add(node)
        }
        suggestedGraphNodes.forEach { node ->
            if (node.conceptId == returnNodeId) return@forEach
            add(node)
        }
    }
}

internal fun ConceptGraphViewport.toWebPayload(): WebGraphPayload {
    val center = centerNode ?: return WebGraphPayload(centerNodeId = "", nodes = emptyList(), edges = emptyList())
    val useSwitchTargetsAsSuggestions = neighbors.isEmpty() && switchableNodes.isNotEmpty()
    val directGraphNodes = neighbors.map { it.node }
    val suggestedGraphNodes = if (useSwitchTargetsAsSuggestions) {
        switchableNodes
    } else {
        suggestedNeighbors.map { it.node }
    }
    val visibleGraphNodes = reorderGraphNodesForReturnPath(
        directGraphNodes = directGraphNodes,
        suggestedGraphNodes = suggestedGraphNodes,
        returnNodeId = returnNodeId,
    )
    val suggestedNodeIds = suggestedGraphNodes.map { it.conceptId }.toSet()
    val neighborPositions = buildWebNeighborPositions(visibleGraphNodes.size)
    val webNodes = buildList {
        add(
            WebGraphNode(
                id = center.conceptId,
                label = center.label,
                displayLabel = center.label,
                accentColor = WebGraphToneCenter.toWebHex(),
                isCenter = true,
                isSuggested = false,
                isReturnNode = false,
                emphasis = 3,
                xFraction = WebGraphCenterPosition.xFraction,
                yFraction = WebGraphCenterPosition.yFraction,
            ),
        )
        visibleGraphNodes.forEachIndexed { index, node ->
            val position = neighborPositions.getOrElse(index) { WebGraphCenterPosition }
            val isSuggested = node.conceptId in suggestedNodeIds
            val isReturnNode = node.conceptId == returnNodeId
            val emphasis = when {
                isReturnNode -> 2
                isSuggested -> 1
                index < FullLabelNeighborCount -> 2
                index < ShortLabelNeighborCount -> 1
                else -> 0
            }
            add(
                WebGraphNode(
                    id = node.conceptId,
                    label = node.label,
                    displayLabel = when (emphasis) {
                        2 -> node.label
                        1 -> abbreviatedGraphLabel(node.label)
                        else -> ""
                    },
                    accentColor = webAccentForNeighbor(index, emphasis).toWebHex(),
                    isCenter = false,
                    isSuggested = isSuggested,
                    isReturnNode = isReturnNode,
                    emphasis = emphasis,
                    xFraction = position.xFraction,
                    yFraction = position.yFraction,
                ),
            )
        }
    }
    val webEdges = if (useSwitchTargetsAsSuggestions) {
        switchableNodes.map { node ->
            WebGraphEdge(
                id = "${center.conceptId}->${node.conceptId}:suggested",
                source = center.conceptId,
                target = node.conceptId,
                relationType = "suggested",
                confidence = 0.22,
                isSuggested = true,
            )
        }
    } else {
        neighbors.map { neighbor ->
            WebGraphEdge(
                id = "${neighbor.relation.fromConceptId}->${neighbor.relation.toConceptId}:${neighbor.relation.relationType.wireName}",
                source = neighbor.relation.fromConceptId,
                target = neighbor.relation.toConceptId,
                relationType = neighbor.relation.relationType.wireName,
                confidence = neighbor.relation.confidence,
                isSuggested = false,
            )
        } + suggestedNeighbors.map { neighbor ->
            WebGraphEdge(
                id = "${neighbor.relation.fromConceptId}->${neighbor.relation.toConceptId}:${neighbor.relation.relationType.wireName}:suggested",
                source = neighbor.relation.fromConceptId,
                target = neighbor.relation.toConceptId,
                relationType = neighbor.relation.relationType.wireName,
                confidence = neighbor.relation.confidence.coerceAtMost(0.42),
                isSuggested = true,
            )
        }
    }
    return WebGraphPayload(
        centerNodeId = center.conceptId,
        nodes = webNodes.distinctBy { it.id },
        edges = webEdges,
    )
}

internal fun WebGraphPayload.toJavascriptLiteral(): String = buildString {
    append('{')
    append("\"version\":")
    append(version)
    append(',')
    append("\"centerNodeId\":")
    append(centerNodeId.asJsonString())
    append(',')
    append("\"nodes\":[")
    nodes.forEachIndexed { index, node ->
        if (index > 0) append(',')
        append('{')
        append("\"id\":")
        append(node.id.asJsonString())
        append(',')
        append("\"label\":")
        append(node.label.asJsonString())
        append(',')
        append("\"displayLabel\":")
        append(node.displayLabel.asJsonString())
        append(',')
        append("\"accentColor\":")
        append(node.accentColor.asJsonString())
        append(',')
        append("\"isCenter\":")
        append(node.isCenter)
        append(',')
        append("\"isSuggested\":")
        append(node.isSuggested)
        append(',')
        append("\"isReturnNode\":")
        append(node.isReturnNode)
        append(',')
        append("\"emphasis\":")
        append(node.emphasis)
        append(',')
        append("\"xFraction\":")
        append(node.xFraction)
        append(',')
        append("\"yFraction\":")
        append(node.yFraction)
        append('}')
    }
    append(']')
    append(',')
    append("\"edges\":[")
    edges.forEachIndexed { index, edge ->
        if (index > 0) append(',')
        append('{')
        append("\"id\":")
        append(edge.id.asJsonString())
        append(',')
        append("\"source\":")
        append(edge.source.asJsonString())
        append(',')
        append("\"target\":")
        append(edge.target.asJsonString())
        append(',')
        append("\"relationType\":")
        append(edge.relationType.asJsonString())
        append(',')
        append("\"confidence\":")
        append(edge.confidence)
        append(',')
        append("\"isSuggested\":")
        append(edge.isSuggested)
        append('}')
    }
    append(']')
    append('}')
}

internal fun parseGraphBridgeEvent(raw: String): GraphBridgeEvent {
    val type = extractJsonStringField(raw, "type")?.trim().orEmpty()
    return when (type) {
        "viewportReady" -> GraphBridgeEvent.ViewportReady
        "nodeClick" -> {
            val conceptId = extractJsonStringField(raw, "conceptId")?.trim().orEmpty()
            if (conceptId.isBlank()) GraphBridgeEvent.Invalid("missing_concept_id")
            else GraphBridgeEvent.NodeClick(conceptId)
        }
        "renderError" -> GraphBridgeEvent.RenderError(
            extractJsonStringField(raw, "message")?.trim().orEmpty(),
        )
        "" -> GraphBridgeEvent.Invalid("missing_or_invalid_type")
        else -> GraphBridgeEvent.Invalid("unknown_type")
    }
}
