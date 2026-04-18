package com.mindflow.app.ui.screens.flow

import androidx.compose.ui.graphics.Color
import com.mindflow.app.ui.theme.Accent
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

internal data class WebGraphPayload(
    val version: Int = 1,
    val centerNodeId: String,
    val nodes: List<WebGraphNode>,
    val edges: List<WebGraphEdge>,
)

internal data class WebGraphNode(
    val id: String,
    val label: String,
    val accentColor: String,
    val isCenter: Boolean,
    val xFraction: Double,
    val yFraction: Double,
)

internal data class WebGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relationType: String,
    val confidence: Double,
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

private val WebGraphCenterPosition = WebGraphPosition(
    xFraction = 0.5,
    yFraction = 0.52,
)

private fun Color.toWebHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun webAccentForConceptId(conceptId: String): Color {
    val palette = listOf(
        Accent,
        Color(0xFF0F766E),
        Color(0xFF2563EB),
        Color(0xFFB45309),
        Color(0xFFDB2777),
    )
    return palette[conceptId.hashCode().absoluteValue % palette.size]
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
    val radiusX = 0.32
    val radiusY = 0.28
    val startAngleDegrees = -90.0
    val angleStepDegrees = 360.0 / neighborCount.toDouble()
    return List(neighborCount) { index ->
        val angleRadians = Math.toRadians(startAngleDegrees + (angleStepDegrees * index))
        WebGraphPosition(
            xFraction = (0.5 + (radiusX * cos(angleRadians))).coerceIn(0.14, 0.86),
            yFraction = (0.52 + (radiusY * sin(angleRadians))).coerceIn(0.16, 0.84),
        )
    }
}

internal fun ConceptGraphViewport.toWebPayload(): WebGraphPayload {
    val center = centerNode ?: return WebGraphPayload(centerNodeId = "", nodes = emptyList(), edges = emptyList())
    val neighborPositions = buildWebNeighborPositions(neighbors.size)
    val webNodes = buildList {
        add(
            WebGraphNode(
                id = center.conceptId,
                label = center.label,
                accentColor = webAccentForConceptId(center.conceptId).toWebHex(),
                isCenter = true,
                xFraction = WebGraphCenterPosition.xFraction,
                yFraction = WebGraphCenterPosition.yFraction,
            ),
        )
        neighbors.forEachIndexed { index, neighbor ->
            val position = neighborPositions.getOrElse(index) { WebGraphCenterPosition }
            add(
                WebGraphNode(
                    id = neighbor.node.conceptId,
                    label = neighbor.node.label,
                    accentColor = webAccentForConceptId(neighbor.node.conceptId).toWebHex(),
                    isCenter = false,
                    xFraction = position.xFraction,
                    yFraction = position.yFraction,
                ),
            )
        }
    }
    val webEdges = neighbors.map { neighbor ->
        WebGraphEdge(
            id = "${neighbor.relation.fromConceptId}->${neighbor.relation.toConceptId}:${neighbor.relation.relationType.wireName}",
            source = neighbor.relation.fromConceptId,
            target = neighbor.relation.toConceptId,
            relationType = neighbor.relation.relationType.wireName,
            confidence = neighbor.relation.confidence,
        )
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
        append("\"accentColor\":")
        append(node.accentColor.asJsonString())
        append(',')
        append("\"isCenter\":")
        append(node.isCenter)
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
