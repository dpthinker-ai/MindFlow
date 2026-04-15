package com.mindflow.app.data.wiki

import android.content.Context
import com.mindflow.app.data.connect.DirectionAssetAnalyzer
import com.mindflow.app.data.connect.DirectionContinuityAnalyzer
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.DirectionStageHistoryAnalyzer
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.NoteInsightSummaryExtractor
import com.mindflow.app.data.connect.ResearchGroundingSnapshot
import com.mindflow.app.data.connect.ResearchEvidenceAnalyzer
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.connect.ThreadWeeklyReviewAnalyzer
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun buildGraphConceptSearchItems(
    conceptBuckets: Map<String, List<Pair<DirectionWikiDirectionSummary, NoteEntity>>>,
): List<KnowledgeLayerSearchItem> =
    conceptBuckets
        .flatMap { (tag, pairs) ->
            val directionCount = pairs.map { it.first.threadKey }.distinct().size
            pairs
                .groupBy { it.first.threadKey }
                .mapNotNull { (threadKey, threadPairs) ->
                    val latestPair = threadPairs.maxByOrNull { it.second.updatedAt } ?: return@mapNotNull null
                    KnowledgeLayerSearchItem(
                        id = "concept:${threadKey}:${graphSearchItemSlug(tag)}",
                        type = KnowledgeLayerSearchType.CONCEPT,
                        title = tag,
                        summary = NoteInsightSummaryExtractor.extract(latestPair.second).ifBlank {
                            latestPair.second.content.trim().take(90)
                        },
                        supportLine = "${threadPairs.size} 条记录 · ${directionCount} 条方向",
                        trustLabel = ResearchEvidenceAnalyzer.classify(latestPair.second).label,
                        threadKey = threadKey,
                        noteId = latestPair.second.id,
                        updatedAt = latestPair.second.updatedAt,
                    )
                }
        }
        .distinctBy { it.id }
        .sortedByDescending { it.updatedAt }

internal fun buildGraphObjectSearchItems(
    candidates: List<KnowledgeObjectCandidate>,
): List<KnowledgeLayerSearchItem> =
    candidates
        .groupBy { it.type to it.title.trim() }
        .flatMap { (_, titleBucket) ->
            val directionCount = titleBucket.map { it.threadKey }.distinct().size
            titleBucket
                .groupBy { it.threadKey }
                .mapNotNull { (threadKey, threadBucket) ->
                    val first = threadBucket.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                    KnowledgeLayerSearchItem(
                        id = "${first.type.folderName}:${threadKey}:${graphSearchItemSlug(first.title)}",
                        type = first.type.toKnowledgeSearchType(),
                        title = first.title,
                        summary = first.summary,
                        supportLine = "${threadBucket.size} 条来源 · ${directionCount} 条方向",
                        trustLabel = first.evidenceType.label,
                        threadKey = threadKey,
                        noteId = first.noteId,
                        updatedAt = first.updatedAt,
                    )
                }
        }
        .distinctBy { it.id }
        .sortedByDescending { it.updatedAt }

internal fun buildConceptGraphCandidates(
    conceptBuckets: Map<String, List<Pair<DirectionWikiDirectionSummary, NoteEntity>>>,
    objectCandidates: List<KnowledgeObjectCandidate>,
): List<ConceptGraphCandidate> {
    val merged = linkedMapOf<String, MutableConceptGraphCandidate>()

    fun mergeCandidate(
        title: String,
        summary: String,
        updatedAt: Long,
        sourceIds: List<String>,
        hotnessContribution: Double,
    ) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        val identityKey = stableConceptIdentityKey(normalizedTitle)
        val conceptId = stableConceptId(identityKey)
        val existing = merged[identityKey]
        if (existing == null) {
            merged[identityKey] = MutableConceptGraphCandidate(
                conceptId = conceptId,
                title = normalizedTitle,
                summary = summary.trim(),
                hotnessScore = hotnessContribution,
                updatedAt = updatedAt,
                sourceIds = linkedSetOf<String>().apply { addAll(sourceIds) },
            )
            return
        }

        existing.aliases += normalizedTitle
        existing.sourceIds += sourceIds
        existing.hotnessScore += hotnessContribution
        if (
            updatedAt > existing.updatedAt ||
            (updatedAt == existing.updatedAt && existing.summary.isBlank() && summary.isNotBlank())
        ) {
            existing.title = normalizedTitle
            existing.summary = summary.trim()
            existing.updatedAt = updatedAt
        }
    }

    conceptBuckets.forEach { (concept, pairs) ->
        val latest = pairs.maxByOrNull { it.second.updatedAt } ?: return@forEach
        val directionCount = pairs.map { it.first.threadKey }.distinct().size
        mergeCandidate(
            title = concept,
            summary = NoteInsightSummaryExtractor.extract(latest.second).ifBlank {
                latest.second.content.trim().take(90)
            },
            updatedAt = latest.second.updatedAt,
            sourceIds = pairs.map { "note:${it.second.id}" }.distinct(),
            hotnessContribution = pairs.size.toDouble() + directionCount.toDouble(),
        )
    }

    objectCandidates.forEach { candidate ->
        candidate.relatedConcepts
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { concept ->
                mergeCandidate(
                    title = concept,
                    summary = candidate.summary,
                    updatedAt = candidate.updatedAt,
                    sourceIds = listOf("${candidate.type.folderName}:${candidate.noteId}"),
                    hotnessContribution = 1.0,
                )
            }
    }

    return merged.values
        .map { candidate ->
            ConceptGraphCandidate(
                conceptId = candidate.conceptId,
                title = candidate.title,
                aliases = candidate.aliases
                    .filter { it != candidate.title }
                    .distinct()
                    .sorted(),
                summary = candidate.summary,
                hotnessScore = candidate.hotnessScore,
                updatedAt = candidate.updatedAt,
                sourceIds = candidate.sourceIds.toList(),
            )
        }
        .sortedWith(
            compareByDescending<ConceptGraphCandidate> { it.hotnessScore }
                .thenByDescending { it.updatedAt }
                .thenBy { it.title },
        )
}

internal fun ConceptGraphSnapshot.toConceptGraphJsonString(): String =
    renderConceptGraphJsonObject(
        linkedMapOf(
            "version" to version,
            "defaultCenterNodeId" to defaultCenterNodeId,
            "source" to source,
            "generatedAt" to generatedAt,
            "nodes" to nodes.map { node ->
                linkedMapOf(
                    "conceptId" to node.conceptId,
                    "label" to node.label,
                    "aliases" to node.aliases,
                    "summary" to node.summary,
                    "hotnessScore" to node.hotnessScore,
                    "updatedAt" to node.updatedAt,
                    "sourceIds" to node.sourceIds,
                )
            },
            "edges" to edges.map { edge ->
                linkedMapOf(
                    "fromConceptId" to edge.fromConceptId,
                    "toConceptId" to edge.toConceptId,
                    "relationType" to edge.relationType.wireName,
                    "reasonLine" to edge.reasonLine,
                    "supportIds" to edge.supportIds,
                    "confidence" to edge.confidence,
                )
            },
        ),
    )

internal fun parseConceptGraphSnapshotOrDefault(
    raw: String?,
): ConceptGraphSnapshot =
    raw
        ?.takeIf { it.isNotBlank() }
        ?.let { json ->
            runCatching { json.toConceptGraphSnapshot() }.getOrDefault(ConceptGraphSnapshot())
        }
        ?: ConceptGraphSnapshot()

internal fun String.toConceptGraphSnapshot(): ConceptGraphSnapshot {
    val root = ConceptGraphMiniJsonParser(this).parseObject()
    val nodes = buildList {
        root.objectList("nodes").forEach { item ->
            val conceptId = item.string("conceptId")
            val label = item.string("label")
            if (conceptId.isBlank() || label.isBlank()) return@forEach
            add(
                ConceptGraphNode(
                    conceptId = conceptId,
                    label = label,
                    aliases = item.stringList("aliases"),
                    summary = item.string("summary"),
                    hotnessScore = item.double("hotnessScore") ?: 0.0,
                    updatedAt = item.long("updatedAt") ?: 0L,
                    sourceIds = item.stringList("sourceIds"),
                ),
            )
        }
    }
    val nodeIds = nodes.map { it.conceptId }.toSet()
    val edges = buildList {
        root.objectList("edges").forEach { item ->
            val fromConceptId = item.string("fromConceptId")
            val toConceptId = item.string("toConceptId")
            if (
                fromConceptId.isBlank() ||
                toConceptId.isBlank() ||
                fromConceptId == toConceptId ||
                fromConceptId !in nodeIds ||
                toConceptId !in nodeIds
            ) {
                return@forEach
            }
            add(
                ConceptGraphEdge(
                    fromConceptId = fromConceptId,
                    toConceptId = toConceptId,
                    relationType = ConceptGraphRelationType.fromWireName(item.string("relationType"))
                        ?: ConceptGraphRelationType.REFERENCES,
                    reasonLine = item.string("reasonLine"),
                    supportIds = item.stringList("supportIds"),
                    confidence = item.double("confidence") ?: 0.0,
                ),
            )
        }
    }

    return ConceptGraphSnapshot(
        version = root.int("version")?.takeIf { it > 0 } ?: 1,
        defaultCenterNodeId = root.string("defaultCenterNodeId"),
        nodes = nodes,
        edges = edges,
        source = root.string("source").ifBlank { "rule" },
        generatedAt = root.long("generatedAt") ?: 0L,
    )
}

private data class MutableConceptGraphCandidate(
    val conceptId: String,
    var title: String,
    val aliases: MutableSet<String> = linkedSetOf(),
    var summary: String,
    var hotnessScore: Double,
    var updatedAt: Long,
    val sourceIds: MutableSet<String>,
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun stableConceptIdentityKey(
    raw: String,
): String = raw
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")

private fun stableConceptId(
    identityKey: String,
): String = "concept:${identityKey.encodeToByteArray().joinToString("") { "%02x".format(it) }}"

private fun renderConceptGraphJsonObject(
    fields: Map<String, Any?>,
): String = fields.entries.joinToString(
    prefix = "{",
    postfix = "}",
) { (key, value) ->
    "${renderConceptGraphJsonString(key)}:${renderConceptGraphJsonValue(value)}"
}

private fun renderConceptGraphJsonArray(
    values: List<*>,
): String = values.joinToString(
    prefix = "[",
    postfix = "]",
) { value ->
    renderConceptGraphJsonValue(value)
}

private fun renderConceptGraphJsonValue(
    value: Any?,
): String = when (value) {
    null -> "null"
    is String -> renderConceptGraphJsonString(value)
    is Number -> value.toString()
    is Boolean -> value.toString()
    is List<*> -> renderConceptGraphJsonArray(value)
    is Map<*, *> -> renderConceptGraphJsonObject(
        value.entries.associate { (key, nestedValue) ->
            key.toString() to nestedValue
        },
    )
    else -> renderConceptGraphJsonString(value.toString())
}

private fun renderConceptGraphJsonString(
    value: String,
): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private data class ConceptGraphJsonFields(
    val values: Map<String, ConceptGraphJsonValue>,
) {
    fun string(key: String): String =
        (values[key] as? ConceptGraphJsonValue.JsonString)?.value?.trim().orEmpty()

    fun stringList(key: String): List<String> =
        (values[key] as? ConceptGraphJsonValue.JsonArray)
            ?.items
            ?.mapNotNull { (it as? ConceptGraphJsonValue.JsonString)?.value?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun objectList(key: String): List<ConceptGraphJsonFields> =
        (values[key] as? ConceptGraphJsonValue.JsonArray)
            ?.items
            ?.mapNotNull { (it as? ConceptGraphJsonValue.JsonObject)?.toFields() }
            .orEmpty()

    fun double(key: String): Double? =
        (values[key] as? ConceptGraphJsonValue.JsonNumber)?.value

    fun long(key: String): Long? =
        double(key)?.toLong()

    fun int(key: String): Int? =
        double(key)?.toInt()
}

private sealed interface ConceptGraphJsonValue {
    data class JsonObject(val values: Map<String, ConceptGraphJsonValue>) : ConceptGraphJsonValue {
        fun toFields(): ConceptGraphJsonFields = ConceptGraphJsonFields(values)
    }

    data class JsonArray(val items: List<ConceptGraphJsonValue>) : ConceptGraphJsonValue

    data class JsonString(val value: String) : ConceptGraphJsonValue

    data class JsonNumber(val value: Double) : ConceptGraphJsonValue

    data class JsonBoolean(val value: Boolean) : ConceptGraphJsonValue

    data object JsonNull : ConceptGraphJsonValue
}

private class ConceptGraphMiniJsonParser(
    private val raw: String,
) {
    private var index = 0

    fun parseObject(): ConceptGraphJsonFields {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == raw.length) { "Unexpected trailing JSON content." }
        val jsonObject = value as? ConceptGraphJsonValue.JsonObject
            ?: throw IllegalArgumentException("Root JSON value must be an object.")
        return jsonObject.toFields()
    }

    private fun parseValue(): ConceptGraphJsonValue {
        skipWhitespace()
        return when (val current = currentChar()) {
            '{' -> parseObjectValue()
            '[' -> parseArrayValue()
            '"' -> ConceptGraphJsonValue.JsonString(parseString())
            't' -> {
                consumeLiteral("true")
                ConceptGraphJsonValue.JsonBoolean(true)
            }
            'f' -> {
                consumeLiteral("false")
                ConceptGraphJsonValue.JsonBoolean(false)
            }
            'n' -> {
                consumeLiteral("null")
                ConceptGraphJsonValue.JsonNull
            }
            '-', in '0'..'9' -> ConceptGraphJsonValue.JsonNumber(parseNumber())
            else -> throw IllegalArgumentException("Unexpected JSON token '$current' at index $index.")
        }
    }

    private fun parseObjectValue(): ConceptGraphJsonValue.JsonObject {
        expect('{')
        skipWhitespace()
        if (peekChar() == '}') {
            index++
            return ConceptGraphJsonValue.JsonObject(emptyMap())
        }
        val values = linkedMapOf<String, ConceptGraphJsonValue>()
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            values[key] = value
            skipWhitespace()
            when (peekChar()) {
                ',' -> index++
                '}' -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' at index $index.")
            }
        }
        return ConceptGraphJsonValue.JsonObject(values)
    }

    private fun parseArrayValue(): ConceptGraphJsonValue.JsonArray {
        expect('[')
        skipWhitespace()
        if (peekChar() == ']') {
            index++
            return ConceptGraphJsonValue.JsonArray(emptyList())
        }
        val items = mutableListOf<ConceptGraphJsonValue>()
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (peekChar()) {
                ',' -> index++
                ']' -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' at index $index.")
            }
        }
        return ConceptGraphJsonValue.JsonArray(items)
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            require(index < raw.length) { "Unterminated JSON string." }
            when (val current = raw[index++]) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(current)
            }
        }
    }

    private fun parseEscape(): Char {
        require(index < raw.length) { "Unterminated JSON escape." }
        return when (val current = raw[index++]) {
            '"', '\\', '/' -> current
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
            else -> throw IllegalArgumentException("Unsupported escape '$current' at index $index.")
        }
    }

    private fun parseNumber(): Double {
        val start = index
        if (peekChar() == '-') index++
        consumeDigits()
        if (peekChar() == '.') {
            index++
            consumeDigits()
        }
        if (peekChar() == 'e' || peekChar() == 'E') {
            index++
            if (peekChar() == '+' || peekChar() == '-') index++
            consumeDigits()
        }
        return raw.substring(start, index).toDouble()
    }

    private fun consumeDigits() {
        require(peekChar().isDigit()) { "Expected digit at index $index." }
        while (peekChar().isDigit()) {
            index++
        }
    }

    private fun consumeLiteral(
        literal: String,
    ) {
        require(raw.startsWith(literal, index)) { "Expected '$literal' at index $index." }
        index += literal.length
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

    private fun peekChar(): Char =
        raw.getOrNull(index) ?: '\u0000'

    private fun skipWhitespace() {
        while (index < raw.length && raw[index].isWhitespace()) {
            index++
        }
    }
}

private fun graphSearchItemSlug(
    title: String,
): String {
    val base = title
        .lowercase()
        .replace("#", "")
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
        .trim('-')
    return if (base.isNotBlank()) base else "item"
}

private fun KnowledgeObjectType.toKnowledgeSearchType(): KnowledgeLayerSearchType = when (this) {
    KnowledgeObjectType.QUESTION -> KnowledgeLayerSearchType.QUESTION
    KnowledgeObjectType.METHOD -> KnowledgeLayerSearchType.METHOD
    KnowledgeObjectType.EXPERIMENT -> KnowledgeLayerSearchType.EXPERIMENT
}

class DirectionWikiCoordinator(
    context: Context,
    private val noteRepository: NoteRepository,
    private val threadPreferencesRepository: ThreadPreferencesRepository,
    private val threadExecutionPlanner: ThreadExecutionPlanner,
    private val externalResearchPlanner: ExternalResearchPlanner,
    private val knowledgeGraphPlanner: KnowledgeGraphPlanner,
    private val conceptGraphPlanner: ConceptGraphPlanner,
    private val applicationScope: CoroutineScope,
) {
    private val legacyRootDir = File(context.filesDir, "direction-wiki")
    private val rootDir = File(context.filesDir, "knowledge-layer")
    private val refreshIntervalMs = 18L * 60L * 60L * 1000L
    private val maxGeneratedDirections = 12
    private val _snapshot = MutableStateFlow(run {
        migrateLegacyRootIfNeeded()
        loadSnapshotFromDisk()
    })
    val snapshot: StateFlow<DirectionWikiSnapshot> = _snapshot

    fun refreshInBackgroundIfNeeded() {
        applicationScope.launch {
            val now = System.currentTimeMillis()
            val current = _snapshot.value
            val allNotes = noteRepository.observeAllNotes().first()
            val activeNotes = allNotes.filter { !it.isArchived }
            val followed = threadPreferencesRepository.getCurrent().followedThreadKeys.sorted()
            val candidateThreadKeys = buildCandidateThreadKeys(activeNotes, followed)
            val isFreshEnough = current.lastGeneratedAt > 0L && now - current.lastGeneratedAt < refreshIntervalMs
            val hasCandidateCoverage = candidateThreadKeys.all { it in current.directions.keys }
            if (isFreshEnough && hasCandidateCoverage) return@launch
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow(): DirectionWikiRefreshResult = withContext(Dispatchers.IO) {
        val allNotes = noteRepository.observeAllNotes().first()
        val activeNotes = allNotes.filter { !it.isArchived }
        val followed = threadPreferencesRepository.getCurrent().followedThreadKeys.sorted()
        ensureDirectories()

        val generatedAt = System.currentTimeMillis()
        val candidateThreadKeys = buildCandidateThreadKeys(activeNotes, followed)
        val summaries = candidateThreadKeys.mapNotNull { threadKey ->
            buildDirectionSummary(
                threadKey = threadKey,
                notes = NoteConnectionAnalyzer.notesForThread(threadKey, activeNotes),
            )
        }

        writeDirectionFiles(generatedAt, summaries, activeNotes)
        val snapshot = loadSnapshotFromDisk()
        _snapshot.value = snapshot
        DirectionWikiRefreshResult(
            generatedDirectionCount = summaries.size,
            generatedAt = generatedAt,
        )
    }

    private fun buildCandidateThreadKeys(
        activeNotes: List<NoteEntity>,
        followed: List<String>,
    ): List<String> {
        val discovered = NoteConnectionAnalyzer.buildThemeThreads(
            notes = activeNotes,
            limit = maxGeneratedDirections,
        ).map { it.key }
        return (followed + discovered)
            .distinct()
            .take(maxGeneratedDirections)
    }

    private suspend fun buildDirectionSummary(
        threadKey: String,
        notes: List<NoteEntity>,
    ): DirectionWikiDirectionSummary? {
        if (notes.isEmpty()) return null

        val thread = NoteConnectionAnalyzer.threadFromKey(threadKey, notes)
        val execution = threadExecutionPlanner.summarize(threadKey, notes)
        val research = externalResearchPlanner.summarize(threadKey, notes)
        val directionAssets = DirectionAssetAnalyzer.build(notes)
        val stageHistory = DirectionStageHistoryAnalyzer.build(notes)
        val continuity = DirectionContinuityAnalyzer.summarize(notes)
        val grounding = ResearchEvidenceAnalyzer.buildGrounding(notes)
        val weeklyReview = ThreadWeeklyReviewAnalyzer.build(notes)
        val knowledgeObjects = notes.flatMap { note ->
            KnowledgeObjectClassifier.classify(
                note = note,
                threadKey = threadKey,
                threadTitle = thread.title,
            )
        }
        val questionCount = knowledgeObjects.count { it.type == KnowledgeObjectType.QUESTION }
        val methodCount = knowledgeObjects.count { it.type == KnowledgeObjectType.METHOD }
        val experimentCount = knowledgeObjects.count { it.type == KnowledgeObjectType.EXPERIMENT }
        val slug = slugFor(thread.title, threadKey)
        val snapshotHistory = DirectionSnapshotHistoryAnalyzer.summarize(
            snapshotsDir = File(rootDir, "wiki/snapshots"),
            slug = slug,
            currentStage = execution.stage,
            currentTimestamp = System.currentTimeMillis(),
        )

        val verifiedPoints = grounding.verifiedItems.map { it.summary }
        val validatedPoints = grounding.validatedItems.map { it.summary }
        val signalPoints = grounding.signalItems.map { it.summary }
        val hypothesisPoints = grounding.hypothesisItems.map { it.summary }

        val openQuestions = buildList {
            research.contrarianQuestion.takeIf { it.isNotBlank() }?.let(::add)
            hypothesisPoints.forEach(::add)
            execution.blocker.takeIf { it.isNotBlank() }?.let(::add)
            if (verifiedPoints.isEmpty() && validatedPoints.isEmpty()) {
                add("还需要一条更硬的查证或验证结果，才能把这个方向真正压实。")
            }
        }.distinct().take(3)

        val stageHistorySummary = stageHistory
            .joinToString(" -> ") { "${it.label}${it.stage.label}" }

        val assetSummary = validatedPoints.firstOrNull()
            ?: verifiedPoints.firstOrNull()
            ?: directionAssets.firstOrNull()?.summary
            ?: execution.summary.ifBlank { research.outsideAngle }
        val conclusionLine = buildConclusionLine(
            assetSummary = assetSummary,
            weeklyMainLine = weeklyReview.mainLine,
            validatedPoint = validatedPoints.firstOrNull().orEmpty(),
            verifiedPoint = verifiedPoints.firstOrNull().orEmpty(),
        )
        val nextShiftLine = execution.postValidationAction
            .ifBlank { execution.nextStep }
            .ifBlank { weeklyReview.lines.getOrNull(1).orEmpty() }
        val trustLine = buildTrustLine(
            signalPoints = signalPoints,
            hypothesisPoints = hypothesisPoints,
            verifiedPoints = verifiedPoints,
            validatedPoints = validatedPoints,
        )
        val lintSummary = KnowledgeLayerLintAnalyzer.summarize(
            stage = execution.stage,
            notes = notes,
            grounding = grounding,
            execution = execution,
            conclusionLine = conclusionLine,
            nextShiftLine = nextShiftLine,
            questionCount = questionCount,
            methodCount = methodCount,
            experimentCount = experimentCount,
        )
        val knowledgeObjectLine = buildKnowledgeObjectLine(
            questionCount = questionCount,
            methodCount = methodCount,
            experimentCount = experimentCount,
        )
        val maintenanceFocusLine = buildMaintenanceFocusLine(
            summaryTitle = thread.title,
            stage = execution.stage,
            conclusionLine = conclusionLine,
            nextShiftLine = nextShiftLine,
            healthLine = lintSummary.healthLine,
            maintenanceTargetLine = lintSummary.maintenanceTargetLine,
            questionObjects = knowledgeObjects.filter { it.type == KnowledgeObjectType.QUESTION },
            methodObjects = knowledgeObjects.filter { it.type == KnowledgeObjectType.METHOD },
            experimentObjects = knowledgeObjects.filter { it.type == KnowledgeObjectType.EXPERIMENT },
            openQuestions = openQuestions,
            daysSinceUpdate = java.util.concurrent.TimeUnit.MILLISECONDS
                .toDays(System.currentTimeMillis() - (notes.maxOfOrNull { it.updatedAt } ?: 0L))
                .coerceAtLeast(0),
        )

        return DirectionWikiDirectionSummary(
            threadKey = threadKey,
            slug = slug,
            title = thread.title,
            stage = execution.stage,
            assetSummary = assetSummary,
            conclusionLine = conclusionLine,
            nextShiftLine = nextShiftLine,
            groundingLine = grounding.summary.summaryLine,
            trustLine = trustLine,
            knowledgeObjectLine = knowledgeObjectLine,
            healthLine = lintSummary.healthLine,
            maintenanceLine = lintSummary.maintenanceLine,
            maintenanceTargetLine = lintSummary.maintenanceTargetLine,
            maintenanceSourceLine = lintSummary.maintenanceSourceLine,
            maintenanceDimensionLine = lintSummary.maintenanceDimensionLine,
            maintenanceFocusLine = maintenanceFocusLine,
            signalPoints = signalPoints,
            hypothesisPoints = hypothesisPoints,
            verifiedPoints = verifiedPoints,
            validatedPoints = validatedPoints,
            openQuestions = openQuestions,
            lintIssues = lintSummary.lintIssues,
            continuityLine = continuity.continuityLine,
            trajectoryLine = continuity.trajectoryLine,
            stageHistorySummary = stageHistorySummary,
            snapshotStageLine = snapshotHistory.snapshotStageLine,
            snapshotCadenceLine = snapshotHistory.snapshotCadenceLine,
            updatedAt = notes.maxOfOrNull { it.updatedAt } ?: 0L,
        )
    }

    private suspend fun writeDirectionFiles(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        allNotes: List<NoteEntity>,
    ) {
        val rawNotesDir = File(rootDir, "raw/notes")
        val rawResearchDir = File(rootDir, "raw/research")
        val rawValidationDir = File(rootDir, "raw/validations")
        val rawReflectionDir = File(rootDir, "raw/reflections")
        val rawReviewsDir = File(rootDir, "raw/reviews")
        val directionsDir = File(rootDir, "wiki/directions")
        val conclusionsDir = File(rootDir, "wiki/conclusions")
        val evidenceDir = File(rootDir, "wiki/evidence")
        val snapshotsDir = File(rootDir, "wiki/snapshots")
        val lintDir = File(rootDir, "wiki/lint")
        val conceptsDir = File(rootDir, "wiki/concepts")
        val questionsDir = File(rootDir, "wiki/questions")
        val methodsDir = File(rootDir, "wiki/methods")
        val experimentsDir = File(rootDir, "wiki/experiments")
        val timestamp = fileTimestamp(generatedAt)
        val rawIndexLines = mutableListOf<String>()
        val objectCandidates = mutableListOf<KnowledgeObjectCandidate>()
        val conceptBuckets = buildConceptBuckets(
            summaries = summaries,
            allNotes = allNotes,
        )

        summaries.forEach { summary ->
            val notes = NoteConnectionAnalyzer.notesForThread(summary.threadKey, allNotes)
            val researchNotes = notes.filter {
                ResearchEvidenceAnalyzer.classify(it) in setOf(
                    ResearchEvidenceType.SIGNAL,
                    ResearchEvidenceType.HYPOTHESIS,
                    ResearchEvidenceType.VERIFIED,
                    ResearchEvidenceType.VALIDATED,
                )
            }
            val validationNotes = notes.filter {
                val type = ResearchEvidenceAnalyzer.classify(it)
                type == ResearchEvidenceType.HYPOTHESIS || type == ResearchEvidenceType.VALIDATED
            }
            val reflectionNotes = notes.filter { note ->
                note.status == com.mindflow.app.data.model.NoteStatus.DONE ||
                    note.content.contains("回看") ||
                    note.content.contains("复盘") ||
                    note.content.contains("经验") ||
                    note.content.contains("教训")
            }
            val execution = threadExecutionPlanner.summarize(summary.threadKey, notes)
            val research = externalResearchPlanner.summarize(summary.threadKey, notes)
            val grounding = ResearchEvidenceAnalyzer.buildGrounding(researchNotes)
            val weeklyReview = ThreadWeeklyReviewAnalyzer.build(notes)
            val relatedConcepts = buildDirectionRelatedConcepts(notes)
            val directionObjectCandidates = buildList {
                notes.forEach { note ->
                    addAll(
                        KnowledgeObjectClassifier.classify(
                            note = note,
                            threadKey = summary.threadKey,
                            threadTitle = summary.title,
                        ),
                    )
                }
                addAll(
                    buildSynthesizedKnowledgeObjects(
                        summary = summary,
                        execution = execution,
                        research = research,
                        relatedConcepts = relatedConcepts,
                    ),
                )
            }

            File(rawNotesDir, "${summary.slug}-$timestamp.md").writeText(buildRawNotesMarkdown(summary, notes))
            if (researchNotes.isNotEmpty()) {
                File(rawResearchDir, "${summary.slug}-$timestamp.md").writeText(buildRawResearchMarkdown(summary, researchNotes))
            }
            if (validationNotes.isNotEmpty()) {
                File(rawValidationDir, "${summary.slug}-$timestamp.md").writeText(buildRawValidationMarkdown(summary, validationNotes))
            }
            if (reflectionNotes.isNotEmpty()) {
                File(rawReflectionDir, "${summary.slug}-$timestamp.md").writeText(buildRawReflectionMarkdown(summary, reflectionNotes))
            }
            File(rawReviewsDir, "${summary.slug}-$timestamp.md").writeText(
                buildRawReviewMarkdown(
                    summary = summary,
                    execution = execution,
                    weeklyReview = weeklyReview,
                ),
            )
            File(directionsDir, "${summary.slug}.md").writeText(buildDirectionMarkdown(summary, execution, research))
            File(conclusionsDir, "${summary.slug}.md").writeText(
                buildConclusionMarkdown(
                    summary = summary,
                    weeklyReview = weeklyReview,
                    relatedConcepts = relatedConcepts,
                    relatedObjects = directionObjectCandidates,
                ),
            )
            File(evidenceDir, "${summary.slug}.md").writeText(
                buildEvidenceMarkdown(
                    summary = summary,
                    grounding = grounding,
                    relatedConcepts = relatedConcepts,
                    relatedObjects = directionObjectCandidates,
                ),
            )
            File(lintDir, "${summary.slug}.md").writeText(buildLintMarkdown(summary))
            File(snapshotsDir, "${summary.slug}-$timestamp.md").writeText(buildSnapshotMarkdown(summary, execution))
            val snapshotHistory = DirectionSnapshotHistoryAnalyzer.summarize(
                snapshotsDir = snapshotsDir,
                slug = summary.slug,
                currentStage = summary.stage,
                currentTimestamp = generatedAt,
            )
            File(snapshotsDir, "${summary.slug}-timeline.md").writeText(
                buildSnapshotTimelineMarkdown(summary, snapshotHistory),
            )
            rawIndexLines += buildList {
                add("- [${summary.title} raw notes](notes/${summary.slug}-$timestamp.md)")
                if (researchNotes.isNotEmpty()) add("- [${summary.title} raw research](research/${summary.slug}-$timestamp.md)")
                if (validationNotes.isNotEmpty()) add("- [${summary.title} validations](validations/${summary.slug}-$timestamp.md)")
                if (reflectionNotes.isNotEmpty()) add("- [${summary.title} reflections](reflections/${summary.slug}-$timestamp.md)")
                add("- [${summary.title} reviews](reviews/${summary.slug}-$timestamp.md)")
            }
            objectCandidates += directionObjectCandidates
        }

        File(rootDir, "raw/index.md").writeText(
            buildString {
                appendLine("# Knowledge Layer Raw Sources")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (rawIndexLines.isEmpty()) {
                    appendLine("还没有导出的 source。")
                } else {
                    rawIndexLines.forEach(::appendLine)
                }
            },
        )

        writeConceptFiles(
            generatedAt = generatedAt,
            conceptBuckets = conceptBuckets,
            conceptsDir = conceptsDir,
            candidates = objectCandidates,
        )
        writeKnowledgeObjectFiles(
            generatedAt = generatedAt,
            candidates = objectCandidates,
            directories = mapOf(
                KnowledgeObjectType.QUESTION to questionsDir,
                KnowledgeObjectType.METHOD to methodsDir,
                KnowledgeObjectType.EXPERIMENT to experimentsDir,
            ),
        )
        writeLintFiles(
            generatedAt = generatedAt,
            summaries = summaries,
            lintDir = lintDir,
        )
        writeEvidenceIndex(
            generatedAt = generatedAt,
            summaries = summaries,
            evidenceDir = evidenceDir,
        )
        writeConclusionIndex(
            generatedAt = generatedAt,
            summaries = summaries,
            conclusionsDir = conclusionsDir,
        )

        File(rootDir, "wiki/index.md").writeText(
            buildString {
                appendLine("# MindFlow Knowledge Layer")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                summaries.sortedBy { it.title }.forEach { summary ->
                    appendLine("- [${summary.title}](directions/${summary.slug}.md) · ${summary.stage.label}")
                }
                appendLine()
                appendLine("- [结论索引](conclusions/index.md)")
                appendLine("- [概念索引](concepts/index.md)")
                appendLine("- [证据索引](evidence/index.md)")
                appendLine("- [问题索引](questions/index.md)")
                appendLine("- [方法索引](methods/index.md)")
                appendLine("- [实验索引](experiments/index.md)")
                appendLine("- [知识健康检查](lint/index.md)")
            },
        )

        File(rootDir, "wiki/log.md").appendText(
            buildString {
                appendLine("## [${displayDate(generatedAt)}] update | ${summaries.size} directions")
                summaries.forEach { summary ->
                    appendLine("- ${summary.title} · ${summary.stage.label}")
                }
                appendLine()
            },
        )

        val knowledgeItems = buildKnowledgeSearchItems(
            summaries = summaries,
            conceptBuckets = conceptBuckets,
            candidates = objectCandidates,
        )
        val threadNoteCounts = summaries.associate { summary ->
            summary.threadKey to NoteConnectionAnalyzer.notesForThread(summary.threadKey, allNotes).size
        }
        val conceptGraph = conceptGraphPlanner.summarize(
            candidates = buildConceptGraphCandidates(
                conceptBuckets = conceptBuckets,
                objectCandidates = objectCandidates,
            ),
        )
        val graphSnapshot = knowledgeGraphPlanner.summarize(
            summaries = summaries,
            knowledgeItems = knowledgeItems,
            threadNoteCounts = threadNoteCounts,
        )
        File(rootDir, "wiki/graph.md").writeText(
            buildGraphMarkdown(
                generatedAt = generatedAt,
                graph = graphSnapshot,
            ),
        )

        writeExport(
            generatedAt = generatedAt,
            summaries = summaries,
            knowledgeItems = knowledgeItems,
            conceptGraph = conceptGraph,
            graph = graphSnapshot,
        )
    }

    private fun writeConceptFiles(
        generatedAt: Long,
        conceptBuckets: Map<String, List<Pair<DirectionWikiDirectionSummary, NoteEntity>>>,
        conceptsDir: File,
        candidates: List<KnowledgeObjectCandidate>,
    ) {
        File(conceptsDir, "index.md").writeText(
            buildString {
                appendLine("# Concepts")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (conceptBuckets.isEmpty()) {
                    appendLine("还没有形成跨方向复用的概念。")
                } else {
                    conceptBuckets.forEach { (tag, pairs) ->
                        val slug = slugFor(tag, tag)
                        val directionCount = pairs.map { it.first.threadKey }.distinct().size
                        appendLine("- [$tag]($slug.md) · ${pairs.size} 条记录 · ${directionCount} 条方向")
                    }
                }
            },
        )

        conceptBuckets.forEach { (tag, pairs) ->
            val slug = slugFor(tag, tag)
            val uniqueDirections = pairs
                .map { it.first }
                .distinctBy { it.threadKey }
                .sortedBy { it.title }
            val uniqueNotes = pairs
                .distinctBy { it.second.id }
                .sortedByDescending { it.second.updatedAt }
                .take(6)
            val relatedObjects = candidates
                .filter { candidate -> tag in candidate.relatedConcepts }
                .groupBy { it.type }
            File(conceptsDir, "$slug.md").writeText(
                buildString {
                    appendLine("# $tag")
                    appendLine()
                    appendLine("- 最近更新：${displayTime(generatedAt)}")
                    appendLine("- 相关方向：${uniqueDirections.size} 条")
                    appendLine("- 相关记录：${uniqueNotes.size} 条")
                    appendLine()
                    appendLine("## 关联方向")
                    uniqueDirections.forEach { direction ->
                        appendLine("- [${direction.title}](../directions/${direction.slug}.md) · ${direction.stage.label} · [证据](../evidence/${direction.slug}.md)")
                    }
                    appendLine()
                    appendLine("## 最近记录")
                    uniqueNotes.forEach { (_, note) ->
                        val summary = NoteInsightSummaryExtractor.extract(note)
                        appendLine("- ${note.topic.ifBlank { "未命名记录" }}")
                        appendLine("  - ${summary.ifBlank { note.content.trim().take(72) }}")
                    }
                    if (relatedObjects.isNotEmpty()) {
                        appendLine()
                        appendLine("## 相关知识对象")
                        KnowledgeObjectType.entries.forEach { type ->
                            val items = relatedObjects[type].orEmpty()
                                .distinctBy { it.title }
                                .sortedByDescending { it.updatedAt }
                                .take(5)
                            if (items.isNotEmpty()) {
                                appendLine("### ${type.displayName}")
                                items.forEach { item ->
                                    val objectSlug = slugFor(item.title, "${item.type.folderName}-${item.noteId}")
                                    appendLine("- [${item.title}](../${type.folderName}/$objectSlug.md) · ${item.summary}")
                                }
                                appendLine()
                            }
                        }
                    }
                },
            )
        }
    }

    private fun writeKnowledgeObjectFiles(
        generatedAt: Long,
        candidates: List<KnowledgeObjectCandidate>,
        directories: Map<KnowledgeObjectType, File>,
    ) {
        val grouped = candidates
            .groupBy { it.type }
            .mapValues { (_, items) ->
                items
                    .groupBy { slugFor(it.title, "${it.type.folderName}-${it.noteId}") }
                    .mapNotNull { (slug, bucket) ->
                        val first = bucket.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        Triple(slug, first, bucket.sortedByDescending { it.updatedAt })
                    }
                    .sortedByDescending { (_, first, _) -> first.updatedAt }
            }

        KnowledgeObjectType.entries.forEach { type ->
            val directory = directories.getValue(type)
            val items = grouped[type].orEmpty()
            File(directory, "index.md").writeText(
                buildString {
                    appendLine("# ${type.displayName}")
                    appendLine()
                    appendLine("更新时间：${displayTime(generatedAt)}")
                    appendLine()
                    if (items.isEmpty()) {
                        appendLine("还没有形成可复用的${type.displayName}对象。")
                    } else {
                        items.forEach { (slug, first, bucket) ->
                            appendLine("- [${first.title}]($slug.md) · ${bucket.size} 条来源 · ${bucket.map { it.threadTitle }.distinct().size} 条方向")
                        }
                    }
                },
            )
            items.forEach { (slug, first, bucket) ->
                val relatedConcepts = bucket
                    .flatMap { it.relatedConcepts }
                    .distinct()
                    .sorted()
                val relatedDirections = bucket
                    .map { it.threadTitle }
                    .distinct()
                    .sorted()
                val evidenceLine = bucket
                    .groupingBy { it.evidenceType }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { (type, count) -> "${type.label} ${count}" }
                File(directory, "$slug.md").writeText(
                    buildString {
                        appendLine("# ${first.title}")
                        appendLine()
                        appendLine("- 类型：${type.displayName}")
                        appendLine("- 最近更新：${displayTime(first.updatedAt)}")
                        appendLine("- 相关方向：${bucket.map { it.threadTitle }.distinct().joinToString("、")}")
                        evidenceLine.takeIf { it.isNotBlank() }?.let {
                            appendLine("- 证据姿态：$it")
                        }
                        appendLine()
                        appendLine("## 当前提炼")
                        appendLine(first.summary)
                        appendLine()
                        if (relatedConcepts.isNotEmpty()) {
                            appendLine("## 相关概念")
                            relatedConcepts.forEach { concept ->
                                val conceptSlug = slugFor(concept, concept)
                                appendLine("- [$concept](../concepts/$conceptSlug.md)")
                            }
                            appendLine()
                        }
                        if (relatedDirections.isNotEmpty()) {
                            appendLine("## 相关方向页面")
                            relatedDirections.forEach { directionTitle ->
                                val directionSlug = slugFor(directionTitle, directionTitle)
                                appendLine("- [$directionTitle](../directions/$directionSlug.md) · [结论](../conclusions/$directionSlug.md) · [证据](../evidence/$directionSlug.md)")
                            }
                            appendLine()
                        }
                        appendLine("## 来源记录")
                        bucket.take(8).forEach { item ->
                            appendLine("- [${item.threadTitle}](../directions/${slugFor(item.threadTitle, item.threadTitle)}.md) · ${item.sourceLabel} · ${item.summary}")
                        }
                    },
                )
            }
        }
    }

    private fun buildConceptBuckets(
        summaries: List<DirectionWikiDirectionSummary>,
        allNotes: List<NoteEntity>,
    ): Map<String, List<Pair<DirectionWikiDirectionSummary, NoteEntity>>> =
        buildMap<String, MutableList<Pair<DirectionWikiDirectionSummary, NoteEntity>>> {
            summaries.forEach { summary ->
                NoteConnectionAnalyzer.notesForThread(summary.threadKey, allNotes)
                    .forEach { note ->
                        note.tags
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .forEach { tag ->
                                getOrPut(tag) { mutableListOf() }.add(summary to note)
                            }
                    }
            }
        }
            .filterValues { pairs -> pairs.size >= 2 }
            .toSortedMap()

    private fun writeLintFiles(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        lintDir: File,
    ) {
        File(lintDir, "index.md").writeText(
            buildString {
                appendLine("# 知识健康检查")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (summaries.isEmpty()) {
                    appendLine("还没有方向可检查。")
                } else {
                    summaries.sortedBy { it.title }.forEach { summary ->
                        appendLine("- [${summary.title}](${summary.slug}.md) · ${summary.healthLine}")
                        summary.maintenanceLine.takeIf { it.isNotBlank() }?.let { maintenance ->
                            appendLine("  - 建议先补：$maintenance")
                        }
                        summary.maintenanceTargetLine.takeIf { it.isNotBlank() }?.let { target ->
                            appendLine("  - 先维护：$target")
                        }
                        summary.maintenanceSourceLine.takeIf { it.isNotBlank() }?.let { source ->
                            appendLine("  - 先补来源：$source")
                        }
                        summary.maintenanceDimensionLine.takeIf { it.isNotBlank() }?.let { dimension ->
                            appendLine("  - 最薄弱：$dimension")
                        }
                        summary.maintenanceFocusLine.takeIf { it.isNotBlank() }?.let { focus ->
                            appendLine("  - 优先对象：$focus")
                        }
                    }
                }
            },
        )
    }

    private fun buildRawNotesMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw notes")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名记录" }}")
            appendLine("- id: ${note.id}")
            appendLine("- status: ${note.status.label}")
            appendLine("- horizon: ${note.horizon.label}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawValidationMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw validations")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名验证记录" }}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine("- status: ${note.status.label}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawReflectionMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw reflections")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名反思记录" }}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine("- status: ${note.status.label}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawReviewMarkdown(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
        weeklyReview: com.mindflow.app.data.connect.ThreadWeeklyReviewSummary,
    ): String = buildString {
        appendLine("# ${summary.title} reviews")
        appendLine()
        appendLine("- updated: ${displayTime(summary.updatedAt)}")
        appendLine("- stage: ${summary.stage.label}")
        appendLine()
        summary.conclusionLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前结论")
            appendLine(it)
            appendLine()
        }
        summary.nextShiftLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 下一步承接")
            appendLine(it)
            appendLine()
        }
        execution.whyNow.takeIf { it.isNotBlank() }?.let {
            appendLine("## 为什么现在")
            appendLine(it)
            appendLine()
        }
        execution.nextStep.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前最小动作")
            appendLine(it)
            appendLine()
        }
        execution.validationStep.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前验证动作")
            appendLine(it)
            appendLine()
        }
        weeklyReview.statsLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 本周回看")
            appendLine(it)
            weeklyReview.lines.forEach { line ->
                appendLine("- $line")
            }
            appendLine()
        }
    }

    private fun buildRawResearchMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw research")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名研究记录" }}")
            appendLine("- evidence: ${ResearchEvidenceAnalyzer.classify(note).label}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildDirectionMarkdown(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
        research: com.mindflow.app.data.connect.ExternalResearchSnapshot,
    ): String = buildString {
        appendLine("# ${summary.title}")
        appendLine()
        appendLine("- 当前阶段：${summary.stage.label}")
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        appendLine()
        appendLine("## 知识层页面")
        appendLine("- [结论](../conclusions/${summary.slug}.md)")
        appendLine("- [证据](../evidence/${summary.slug}.md)")
        appendLine("- [健康检查](../lint/${summary.slug}.md)")
        appendLine()
        appendLine("## 当前判断")
        appendLine(summary.assetSummary.ifBlank { execution.summary.ifBlank { "这条方向还在继续形成。" } })
        appendLine()
        summary.conclusionLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前结论")
            appendLine(it)
            appendLine()
        }
        summary.nextShiftLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 下一步承接")
            appendLine(it)
            appendLine()
        }
        summary.groundingLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 证据基础")
            appendLine(it)
            appendLine()
        }
        summary.knowledgeObjectLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 知识对象覆盖")
            appendLine(it)
            appendLine()
        }
        summary.healthLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 知识健康")
            appendLine(it)
            appendLine()
        }
        if (summary.lintIssues.isNotEmpty()) {
            appendLine("## 待补问题")
            summary.lintIssues.forEach { appendLine("- $it") }
            appendLine()
        }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 节奏")
            appendLine(it)
            appendLine()
        }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期阶段")
            appendLine(it)
            appendLine()
        }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 快照节奏")
            appendLine(it)
            appendLine()
        }
        execution.blocker.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前卡点")
            appendLine(it)
            appendLine()
        }
        appendLine("## 执行")
        execution.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("- 为什么现在：$it") }
        execution.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("- 当前最小动作：$it") }
        execution.validationStep.takeIf { it.isNotBlank() }?.let { appendLine("- 当前验证动作：$it") }
        execution.postValidationAction.takeIf { it.isNotBlank() }?.let { appendLine("- 如果成立，下一步：$it") }
        appendLine()
        appendLine("## 研究")
        research.outsideAngle.takeIf { it.isNotBlank() }?.let { appendLine("- 外部线索：$it") }
        research.opportunityGap.takeIf { it.isNotBlank() }?.let { appendLine("- 机会缺口：$it") }
        research.contrarianQuestion.takeIf { it.isNotBlank() }?.let { appendLine("- 值得追问：$it") }
        research.externalHypothesis.takeIf { it.isNotBlank() }?.let { appendLine("- 外部假设：$it") }
        appendLine()
        if (summary.signalPoints.isNotEmpty()) {
            appendLine("## 外部线索沉淀")
            summary.signalPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.hypothesisPoints.isNotEmpty()) {
            appendLine("## 待验证")
            summary.hypothesisPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.verifiedPoints.isNotEmpty()) {
            appendLine("## 已查证")
            summary.verifiedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.validatedPoints.isNotEmpty()) {
            appendLine("## 已验证")
            summary.validatedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.openQuestions.isNotEmpty()) {
            appendLine("## 开放问题")
            summary.openQuestions.forEach { appendLine("- $it") }
            appendLine()
        }
        summary.stageHistorySummary.takeIf { it.isNotBlank() }?.let {
            appendLine("## 阶段历史")
            appendLine(it)
            appendLine()
        }
        summary.trajectoryLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期走势")
            appendLine(it)
            appendLine()
        }
    }

    private fun buildEvidenceMarkdown(
        summary: DirectionWikiDirectionSummary,
        grounding: ResearchGroundingSnapshot,
        relatedConcepts: List<String>,
        relatedObjects: List<KnowledgeObjectCandidate>,
    ): String = buildString {
        appendLine("# ${summary.title} evidence")
        appendLine()
        appendLine("- ${grounding.summary.summaryLine.ifBlank { "暂无结构化研究证据" }}")
        appendLine()
        if (relatedConcepts.isNotEmpty()) {
            appendLine("## 相关概念")
            relatedConcepts.forEach { concept ->
                val conceptSlug = slugFor(concept, concept)
                appendLine("- [$concept](../concepts/$conceptSlug.md)")
            }
            appendLine()
        }
        if (summary.signalPoints.isNotEmpty()) {
            appendLine("## 外部线索")
            summary.signalPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.hypothesisPoints.isNotEmpty()) {
            appendLine("## 待验证")
            summary.hypothesisPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.verifiedPoints.isNotEmpty()) {
            appendLine("## 已查证")
            summary.verifiedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.validatedPoints.isNotEmpty()) {
            appendLine("## 已验证")
            summary.validatedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (relatedObjects.isNotEmpty()) {
            appendLine("## 相关知识对象")
            KnowledgeObjectType.entries.forEach { type ->
                val items = relatedObjects
                    .filter { it.type == type }
                    .distinctBy { it.title }
                    .sortedByDescending { it.updatedAt }
                    .take(4)
                if (items.isNotEmpty()) {
                    appendLine("### ${type.displayName}")
                    items.forEach { item ->
                        val objectSlug = slugFor(item.title, "${item.type.folderName}-${item.noteId}")
                        appendLine("- [${item.title}](../${type.folderName}/$objectSlug.md) · ${item.sourceLabel} · ${item.summary}")
                    }
                    appendLine()
                }
            }
        }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 节奏")
            appendLine(it)
            appendLine()
        }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期阶段")
            appendLine(it)
            appendLine()
        }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 快照节奏")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 建议先补")
            appendLine(it)
            appendLine()
        }
    }

    private fun writeConclusionIndex(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        conclusionsDir: File,
    ) {
        File(conclusionsDir, "index.md").writeText(
            buildString {
                appendLine("# 方向结论")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (summaries.isEmpty()) {
                    appendLine("还没有可复用的方向结论。")
                } else {
                    summaries.sortedBy { it.title }.forEach { summary ->
                        appendLine("- [${summary.title}](${summary.slug}.md) · ${summary.conclusionLine.ifBlank { summary.assetSummary.ifBlank { summary.stage.label } }}")
                    }
                }
            },
        )
    }

    private fun buildConclusionMarkdown(
        summary: DirectionWikiDirectionSummary,
        weeklyReview: com.mindflow.app.data.connect.ThreadWeeklyReviewSummary,
        relatedConcepts: List<String>,
        relatedObjects: List<KnowledgeObjectCandidate>,
    ): String = buildString {
        appendLine("# ${summary.title} conclusions")
        appendLine()
        appendLine("- 当前阶段：${summary.stage.label}")
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        appendLine()
        if (relatedConcepts.isNotEmpty()) {
            appendLine("## 相关概念")
            relatedConcepts.forEach { concept ->
                val conceptSlug = slugFor(concept, concept)
                appendLine("- [$concept](../concepts/$conceptSlug.md)")
            }
            appendLine()
        }
        appendLine("## 当前结论")
        appendLine(summary.conclusionLine.ifBlank { summary.assetSummary.ifBlank { "这条方向还在继续形成更稳定的判断。" } })
        appendLine()
        summary.nextShiftLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 下一步承接")
            appendLine(it)
            appendLine()
        }
        summary.validatedPoints.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine("## 已验证结果")
                appendLine("- $it")
                appendLine()
            }
        summary.verifiedPoints.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine("## 已查证")
                appendLine("- $it")
                appendLine()
            }
        summary.openQuestions.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine("## 开放问题")
                appendLine("- $it")
                appendLine()
            }
        if (relatedObjects.isNotEmpty()) {
            appendLine("## 相关知识对象")
            KnowledgeObjectType.entries.forEach { type ->
                val items = relatedObjects
                    .filter { it.type == type }
                    .distinctBy { it.title }
                    .sortedByDescending { it.updatedAt }
                    .take(3)
                if (items.isNotEmpty()) {
                    appendLine("### ${type.displayName}")
                    items.forEach { item ->
                        val objectSlug = slugFor(item.title, "${item.type.folderName}-${item.noteId}")
                        appendLine("- [${item.title}](../${type.folderName}/$objectSlug.md) · ${item.sourceLabel} · ${item.summary}")
                    }
                    appendLine()
                }
            }
        }
        weeklyReview.mainLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 本周判断")
            appendLine(it)
            appendLine()
        }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期阶段")
            appendLine(it)
            appendLine()
        }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 快照节奏")
            appendLine(it)
            appendLine()
        }
    }

    private fun buildLintMarkdown(
        summary: DirectionWikiDirectionSummary,
    ): String = buildString {
        appendLine("# ${summary.title} lint")
        appendLine()
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        appendLine("- 当前阶段：${summary.stage.label}")
        appendLine()
        summary.healthLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前判断")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 建议先补")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceTargetLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 先维护")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceSourceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 先补来源")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceDimensionLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 最薄弱维度")
            appendLine(it)
            appendLine()
        }
        summary.maintenanceFocusLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 优先对象")
            appendLine(it)
            appendLine()
        }
        summary.knowledgeObjectLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 知识对象覆盖")
            appendLine(it)
            appendLine()
        }
        if (summary.lintIssues.isNotEmpty()) {
            appendLine("## 待修补")
            summary.lintIssues.forEach { appendLine("- $it") }
        } else {
            appendLine("当前没有明显的知识健康问题。")
        }
    }

    private fun buildSnapshotMarkdown(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
    ): String = buildString {
        appendLine("# ${summary.title} snapshot")
        appendLine()
        appendLine("- 阶段：${summary.stage.label}")
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        summary.groundingLine.takeIf { it.isNotBlank() }?.let { appendLine("- 证据基础：$it") }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let { appendLine("- 节奏：$it") }
        execution.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("- 为什么现在：$it") }
        execution.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("- 下一步：$it") }
        execution.validationStep.takeIf { it.isNotBlank() }?.let { appendLine("- 先验证：$it") }
        summary.trajectoryLine.takeIf { it.isNotBlank() }?.let { appendLine("- 长期走势：$it") }
        summary.stageHistorySummary.takeIf { it.isNotBlank() }?.let { appendLine("- 历史：$it") }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let { appendLine("- 长期阶段：$it") }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let { appendLine("- 快照节奏：$it") }
    }

    private fun buildSnapshotTimelineMarkdown(
        summary: DirectionWikiDirectionSummary,
        history: DirectionSnapshotHistorySummary,
    ): String = buildString {
        appendLine("# ${summary.title} stage timeline")
        appendLine()
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let { appendLine("- $it") }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let { appendLine("- $it") }
        appendLine()
        appendLine("## Recent snapshots")
        if (history.snapshotEntries.isEmpty()) {
            appendLine("还没有可追踪的阶段快照。")
        } else {
            history.snapshotEntries
                .sortedByDescending { it.timestamp }
                .forEach { entry ->
                    appendLine("- ${displayTime(entry.timestamp)} · ${entry.stage.label}")
            }
        }
    }

    private fun buildGraphMarkdown(
        generatedAt: Long,
        graph: DirectionWikiGraphSnapshot,
    ): String = buildString {
        appendLine("# 信息图谱")
        appendLine()
        appendLine("更新时间：${displayTime(generatedAt)}")
        graph.overview.summaryLine.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }
        if (graph.overview.hubThreadKeys.isNotEmpty()) {
            appendLine()
            appendLine("结构中心：${graph.overview.hubThreadKeys.joinToString(" / ")}")
        }
        if (graph.overview.isolatedThreadKeys.isNotEmpty()) {
            appendLine("孤立主题：${graph.overview.isolatedThreadKeys.joinToString(" / ")}")
        }
        if (graph.overview.missingLinkCandidates.isNotEmpty()) {
            appendLine("待补边：${graph.overview.missingLinkCandidates.joinToString(" / ")}")
        }
        appendLine()
        appendLine("## 主题")
        if (graph.nodes.isEmpty()) {
            appendLine("还没有足够稳定的主题结构。")
        } else {
            graph.nodes.forEach { node ->
                appendLine("- ${node.label}")
                appendLine("  - 成熟度：${node.maturity.toDisplayLabel()} · 记录 ${node.noteCount} 条")
                node.summaryLine.takeIf { it.isNotBlank() }?.let { appendLine("  - 现在在讲：$it") }
                node.gapLine.takeIf { it.isNotBlank() }?.let { appendLine("  - 现在最缺：$it") }
            }
        }
        appendLine()
        appendLine("## 关系")
        if (graph.edges.isEmpty()) {
            appendLine("当前还没有足够硬的主题关系。")
        } else {
            graph.edges.forEach { edge ->
                appendLine("- ${edge.fromThreadKey} <-> ${edge.toThreadKey} · ${edge.relationType.wireName} · 强度 ${edge.strength}")
                edge.reasonLine.takeIf { it.isNotBlank() }?.let { appendLine("  - $it") }
            }
        }
        if (graph.presentation.nodes.isNotEmpty()) {
            appendLine()
            appendLine("## 前台压缩")
            graph.presentation.headline.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            graph.presentation.summaryLine.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            graph.presentation.nodes.forEach { node ->
                appendLine("- ${node.label}")
            }
        }
    }

    private fun writeExport(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
        conceptGraph: ConceptGraphSnapshot,
        graph: DirectionWikiGraphSnapshot,
    ) {
        val root = JSONObject()
            .put("generatedAt", generatedAt)
            .put("rootPath", rootDir.absolutePath)
            .put(
                "directions",
                JSONArray().apply {
                    summaries.forEach { summary ->
                        put(
                            JSONObject()
                                .put("threadKey", summary.threadKey)
                                .put("slug", summary.slug)
                                .put("title", summary.title)
                                .put("stage", summary.stage.name)
                                .put("assetSummary", summary.assetSummary)
                                .put("conclusionLine", summary.conclusionLine)
                                .put("nextShiftLine", summary.nextShiftLine)
                                .put("groundingLine", summary.groundingLine)
                                .put("trustLine", summary.trustLine)
                                .put("knowledgeObjectLine", summary.knowledgeObjectLine)
                                .put("healthLine", summary.healthLine)
                                .put("maintenanceLine", summary.maintenanceLine)
                                .put("maintenanceTargetLine", summary.maintenanceTargetLine)
                                .put("maintenanceSourceLine", summary.maintenanceSourceLine)
                                .put("maintenanceDimensionLine", summary.maintenanceDimensionLine)
                                .put("maintenanceFocusLine", summary.maintenanceFocusLine)
                                .put("continuityLine", summary.continuityLine)
                                .put("trajectoryLine", summary.trajectoryLine)
                                .put("stageHistorySummary", summary.stageHistorySummary)
                                .put("snapshotStageLine", summary.snapshotStageLine)
                                .put("snapshotCadenceLine", summary.snapshotCadenceLine)
                                .put("updatedAt", summary.updatedAt)
                                .put("signalPoints", JSONArray(summary.signalPoints))
                                .put("hypothesisPoints", JSONArray(summary.hypothesisPoints))
                                .put("verifiedPoints", JSONArray(summary.verifiedPoints))
                                .put("validatedPoints", JSONArray(summary.validatedPoints))
                                .put("lintIssues", JSONArray(summary.lintIssues))
                                .put("openQuestions", JSONArray(summary.openQuestions)),
                        )
                    }
                },
            )
            .put(
                "knowledgeItems",
                JSONArray().apply {
                    knowledgeItems.forEach { item ->
                        put(
                            JSONObject()
                                .put("id", item.id)
                                .put("type", item.type.name)
                                .put("title", item.title)
                                .put("summary", item.summary)
                                .put("supportLine", item.supportLine)
                                .put("trustLabel", item.trustLabel)
                                .put("threadKey", item.threadKey)
                                .put("noteId", item.noteId ?: JSONObject.NULL)
                                .put("updatedAt", item.updatedAt),
                        )
                    }
                },
            )
            .put("conceptGraph", JSONObject(conceptGraph.toConceptGraphJsonString()))
            .put(
                "graph",
                JSONObject()
                    .put("version", graph.version)
                    .put("source", graph.source)
                    .put("generatedAt", graph.generatedAt)
                    .put(
                        "overview",
                        JSONObject()
                            .put("summaryLine", graph.overview.summaryLine)
                            .put("hubThreadKeys", JSONArray(graph.overview.hubThreadKeys))
                            .put("isolatedThreadKeys", JSONArray(graph.overview.isolatedThreadKeys))
                            .put("densifyingThreadKeys", JSONArray(graph.overview.densifyingThreadKeys))
                            .put("missingLinkCandidates", JSONArray(graph.overview.missingLinkCandidates)),
                    )
                    .put(
                        "nodes",
                        JSONArray().apply {
                            graph.nodes.forEach { node ->
                                put(
                                    JSONObject()
                                        .put("threadKey", node.threadKey)
                                        .put("label", node.label)
                                        .put("summaryLine", node.summaryLine)
                                        .put("gapLine", node.gapLine)
                                        .put("maturity", node.maturity.wireName)
                                        .put("recencyScore", node.recencyScore)
                                        .put("densityScore", node.densityScore)
                                        .put("supportIds", JSONArray(node.supportIds))
                                        .put("noteCount", node.noteCount)
                                        .put("updatedAt", node.updatedAt),
                                )
                            }
                        },
                    )
                    .put(
                        "edges",
                        JSONArray().apply {
                            graph.edges.forEach { edge ->
                                put(
                                    JSONObject()
                                        .put("fromThreadKey", edge.fromThreadKey)
                                        .put("toThreadKey", edge.toThreadKey)
                                        .put("relationType", edge.relationType.wireName)
                                        .put("strength", edge.strength)
                                        .put("reasonLine", edge.reasonLine)
                                        .put("supportIds", JSONArray(edge.supportIds))
                                        .put("firstSeenAt", edge.firstSeenAt)
                                        .put("lastSeenAt", edge.lastSeenAt)
                                        .put("confidence", edge.confidence),
                                )
                            }
                        },
                    )
                    .put(
                        "presentation",
                        JSONObject()
                            .put("title", graph.presentation.title)
                            .put("headline", graph.presentation.headline)
                            .put("summaryLine", graph.presentation.summaryLine)
                            .put(
                                "nodes",
                                JSONArray().apply {
                                    graph.presentation.nodes.forEach { node ->
                                        put(
                                            JSONObject()
                                                .put("threadKey", node.threadKey)
                                                .put("label", node.label)
                                                .put("summaryLine", node.summaryLine)
                                                .put("gapLine", node.gapLine)
                                                .put("relationCount", node.relationCount)
                                                .put("densityScore", node.densityScore)
                                                .put("maturity", node.maturity.wireName)
                                                .put("noteCount", node.noteCount),
                                        )
                                    }
                                },
                            )
                            .put(
                                "edges",
                                JSONArray().apply {
                                    graph.presentation.edges.forEach { edge ->
                                        put(
                                            JSONObject()
                                                .put("fromThreadKey", edge.fromThreadKey)
                                                .put("toThreadKey", edge.toThreadKey)
                                                .put("strength", edge.strength)
                                                .put("reasonLine", edge.reasonLine),
                                        )
                                    }
                                },
                            )
                            .put(
                                "focus",
                                graph.presentation.focus?.let { focus ->
                                    JSONObject()
                                        .put("threadKey", focus.threadKey)
                                        .put("label", focus.label)
                                        .put("summaryLine", focus.summaryLine)
                                        .put("gapLine", focus.gapLine)
                                        .put("relatedThreadKey", focus.relatedThreadKey)
                                        .put("relatedReasonLine", focus.relatedReasonLine)
                                } ?: JSONObject.NULL,
                            ),
                    ),
            )
        File(rootDir, "wiki/export/direction-assets.json").writeText(root.toString(2))
    }

    private fun loadSnapshotFromDisk(): DirectionWikiSnapshot {
        val exportFile = File(rootDir, "wiki/export/direction-assets.json")
        if (!exportFile.exists()) {
            return DirectionWikiSnapshot(rootPath = rootDir.absolutePath)
        }
        return runCatching {
            val json = JSONObject(exportFile.readText())
            val directionsArray = json.optJSONArray("directions") ?: JSONArray()
            val directions = buildMap {
                for (index in 0 until directionsArray.length()) {
                    val item = directionsArray.optJSONObject(index) ?: continue
                    val threadKey = item.optString("threadKey")
                    if (threadKey.isBlank()) continue
                    put(
                        threadKey,
                        DirectionWikiDirectionSummary(
                            threadKey = threadKey,
                            slug = item.optString("slug"),
                            title = item.optString("title"),
                            stage = item.optString("stage").toDirectionStage(),
                            assetSummary = item.optString("assetSummary"),
                            conclusionLine = item.optString("conclusionLine"),
                            nextShiftLine = item.optString("nextShiftLine"),
                            groundingLine = item.optString("groundingLine"),
                            trustLine = item.optString("trustLine"),
                            knowledgeObjectLine = item.optString("knowledgeObjectLine"),
                            healthLine = item.optString("healthLine"),
                            maintenanceLine = item.optString("maintenanceLine"),
                            maintenanceTargetLine = item.optString("maintenanceTargetLine"),
                            maintenanceSourceLine = item.optString("maintenanceSourceLine"),
                            maintenanceDimensionLine = item.optString("maintenanceDimensionLine"),
                            maintenanceFocusLine = item.optString("maintenanceFocusLine"),
                            continuityLine = item.optString("continuityLine"),
                            trajectoryLine = item.optString("trajectoryLine"),
                            snapshotStageLine = item.optString("snapshotStageLine"),
                            snapshotCadenceLine = item.optString("snapshotCadenceLine"),
                            signalPoints = item.optJSONArray("signalPoints").toStringList(),
                            hypothesisPoints = item.optJSONArray("hypothesisPoints").toStringList(),
                            verifiedPoints = item.optJSONArray("verifiedPoints").toStringList(),
                            validatedPoints = item.optJSONArray("validatedPoints").toStringList(),
                            lintIssues = item.optJSONArray("lintIssues").toStringList(),
                            openQuestions = item.optJSONArray("openQuestions").toStringList(),
                            stageHistorySummary = item.optString("stageHistorySummary"),
                            updatedAt = item.optLong("updatedAt"),
                        ),
                    )
                }
            }
            val knowledgeItemsArray = json.optJSONArray("knowledgeItems") ?: JSONArray()
            val knowledgeItems = buildList {
                for (index in 0 until knowledgeItemsArray.length()) {
                    val item = knowledgeItemsArray.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    if (id.isBlank() || title.isBlank()) continue
                    add(
                        KnowledgeLayerSearchItem(
                            id = id,
                            type = item.optString("type").toKnowledgeSearchType(),
                            title = title,
                            summary = item.optString("summary"),
                            supportLine = item.optString("supportLine"),
                            trustLabel = item.optString("trustLabel"),
                            threadKey = item.optString("threadKey"),
                            noteId = item.takeIf { !it.isNull("noteId") }?.optLong("noteId"),
                            updatedAt = item.optLong("updatedAt"),
                        ),
                    )
                }
            }
            val conceptGraph = parseConceptGraphSnapshotOrDefault(
                when (val rawConceptGraph = json.opt("conceptGraph")) {
                    is JSONObject -> rawConceptGraph.toString()
                    is String -> rawConceptGraph
                    else -> null
                },
            )
            val graphObject = json.optJSONObject("graph")
            val graph = graphObject?.let { graphJson ->
                val overviewObject = graphJson.optJSONObject("overview")
                DirectionWikiGraphSnapshot(
                    version = graphJson.optInt("version").takeIf { it > 0 } ?: 1,
                    overview = DirectionWikiGraphOverview(
                        summaryLine = overviewObject?.optString("summaryLine")
                            ?: graphJson.optString("overviewLine"),
                        hubThreadKeys = overviewObject?.optJSONArray("hubThreadKeys").toStringList(),
                        isolatedThreadKeys = overviewObject?.optJSONArray("isolatedThreadKeys").toStringList(),
                        densifyingThreadKeys = overviewObject?.optJSONArray("densifyingThreadKeys").toStringList(),
                        missingLinkCandidates = overviewObject?.optJSONArray("missingLinkCandidates").toStringList(),
                    ),
                    source = graphJson.optString("source", "rule"),
                    generatedAt = graphJson.optLong("generatedAt"),
                    nodes = buildList {
                        val nodesArray = graphJson.optJSONArray("nodes") ?: JSONArray()
                        for (index in 0 until nodesArray.length()) {
                            val item = nodesArray.optJSONObject(index) ?: continue
                            val threadKey = item.optString("threadKey")
                            val label = item.optString("label")
                            if (threadKey.isBlank() || label.isBlank()) continue
                            add(
                                DirectionWikiGraphNode(
                                    threadKey = threadKey,
                                    label = label,
                                    summaryLine = item.optString("summaryLine"),
                                    gapLine = item.optString("gapLine"),
                                    maturity = item.optString("maturity").toGraphMaturity(),
                                    recencyScore = item.optDouble("recencyScore").takeIf { !it.isNaN() } ?: 0.0,
                                    densityScore = item.optDouble("densityScore").takeIf { !it.isNaN() } ?: 0.0,
                                    supportIds = item.optJSONArray("supportIds").toStringList(),
                                    noteCount = item.optInt("noteCount").coerceAtLeast(0),
                                    updatedAt = item.optLong("updatedAt"),
                                ),
                            )
                        }
                    },
                    edges = buildList {
                        val edgesArray = graphJson.optJSONArray("edges") ?: JSONArray()
                        for (index in 0 until edgesArray.length()) {
                            val item = edgesArray.optJSONObject(index) ?: continue
                            val from = item.optString("fromThreadKey")
                            val to = item.optString("toThreadKey")
                            if (from.isBlank() || to.isBlank() || from == to) continue
                            add(
                                DirectionWikiGraphEdge(
                                    fromThreadKey = from,
                                    toThreadKey = to,
                                    relationType = item.optString("relationType").toGraphRelationType(),
                                    strength = item.optInt("strength").coerceIn(1, 5).takeIf { it > 0 } ?: 3,
                                    reasonLine = item.optString("reasonLine"),
                                    supportIds = item.optJSONArray("supportIds").toStringList(),
                                    firstSeenAt = item.optLong("firstSeenAt"),
                                    lastSeenAt = item.optLong("lastSeenAt"),
                                    confidence = item.optDouble("confidence").takeIf { !it.isNaN() } ?: 0.0,
                                ),
                            )
                        }
                    },
                    presentation = graphJson.optJSONObject("presentation").toGraphPresentation(),
                )
            } ?: DirectionWikiGraphSnapshot()
            DirectionWikiSnapshot(
                rootPath = json.optString("rootPath", rootDir.absolutePath),
                lastGeneratedAt = json.optLong("generatedAt"),
                directions = directions,
                knowledgeItems = knowledgeItems,
                conceptGraph = conceptGraph,
                graph = graph,
            )
        }.getOrElse {
            DirectionWikiSnapshot(rootPath = rootDir.absolutePath)
        }
    }

    private fun ensureDirectories() {
        listOf(
            File(rootDir, "raw/notes"),
            File(rootDir, "raw/research"),
            File(rootDir, "raw/validations"),
            File(rootDir, "raw/reflections"),
            File(rootDir, "raw/reviews"),
            File(rootDir, "wiki/directions"),
            File(rootDir, "wiki/concepts"),
            File(rootDir, "wiki/conclusions"),
            File(rootDir, "wiki/evidence"),
            File(rootDir, "wiki/lint"),
            File(rootDir, "wiki/questions"),
            File(rootDir, "wiki/methods"),
            File(rootDir, "wiki/experiments"),
            File(rootDir, "wiki/snapshots"),
            File(rootDir, "wiki/export"),
        ).forEach { it.mkdirs() }
        File(rootDir, "AGENTS.md").takeIf { !it.exists() }?.writeText(
            """
            # MindFlow Knowledge Layer Agent Rules

            - Treat `raw/` as append-only sources.
            - Update `wiki/directions/`, `wiki/concepts/`, `wiki/evidence/`, `wiki/questions/`, `wiki/methods/`, `wiki/experiments/`, `wiki/snapshots/`, `wiki/index.md`, and `wiki/log.md`.
            - Distinguish clearly between:
              - AI external perspective
              - pending validation
              - verified findings
              - validated outcomes
            - Treat directions as one view of the knowledge layer, not the whole knowledge layer.
            - Prefer concise markdown pages over verbose chat transcripts.
            """.trimIndent(),
        )
    }

    private fun buildKnowledgeSearchItems(
        summaries: List<DirectionWikiDirectionSummary>,
        conceptBuckets: Map<String, List<Pair<DirectionWikiDirectionSummary, NoteEntity>>>,
        candidates: List<KnowledgeObjectCandidate>,
    ): List<KnowledgeLayerSearchItem> {
        val directionItems = summaries.map { summary ->
            KnowledgeLayerSearchItem(
                id = "direction:${summary.threadKey}",
                type = KnowledgeLayerSearchType.DIRECTION,
                title = summary.title,
                summary = summary.conclusionLine.ifBlank { summary.assetSummary.ifBlank { summary.healthLine } },
                supportLine = summary.stage.label,
                trustLabel = summary.toTrustLabel(),
                threadKey = summary.threadKey,
                updatedAt = summary.updatedAt,
            )
        }
        val conceptItems = buildGraphConceptSearchItems(conceptBuckets)
        val objectItems = buildGraphObjectSearchItems(candidates)
        val conclusionItems = summaries
            .filter { it.conclusionLine.isNotBlank() || it.assetSummary.isNotBlank() }
            .map { summary ->
                KnowledgeLayerSearchItem(
                    id = "conclusion:${summary.threadKey}",
                    type = KnowledgeLayerSearchType.CONCLUSION,
                    title = "${summary.title} 结论",
                    summary = summary.conclusionLine.ifBlank { summary.assetSummary },
                    supportLine = summary.nextShiftLine.ifBlank { summary.groundingLine },
                    trustLabel = summary.toTrustLabel(),
                    threadKey = summary.threadKey,
                    updatedAt = summary.updatedAt,
                )
            }
        val evidenceItems = summaries
            .filter {
                it.groundingLine.isNotBlank() ||
                    it.verifiedPoints.isNotEmpty() ||
                    it.validatedPoints.isNotEmpty() ||
                    it.hypothesisPoints.isNotEmpty()
            }
            .map { summary ->
                val supportLine = when {
                    summary.validatedPoints.isNotEmpty() -> "已验证 ${summary.validatedPoints.size}"
                    summary.verifiedPoints.isNotEmpty() -> "已查证 ${summary.verifiedPoints.size}"
                    summary.hypothesisPoints.isNotEmpty() -> "待验证 ${summary.hypothesisPoints.size}"
                    else -> summary.groundingLine
                }
                KnowledgeLayerSearchItem(
                    id = "evidence:${summary.threadKey}",
                    type = KnowledgeLayerSearchType.EVIDENCE,
                    title = "${summary.title} 证据",
                    summary = summary.groundingLine.ifBlank {
                        summary.validatedPoints.firstOrNull()
                            ?: summary.verifiedPoints.firstOrNull()
                            ?: summary.hypothesisPoints.firstOrNull()
                            ?: ""
                    },
                    supportLine = supportLine,
                    trustLabel = summary.toTrustLabel(),
                    threadKey = summary.threadKey,
                    updatedAt = summary.updatedAt,
                )
            }
        return (directionItems + conceptItems + objectItems + conclusionItems + evidenceItems)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    private fun migrateLegacyRootIfNeeded() {
        if (rootDir.exists() || !legacyRootDir.exists()) return
        legacyRootDir.renameTo(rootDir)
    }

    private fun slugFor(title: String, fallback: String): String {
        val base = title
            .lowercase()
            .replace("#", "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
        return if (base.isNotBlank()) base else fallback.replace(':', '-').replace('/', '-')
    }

    private fun fileTimestamp(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun displayTime(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun String.toKnowledgeSearchType(): KnowledgeLayerSearchType =
        runCatching { KnowledgeLayerSearchType.valueOf(this) }.getOrDefault(KnowledgeLayerSearchType.DIRECTION)

    private fun displayDate(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    private fun String.toDirectionStage(): DirectionStage =
        runCatching { DirectionStage.valueOf(this) }.getOrDefault(DirectionStage.FORMING)

    private fun DirectionWikiDirectionSummary.toTrustLabel(): String =
        when {
            validatedPoints.isNotEmpty() -> ResearchEvidenceType.VALIDATED.label
            verifiedPoints.isNotEmpty() -> ResearchEvidenceType.VERIFIED.label
            hypothesisPoints.isNotEmpty() -> ResearchEvidenceType.HYPOTHESIS.label
            signalPoints.isNotEmpty() -> ResearchEvidenceType.SIGNAL.label
            else -> ""
        }

    private fun buildKnowledgeObjectLine(
        questionCount: Int,
        methodCount: Int,
        experimentCount: Int,
    ): String {
        val parts = buildList {
            if (questionCount > 0) add("${questionCount} 个问题")
            if (methodCount > 0) add("${methodCount} 个方法")
            if (experimentCount > 0) add("${experimentCount} 个实验")
        }
        return if (parts.isEmpty()) "" else "当前已形成 ${parts.joinToString(" · ")}。"
    }

    private fun buildSynthesizedKnowledgeObjects(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
        research: com.mindflow.app.data.connect.ExternalResearchSnapshot,
        relatedConcepts: List<String>,
    ): List<KnowledgeObjectCandidate> {
        val candidates = mutableListOf<KnowledgeObjectCandidate>()
        val baseId = syntheticObjectId(summary.threadKey, "knowledge")
        summary.openQuestions.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { question ->
                candidates += KnowledgeObjectCandidate(
                    type = KnowledgeObjectType.QUESTION,
                    title = "${summary.title} · 开放问题",
                    summary = question,
                    noteId = baseId,
                    updatedAt = summary.updatedAt,
                    threadKey = summary.threadKey,
                    threadTitle = summary.title,
                    relatedConcepts = relatedConcepts,
                    evidenceType = ResearchEvidenceType.HYPOTHESIS,
                    sourceLabel = "知识层综合判断",
                )
            }
        research.contrarianQuestion.takeIf { it.isNotBlank() }?.let { question ->
            candidates += KnowledgeObjectCandidate(
                type = KnowledgeObjectType.QUESTION,
                title = "${summary.title} · 研究追问",
                summary = question,
                noteId = baseId - 10,
                updatedAt = summary.updatedAt,
                threadKey = summary.threadKey,
                threadTitle = summary.title,
                relatedConcepts = relatedConcepts,
                evidenceType = ResearchEvidenceType.SIGNAL,
                sourceLabel = "AI 外部线索",
            )
        }
        execution.validationStep.takeIf { it.isNotBlank() }?.let { validation ->
            candidates += KnowledgeObjectCandidate(
                type = KnowledgeObjectType.EXPERIMENT,
                title = "${summary.title} · 当前验证",
                summary = validation,
                noteId = baseId - 1,
                updatedAt = summary.updatedAt,
                threadKey = summary.threadKey,
                threadTitle = summary.title,
                relatedConcepts = relatedConcepts,
                evidenceType = ResearchEvidenceType.HYPOTHESIS,
                sourceLabel = "知识层验证动作",
            )
        }
        research.externalHypothesis.takeIf { it.isNotBlank() }?.let { hypothesis ->
            candidates += KnowledgeObjectCandidate(
                type = KnowledgeObjectType.EXPERIMENT,
                title = "${summary.title} · 外部假设",
                summary = hypothesis,
                noteId = baseId - 11,
                updatedAt = summary.updatedAt,
                threadKey = summary.threadKey,
                threadTitle = summary.title,
                relatedConcepts = relatedConcepts,
                evidenceType = ResearchEvidenceType.HYPOTHESIS,
                sourceLabel = "AI 外部线索",
            )
        }
        summary.nextShiftLine.takeIf { it.isNotBlank() }?.let { nextShift ->
            candidates += KnowledgeObjectCandidate(
                type = KnowledgeObjectType.METHOD,
                title = "${summary.title} · 下一步承接",
                summary = nextShift,
                noteId = baseId - 2,
                updatedAt = summary.updatedAt,
                threadKey = summary.threadKey,
                threadTitle = summary.title,
                relatedConcepts = relatedConcepts,
                evidenceType = ResearchEvidenceType.VERIFIED,
                sourceLabel = "知识层承接动作",
            )
        }
        return candidates
    }

    private fun buildDirectionRelatedConcepts(
        notes: List<NoteEntity>,
    ): List<String> = notes
        .flatMap { note -> note.tags }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(3)

    private fun buildMaintenanceFocusLine(
        summaryTitle: String,
        stage: DirectionStage,
        conclusionLine: String,
        nextShiftLine: String,
        healthLine: String,
        maintenanceTargetLine: String,
        questionObjects: List<KnowledgeObjectCandidate>,
        methodObjects: List<KnowledgeObjectCandidate>,
        experimentObjects: List<KnowledgeObjectCandidate>,
        openQuestions: List<String>,
        daysSinceUpdate: Long,
    ): String {
        val firstQuestion = questionObjects.maxByOrNull { it.updatedAt }?.title
            ?: openQuestions.firstOrNull()?.let { "$summaryTitle · 开放问题" }
        val firstMethod = methodObjects.maxByOrNull { it.updatedAt }?.title
        val firstExperiment = experimentObjects.maxByOrNull { it.updatedAt }?.title
        return when {
            maintenanceTargetLine == "证据页" ->
                "证据页「$summaryTitle」"
            stage == DirectionStage.FORMING && firstQuestion != null ->
                "问题对象「$firstQuestion」"
            maintenanceTargetLine == "方法 / 实验对象" && firstExperiment != null ->
                "实验对象「$firstExperiment」"
            maintenanceTargetLine == "方法 / 实验对象" && firstMethod != null ->
                "方法对象「$firstMethod」"
            maintenanceTargetLine == "结论页" && conclusionLine.isNotBlank() ->
                "结论页「$summaryTitle」"
            maintenanceTargetLine == "方向页" && daysSinceUpdate >= 21L ->
                "方向页「$summaryTitle」"
            maintenanceTargetLine == "结论与知识对象" && nextShiftLine.isNotBlank() ->
                "承接对象「$summaryTitle · 下一步承接」"
            healthLine.isNotBlank() && firstQuestion != null ->
                "问题对象「$firstQuestion」"
            healthLine.isNotBlank() && firstExperiment != null ->
                "实验对象「$firstExperiment」"
            healthLine.isNotBlank() && firstMethod != null ->
                "方法对象「$firstMethod」"
            else -> ""
        }
    }

    private fun buildConclusionLine(
        assetSummary: String,
        weeklyMainLine: String,
        validatedPoint: String,
        verifiedPoint: String,
    ): String =
        validatedPoint
            .ifBlank { verifiedPoint }
            .ifBlank { assetSummary }
            .ifBlank { weeklyMainLine }

    private fun buildTrustLine(
        signalPoints: List<String>,
        hypothesisPoints: List<String>,
        verifiedPoints: List<String>,
        validatedPoints: List<String>,
    ): String =
        when {
            validatedPoints.isNotEmpty() ->
                "当前已经有已验证结果，可以把这条方向当作更稳定的结论继续推进。"
            verifiedPoints.isNotEmpty() ->
                "当前已经出现已查证信息，但还需要继续验证，别过早把它当成最终结论。"
            hypothesisPoints.isNotEmpty() ->
                "当前主要停在待验证层，先做最小验证，比继续堆更多判断更有效。"
            signalPoints.isNotEmpty() ->
                "当前主要还是外部线索，只适合用来启发方向，还不能当成可靠结论。"
            else -> ""
        }

    private fun syntheticObjectId(
        key: String,
        suffix: String,
    ): Long {
        val raw = "$key:$suffix".hashCode().toLong()
        return if (raw >= 0) -(raw + 1L) else raw
    }

    private fun writeEvidenceIndex(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        evidenceDir: File,
    ) {
        File(evidenceDir, "index.md").writeText(
            buildString {
                appendLine("# 证据索引")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (summaries.isEmpty()) {
                    appendLine("还没有可追踪的证据页。")
                } else {
                    summaries.sortedBy { it.title }.forEach { summary ->
                        appendLine("- [${summary.title}](${summary.slug}.md) · ${summary.groundingLine.ifBlank { summary.healthLine.ifBlank { summary.stage.label } }}")
                    }
                }
            },
        )
    }

    private fun DirectionWikiGraphMaturity.toDisplayLabel(): String =
        when (this) {
            DirectionWikiGraphMaturity.FORMING -> "形成中"
            DirectionWikiGraphMaturity.STRENGTHENING -> "增强中"
            DirectionWikiGraphMaturity.STABLE -> "稳定"
        }

    private fun String.toGraphMaturity(): DirectionWikiGraphMaturity =
        DirectionWikiGraphMaturity.entries.firstOrNull { it.wireName == trim().lowercase() }
            ?: DirectionWikiGraphMaturity.FORMING

    private fun String.toGraphRelationType(): DirectionWikiGraphRelationType =
        DirectionWikiGraphRelationType.entries.firstOrNull { it.wireName == trim().lowercase() }
            ?: DirectionWikiGraphRelationType.CO_OCCURRENCE

    private fun JSONObject?.toGraphPresentation(): DirectionWikiGraphPresentationSnapshot {
        if (this == null) return DirectionWikiGraphPresentationSnapshot()
        val nodes = buildList {
            val array = optJSONArray("nodes") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val threadKey = item.optString("threadKey")
                val label = item.optString("label")
                if (threadKey.isBlank() || label.isBlank()) continue
                add(
                    DirectionWikiGraphPresentationNode(
                        threadKey = threadKey,
                        label = label,
                        summaryLine = item.optString("summaryLine"),
                        gapLine = item.optString("gapLine"),
                        relationCount = item.optInt("relationCount").coerceAtLeast(0),
                        densityScore = item.optDouble("densityScore").takeIf { !it.isNaN() } ?: 0.0,
                        maturity = item.optString("maturity").toGraphMaturity(),
                        noteCount = item.optInt("noteCount").coerceAtLeast(0),
                    ),
                )
            }
        }
        val edges = buildList {
            val array = optJSONArray("edges") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val from = item.optString("fromThreadKey")
                val to = item.optString("toThreadKey")
                if (from.isBlank() || to.isBlank() || from == to) continue
                add(
                    DirectionWikiGraphPresentationEdge(
                        fromThreadKey = from,
                        toThreadKey = to,
                        strength = item.optInt("strength").coerceIn(1, 5).takeIf { it > 0 } ?: 3,
                        reasonLine = item.optString("reasonLine"),
                    ),
                )
            }
        }
        val focus = optJSONObject("focus")?.let { focusObject ->
            focusObject.optString("threadKey")
                .takeIf { it.isNotBlank() }
                ?.let { threadKey ->
                    DirectionWikiGraphPresentationFocus(
                        threadKey = threadKey,
                        label = focusObject.optString("label"),
                        summaryLine = focusObject.optString("summaryLine"),
                        gapLine = focusObject.optString("gapLine"),
                        relatedThreadKey = focusObject.optString("relatedThreadKey"),
                        relatedReasonLine = focusObject.optString("relatedReasonLine"),
                    )
                }
        }
        return DirectionWikiGraphPresentationSnapshot(
            title = optString("title").ifBlank { "信息图谱" },
            headline = optString("headline"),
            summaryLine = optString("summaryLine"),
            nodes = nodes,
            edges = edges,
            focus = focus,
        )
    }

}
