package com.mindflow.app.data.skills

import com.mindflow.app.data.local.entity.NoteEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

interface NativeToolBridge {
    suspend fun invoke(
        apiName: String,
        payloadJson: String,
    ): String

    fun canInvoke(apiName: String): Boolean
}

class UnsupportedNativeToolBridge : NativeToolBridge {
    override suspend fun invoke(
        apiName: String,
        payloadJson: String,
    ): String = error("Native tool bridge is not attached for api=$apiName")

    override fun canInvoke(apiName: String): Boolean = false
}

class DefaultNativeToolBridge(
    private val loadAllNotes: suspend () -> List<NoteEntity>,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : NativeToolBridge {
    override suspend fun invoke(
        apiName: String,
        payloadJson: String,
    ): String = when (apiName) {
        "history.count" -> renderHistoryCount(payloadJson)
        "history.query" -> renderHistoryQuery(payloadJson)
        else -> error("Unsupported native tool api=$apiName")
    }

    override fun canInvoke(apiName: String): Boolean = apiName in supportedApis

    private suspend fun renderHistoryCount(payloadJson: String): String {
        val request = parseHistoryRequest(payloadJson)
        val allNotes = loadActiveNotes()
        val scopedNotes = selectScopedNotes(allNotes, request.timeScope)
        val matchedNotes = selectMatchedNotes(scopedNotes, request.entityTerms)
        return renderJsonObject(
            linkedMapOf(
                "coverage" to buildCoveragePayload(
                    scopedNotes = scopedNotes,
                    matchedNotes = matchedNotes,
                    processedCount = matchedNotes.size,
                    nextCursor = null,
                ),
            ),
        )
    }

    private suspend fun renderHistoryQuery(payloadJson: String): String {
        val request = parseHistoryRequest(payloadJson)
        val allNotes = loadActiveNotes()
        val scopedNotes = selectScopedNotes(allNotes, request.timeScope)
        val matchedNotes = selectMatchedNotes(scopedNotes, request.entityTerms, request.sort)
        val offset = request.cursor ?: 0
        val page = matchedNotes.drop(offset).take(request.pageSize)
        val nextCursor = (offset + page.size).takeIf { it < matchedNotes.size }?.toString()
        return renderJsonObject(
            linkedMapOf(
                "coverage" to buildCoveragePayload(
                    scopedNotes = scopedNotes,
                    matchedNotes = matchedNotes,
                    processedCount = minOf(offset + page.size, matchedNotes.size),
                    nextCursor = nextCursor,
                ),
                "records" to page.map { note ->
                    linkedMapOf(
                        "id" to note.id.toString(),
                        "date" to note.createdLocalDate(zoneId).toString(),
                        "title" to note.displayTitle(),
                        "summary" to note.summaryLine(),
                        "folder" to note.folderKey,
                        "tags" to note.tags,
                        "content" to request.includeContent.thenValue(note.content),
                    ).filterValues { it != null }
                },
            ),
        )
    }

    private suspend fun loadActiveNotes(): List<NoteEntity> =
        loadAllNotes().filterNot(NoteEntity::isArchived)

    private fun selectScopedNotes(
        notes: List<NoteEntity>,
        timeScope: HistorySkillTimeScope,
    ): List<NoteEntity> = notes
        .asSequence()
        .filter { timeScope.matches(it.createdLocalDate(zoneId)) }
        .toList()

    private fun selectMatchedNotes(
        notes: List<NoteEntity>,
        entityTerms: List<String>,
        sort: HistorySkillSort = HistorySkillSort.CREATED_AT_ASC,
    ): List<NoteEntity> = notes
        .asSequence()
        .filter { note -> entityTerms.all { note.matchesEntityTerm(it) } }
        .sortedWith(sort.comparator())
        .toList()

    private fun parseHistoryRequest(payloadJson: String): HistorySkillQueryRequest {
        val payload = SkillMiniJsonParser(payloadJson.ifBlank { "{}" }).parseObject()
        return HistorySkillQueryRequest(
            timeScope = parseTimeScope(payload.objectValue("timeScope")),
            entityTerms = payload.stringArrayValue("entityTerms"),
            pageSize = payload.numberValue("pageSize")?.toInt()?.coerceIn(1, 100) ?: 50,
            cursor = payload.stringValue("cursor")?.toIntOrNull(),
            includeContent = payload.booleanValue("includeContent") ?: false,
            sort = HistorySkillSort.fromWire(payload.stringValue("sort")),
        )
    }

    private fun parseTimeScope(raw: Map<String, SkillJsonValue>): HistorySkillTimeScope {
        val type = raw.stringValue("type")?.trim()?.lowercase().orEmpty()
        return when (type) {
            "day" -> raw.stringValue("date")
                ?.let(LocalDate::parse)
                ?.let(HistorySkillTimeScope::Day)
                ?: HistorySkillTimeScope.AllTime
            "month" -> raw.stringValue("month")
                ?.let(YearMonth::parse)
                ?.let(HistorySkillTimeScope::Month)
                ?: HistorySkillTimeScope.AllTime
            "range" -> {
                val start = raw.stringValue("start")?.let(LocalDate::parse)
                val end = raw.stringValue("end")?.let(LocalDate::parse)
                if (start != null && end != null) {
                    HistorySkillTimeScope.Range(start = start, endInclusive = end)
                } else {
                    HistorySkillTimeScope.AllTime
                }
            }
            else -> HistorySkillTimeScope.AllTime
        }
    }

    private fun buildCoveragePayload(
        scopedNotes: List<NoteEntity>,
        matchedNotes: List<NoteEntity>,
        processedCount: Int,
        nextCursor: String?,
    ): Map<String, Any?> {
        val sortedMatched = matchedNotes.sortedBy(NoteEntity::createdAt)
        val dateRangePayload = if (sortedMatched.isNotEmpty()) {
            linkedMapOf(
                "start" to sortedMatched.first().createdLocalDate(zoneId).toString(),
                "end" to sortedMatched.last().createdLocalDate(zoneId).toString(),
            )
        } else {
            null
        }
        return linkedMapOf(
            "totalCount" to scopedNotes.size,
            "matchedCount" to matchedNotes.size,
            "processedCount" to processedCount,
            "complete" to (nextCursor == null),
            "dateRange" to dateRangePayload,
            "nextCursor" to nextCursor,
        ).filterValues { it != null }
    }

    private fun NoteEntity.matchesEntityTerm(rawTerm: String): Boolean {
        val term = rawTerm.trim().lowercase()
        if (term.isBlank()) return true
        val haystack = buildString {
            append(topic)
            append('\n')
            append(folderKey.orEmpty())
            append('\n')
            append(tags.joinToString(" "))
            append('\n')
            append(content)
        }.lowercase()
        return haystack.contains(term)
    }

    private fun NoteEntity.displayTitle(): String =
        topic.ifBlank {
            content.lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotBlank() }
                ?.take(32)
                ?: "未命名记录"
        }

    private fun NoteEntity.summaryLine(): String = content
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .take(160)

    private fun NoteEntity.createdLocalDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()

    private fun HistorySkillSort.comparator(): Comparator<NoteEntity> = when (this) {
        HistorySkillSort.CREATED_AT_ASC -> compareBy(NoteEntity::createdAt).thenBy(NoteEntity::id)
        HistorySkillSort.CREATED_AT_DESC -> compareByDescending(NoteEntity::createdAt).thenByDescending(NoteEntity::id)
    }

    private fun Boolean.thenValue(value: String): String? = if (this) value else null

    private companion object {
        val supportedApis = setOf("history.count", "history.query")
    }
}

private data class HistorySkillQueryRequest(
    val timeScope: HistorySkillTimeScope = HistorySkillTimeScope.AllTime,
    val entityTerms: List<String> = emptyList(),
    val pageSize: Int = 50,
    val cursor: Int? = null,
    val includeContent: Boolean = false,
    val sort: HistorySkillSort = HistorySkillSort.CREATED_AT_ASC,
)

private enum class HistorySkillSort {
    CREATED_AT_ASC,
    CREATED_AT_DESC;

    companion object {
        fun fromWire(raw: String?): HistorySkillSort = when (raw?.trim()?.lowercase()) {
            "created_at_desc" -> CREATED_AT_DESC
            else -> CREATED_AT_ASC
        }
    }
}

private sealed interface HistorySkillTimeScope {
    data object AllTime : HistorySkillTimeScope
    data class Day(val date: LocalDate) : HistorySkillTimeScope
    data class Month(val month: YearMonth) : HistorySkillTimeScope
    data class Range(
        val start: LocalDate,
        val endInclusive: LocalDate,
    ) : HistorySkillTimeScope

    fun matches(date: LocalDate): Boolean = when (this) {
        AllTime -> true
        is Day -> date == this.date
        is Month -> YearMonth.from(date) == month
        is Range -> !date.isBefore(start) && !date.isAfter(endInclusive)
    }
}

private fun renderJsonObject(
    fields: Map<String, Any?>,
): String = fields.entries.joinToString(
    prefix = "{",
    postfix = "}",
) { (key, value) ->
    "${renderJsonString(key)}:${renderJsonValue(value)}"
}

private fun renderJsonArray(
    values: List<*>,
): String = values.joinToString(
    prefix = "[",
    postfix = "]",
) { value -> renderJsonValue(value) }

private fun renderJsonValue(
    value: Any?,
): String = when (value) {
    null -> "null"
    is String -> renderJsonString(value)
    is Number -> value.toString()
    is Boolean -> value.toString()
    is List<*> -> renderJsonArray(value)
    is Map<*, *> -> renderJsonObject(
        value.entries.associate { (key, nestedValue) ->
            key.toString() to nestedValue
        },
    )
    else -> renderJsonString(value.toString())
}

private fun renderJsonString(
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

private fun Map<String, SkillJsonValue>.stringValue(key: String): String? =
    (this[key] as? SkillJsonValue.JsonString)?.value?.takeIf { it.isNotBlank() }

private fun Map<String, SkillJsonValue>.booleanValue(key: String): Boolean? =
    (this[key] as? SkillJsonValue.JsonBoolean)?.value

private fun Map<String, SkillJsonValue>.numberValue(key: String): Double? =
    (this[key] as? SkillJsonValue.JsonNumber)?.value

private fun Map<String, SkillJsonValue>.objectValue(key: String): Map<String, SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonObject)?.values ?: emptyMap()

private fun Map<String, SkillJsonValue>.stringArrayValue(key: String): List<String> {
    val array = this[key] as? SkillJsonValue.JsonArray ?: return emptyList()
    return array.items.mapNotNull { (it as? SkillJsonValue.JsonString)?.value?.takeIf(String::isNotBlank) }
}
