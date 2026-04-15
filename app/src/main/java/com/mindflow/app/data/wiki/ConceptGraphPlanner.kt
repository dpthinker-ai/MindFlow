package com.mindflow.app.data.wiki

import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate

class ConceptGraphPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
    private val conceptGraphGenerator: suspend (AiSettings, String) -> AiChatResult = { settings, contextSummary ->
        aiServiceClient.generateConceptGraphSnapshot(
            settings = settings,
            contextSummary = contextSummary,
        )
    },
) {
    suspend fun summarize(
        candidates: List<ConceptGraphCandidate>,
    ): ConceptGraphSnapshot {
        if (candidates.isEmpty()) return ConceptGraphSnapshot()

        val generatedAt = System.currentTimeMillis()
        val fallbackSnapshot = buildRuleSnapshot(
            candidates = candidates,
            generatedAt = generatedAt,
        )
        val settings = aiSettingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) {
            return fallbackSnapshot
        }

        val dayKey = LocalDate.now().toString()
        aiSettingsRepository.recordUsage(
            requestIncrement = 1,
            dayKey = dayKey,
        )

        return when (
            val result = conceptGraphGenerator(
                settings,
                buildAiContext(candidates),
            )
        ) {
            is AiChatResult.Success -> {
                val parsed = parseSnapshot(
                    raw = result.content,
                    candidates = candidates,
                    generatedAt = generatedAt,
                )
                if (parsed != null && parsed.nodes.isNotEmpty()) {
                    aiSettingsRepository.recordUsage(
                        successIncrement = 1,
                        tokenIncrement = result.totalTokens ?: 0,
                        dayKey = dayKey,
                    )
                    parsed
                } else {
                    fallbackSnapshot
                }
            }

            is AiChatResult.Failure -> fallbackSnapshot
        }
    }

    private fun buildRuleSnapshot(
        candidates: List<ConceptGraphCandidate>,
        generatedAt: Long,
    ): ConceptGraphSnapshot {
        val nodes = candidates.map { candidate ->
            candidate.toNode(
                label = candidate.title,
                aliases = candidate.aliases,
                summary = candidate.summary,
                hotnessScore = candidate.hotnessScore,
                updatedAt = candidate.updatedAt,
                sourceIds = candidate.sourceIds,
            )
        }
        return ConceptGraphSnapshot(
            defaultCenterNodeId = selectDefaultCenterNodeId(nodes),
            nodes = nodes,
            edges = emptyList(),
            source = "rule",
            generatedAt = generatedAt,
        )
    }

    private fun buildAiContext(
        candidates: List<ConceptGraphCandidate>,
    ): String = buildString {
        appendLine("conceptCandidates:")
        candidates.forEach { candidate ->
            appendLine(
                "conceptId=${candidate.conceptId} | title=${candidate.title} | aliases=${candidate.aliases.joinToString(",")} | hotnessScore=${candidate.hotnessScore} | updatedAt=${candidate.updatedAt}",
            )
            if (candidate.summary.isNotBlank()) {
                appendLine("summary=${candidate.summary}")
            }
            if (candidate.sourceIds.isNotEmpty()) {
                appendLine("sourceIds=${candidate.sourceIds.joinToString(",")}")
            }
        }
        appendLine()
        appendLine("Return JSON only.")
        appendLine("nodes[].conceptId must resolve to one provided concept candidate or one of its aliases.")
        appendLine("edges[].relationType must be one of: supports, advances, parallel, references, contrasts.")
    }

    private fun parseSnapshot(
        raw: String,
        candidates: List<ConceptGraphCandidate>,
        generatedAt: Long,
    ): ConceptGraphSnapshot? {
        val jsonText = extractJsonObject(raw) ?: return null
        return try {
            val root = MiniJsonParser(jsonText).parseObject()
            val candidateByConceptId = candidates.associateBy { it.conceptId }
            val candidateLookup = buildCandidateLookup(candidates)
            val nodes = parseNodes(
                root = root,
                candidateLookup = candidateLookup,
                candidateByConceptId = candidateByConceptId,
            )
            if (nodes.isEmpty()) return null

            val resolvedNodeIds = nodes.map { it.conceptId }.toSet()
            val nodeKeyLookup = buildNodeKeyLookup(
                nodes = nodes,
                candidateByConceptId = candidateByConceptId,
            )
            val edges = parseEdges(
                root = root,
                nodeKeyLookup = nodeKeyLookup,
                resolvedNodeIds = resolvedNodeIds,
            )

            ConceptGraphSnapshot(
                defaultCenterNodeId = selectDefaultCenterNodeId(nodes),
                nodes = nodes,
                edges = edges,
                source = "llm+rule",
                generatedAt = generatedAt,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseNodes(
        root: JsonFields,
        candidateLookup: Map<String, ConceptGraphCandidate>,
        candidateByConceptId: Map<String, ConceptGraphCandidate>,
    ): List<ConceptGraphNode> {
        val mergedNodes = linkedMapOf<String, ConceptGraphNode>()
        root.objectList("nodes").forEach { item ->
            val candidate = resolveCandidate(
                item = item,
                candidateLookup = candidateLookup,
            ) ?: return@forEach
            val conceptId = candidate.conceptId
            val baseNode = candidateByConceptId.getValue(conceptId).toNode(
                label = item.string("label").ifBlank { candidate.title },
                aliases = candidate.aliases + item.stringList("aliases"),
                summary = item.string("summary").ifBlank { candidate.summary },
                hotnessScore = item.double("hotnessScore") ?: candidate.hotnessScore,
                updatedAt = item.long("updatedAt") ?: candidate.updatedAt,
                sourceIds = candidate.sourceIds + item.stringList("sourceIds"),
            )
            mergedNodes[conceptId] = mergedNodes[conceptId]?.mergeWith(baseNode) ?: baseNode
        }
        return mergedNodes.values.toList()
    }

    private fun parseEdges(
        root: JsonFields,
        nodeKeyLookup: Map<String, String>,
        resolvedNodeIds: Set<String>,
    ): List<ConceptGraphEdge> {
        val edges = linkedMapOf<String, ConceptGraphEdge>()
        root.objectList("edges").forEach { item ->
            val fromConceptId = resolveNodeId(item.string("fromConceptId"), nodeKeyLookup) ?: return@forEach
            val toConceptId = resolveNodeId(item.string("toConceptId"), nodeKeyLookup) ?: return@forEach
            if (fromConceptId == toConceptId) return@forEach
            if (fromConceptId !in resolvedNodeIds || toConceptId !in resolvedNodeIds) return@forEach
            val relationType = ConceptGraphRelationType.fromWireName(
                item.string("relationType"),
            ) ?: return@forEach
            val edge = ConceptGraphEdge(
                fromConceptId = fromConceptId,
                toConceptId = toConceptId,
                relationType = relationType,
                reasonLine = item.string("reasonLine"),
                supportIds = item.stringList("supportIds").distinct(),
                confidence = (item.double("confidence") ?: 0.0).coerceIn(0.0, 1.0),
            )
            edges.putIfAbsent(edge.identityKey(), edge)
        }
        return edges.values.toList()
    }

    private fun resolveCandidate(
        item: JsonFields,
        candidateLookup: Map<String, ConceptGraphCandidate>,
    ): ConceptGraphCandidate? {
        val keys = listOf(
            item.string("conceptId"),
            item.string("label"),
        )
        return keys
            .asSequence()
            .map(::normalizeConceptKey)
            .filter { it.isNotBlank() }
            .mapNotNull(candidateLookup::get)
            .firstOrNull()
    }

    private fun buildCandidateLookup(
        candidates: List<ConceptGraphCandidate>,
    ): Map<String, ConceptGraphCandidate> = buildMap {
        candidates.forEach { candidate ->
            registerCandidateKey(candidate.conceptId, candidate)
            registerCandidateKey(candidate.title, candidate)
            candidate.aliases.forEach { alias ->
                registerCandidateKey(alias, candidate)
            }
        }
    }

    private fun MutableMap<String, ConceptGraphCandidate>.registerCandidateKey(
        raw: String,
        candidate: ConceptGraphCandidate,
    ) {
        val normalized = normalizeConceptKey(raw)
        if (normalized.isBlank() || normalized in this) return
        put(normalized, candidate)
    }

    private fun buildNodeKeyLookup(
        nodes: List<ConceptGraphNode>,
        candidateByConceptId: Map<String, ConceptGraphCandidate>,
    ): Map<String, String> = buildMap {
        nodes.forEach { node ->
            registerNodeKey(node.conceptId, node.conceptId)
            registerNodeKey(node.label, node.conceptId)
            node.aliases.forEach { alias ->
                registerNodeKey(alias, node.conceptId)
            }
            candidateByConceptId[node.conceptId]?.let { candidate ->
                registerNodeKey(candidate.title, node.conceptId)
                candidate.aliases.forEach { alias ->
                    registerNodeKey(alias, node.conceptId)
                }
            }
        }
    }

    private fun MutableMap<String, String>.registerNodeKey(
        raw: String,
        conceptId: String,
    ) {
        val normalized = normalizeConceptKey(raw)
        if (normalized.isBlank() || normalized in this) return
        put(normalized, conceptId)
    }

    private fun resolveNodeId(
        raw: String,
        nodeKeyLookup: Map<String, String>,
    ): String? {
        val normalized = normalizeConceptKey(raw)
        if (normalized.isBlank()) return null
        return nodeKeyLookup[normalized]
    }

    private fun selectDefaultCenterNodeId(
        nodes: List<ConceptGraphNode>,
    ): String = nodes
        .maxWithOrNull(
            compareBy<ConceptGraphNode> { it.hotnessScore }
                .thenBy { it.updatedAt }
                .thenBy { it.conceptId },
        )
        ?.conceptId
        .orEmpty()

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    private fun ConceptGraphCandidate.toNode(
        label: String,
        aliases: List<String>,
        summary: String,
        hotnessScore: Double,
        updatedAt: Long,
        sourceIds: List<String>,
    ): ConceptGraphNode = ConceptGraphNode(
        conceptId = conceptId,
        label = label.ifBlank { title },
        aliases = aliases
            .map { it.trim() }
            .filter { it.isNotBlank() && it != conceptId }
            .distinct(),
        summary = summary,
        hotnessScore = hotnessScore,
        updatedAt = updatedAt,
        sourceIds = sourceIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
    )

    private fun ConceptGraphNode.mergeWith(
        other: ConceptGraphNode,
    ): ConceptGraphNode = copy(
        label = label.ifBlank { other.label },
        aliases = (aliases + other.aliases).distinct(),
        summary = summary.ifBlank { other.summary },
        hotnessScore = maxOf(hotnessScore, other.hotnessScore),
        updatedAt = maxOf(updatedAt, other.updatedAt),
        sourceIds = (sourceIds + other.sourceIds).distinct(),
    )

    private fun ConceptGraphEdge.identityKey(): String =
        listOf(fromConceptId, toConceptId, relationType.wireName).joinToString("|")

    private fun normalizeConceptKey(
        raw: String,
    ): String = raw.trim().lowercase()

    private data class JsonFields(
        val values: Map<String, JsonValue>,
    ) {
        fun string(key: String): String =
            (values[key] as? JsonValue.JsonString)?.value?.trim().orEmpty()

        fun stringList(key: String): List<String> =
            (values[key] as? JsonValue.JsonArray)
                ?.items
                ?.mapNotNull { (it as? JsonValue.JsonString)?.value?.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

        fun objectList(key: String): List<JsonFields> =
            (values[key] as? JsonValue.JsonArray)
                ?.items
                ?.mapNotNull { (it as? JsonValue.JsonObject)?.toFields() }
                .orEmpty()

        fun double(key: String): Double? =
            (values[key] as? JsonValue.JsonNumber)?.value

        fun long(key: String): Long? =
            double(key)?.toLong()?.takeIf { it > 0L }
    }

    private sealed interface JsonValue {
        data class JsonObject(val values: Map<String, JsonValue>) : JsonValue {
            fun toFields(): JsonFields = JsonFields(values)
        }

        data class JsonArray(val items: List<JsonValue>) : JsonValue

        data class JsonString(val value: String) : JsonValue

        data class JsonNumber(val value: Double) : JsonValue

        data class JsonBoolean(val value: Boolean) : JsonValue

        data object JsonNull : JsonValue
    }

    private class MiniJsonParser(
        private val raw: String,
    ) {
        private var index = 0

        fun parseObject(): JsonFields {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(index == raw.length) { "Unexpected trailing JSON content." }
            val jsonObject = value as? JsonValue.JsonObject
                ?: throw IllegalArgumentException("Root JSON value must be an object.")
            return jsonObject.toFields()
        }

        private fun parseValue(): JsonValue {
            skipWhitespace()
            val current = currentChar()
            return when (current) {
                '{' -> parseObjectValue()
                '[' -> parseArrayValue()
                '"' -> JsonValue.JsonString(parseString())
                't' -> {
                    consumeLiteral("true")
                    JsonValue.JsonBoolean(true)
                }
                'f' -> {
                    consumeLiteral("false")
                    JsonValue.JsonBoolean(false)
                }
                'n' -> {
                    consumeLiteral("null")
                    JsonValue.JsonNull
                }
                else -> {
                    if (current == '-' || current.isDigit()) {
                        JsonValue.JsonNumber(parseNumber())
                    } else {
                        throw IllegalArgumentException("Unexpected JSON token at index $index.")
                    }
                }
            }
        }

        private fun parseObjectValue(): JsonValue.JsonObject {
            expect('{')
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (peek('}')) {
                index++
                return JsonValue.JsonObject(values)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                values[key] = value
                skipWhitespace()
                when {
                    peek('}') -> {
                        index++
                        return JsonValue.JsonObject(values)
                    }

                    peek(',') -> index++
                    else -> throw IllegalArgumentException("Expected ',' or '}' at index $index.")
                }
            }
        }

        private fun parseArrayValue(): JsonValue.JsonArray {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (peek(']')) {
                index++
                return JsonValue.JsonArray(items)
            }
            while (true) {
                items += parseValue()
                skipWhitespace()
                when {
                    peek(']') -> {
                        index++
                        return JsonValue.JsonArray(items)
                    }

                    peek(',') -> index++
                    else -> throw IllegalArgumentException("Expected ',' or ']' at index $index.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < raw.length) {
                val current = raw[index++]
                when (current) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> builder.append(current)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string.")
        }

        private fun parseEscape(): Char {
            val escaped = currentChar()
            index++
            return when (escaped) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(index + 4 <= raw.length) { "Invalid unicode escape." }
                    val hex = raw.substring(index, index + 4)
                    index += 4
                    hex.toInt(16).toChar()
                }

                else -> throw IllegalArgumentException("Invalid escape sequence at index $index.")
            }
        }

        private fun parseNumber(): Double {
            val start = index
            if (peek('-')) index++
            consumeDigits()
            if (peek('.')) {
                index++
                consumeDigits()
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                consumeDigits()
            }
            return raw.substring(start, index).toDouble()
        }

        private fun consumeDigits() {
            val start = index
            while (index < raw.length && raw[index].isDigit()) {
                index++
            }
            require(index > start) { "Expected digits at index $index." }
        }

        private fun consumeLiteral(
            literal: String,
        ) {
            require(raw.regionMatches(index, literal, 0, literal.length)) {
                "Expected '$literal' at index $index."
            }
            index += literal.length
        }

        private fun skipWhitespace() {
            while (index < raw.length && raw[index].isWhitespace()) {
                index++
            }
        }

        private fun expect(
            expected: Char,
        ) {
            require(currentChar() == expected) { "Expected '$expected' at index $index." }
            index++
        }

        private fun currentChar(): Char {
            require(index < raw.length) { "Unexpected end of JSON input." }
            return raw[index]
        }

        private fun peek(
            expected: Char,
        ): Boolean = index < raw.length && raw[index] == expected
    }
}
