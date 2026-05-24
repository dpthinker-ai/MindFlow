package com.mindflow.app.data.ai

import java.io.File

interface AiUsageEventRepository {
    suspend fun append(event: AiUsageEvent)
    suspend fun recent(limit: Int = 20): List<AiUsageEvent>
    suspend fun todaySummary(dayStartMillis: Long, nextDayStartMillis: Long): AiUsageSummary
    suspend fun markNotified(eventIds: List<String>, notifiedAt: Long, notificationBatchId: String)
}

class FileAiUsageEventRepository(
    directory: File,
    private val now: () -> Long = { System.currentTimeMillis() },
) : AiUsageEventRepository {
    private val file = File(directory, "ai-usage-events.jsonl")

    override suspend fun append(event: AiUsageEvent) {
        file.parentFile?.mkdirs()
        file.appendText(event.toJsonLine() + "\n")
        prune()
    }

    override suspend fun recent(limit: Int): List<AiUsageEvent> =
        readEvents().takeLast(limit.coerceAtLeast(0))

    override suspend fun todaySummary(dayStartMillis: Long, nextDayStartMillis: Long): AiUsageSummary {
        val events = readEvents().filter { it.timestamp in dayStartMillis until nextDayStartMillis }
        return AiUsageSummary(
            requests = events.size,
            backgroundRequests = events.count { it.triggerMode != AiTriggerMode.FOREGROUND_USER_ACTION },
            successes = events.count { it.success },
            fallbackCount = events.count { it.fallbackOccurred },
            tokens = events.sumOf { it.tokenCount ?: 0 },
        )
    }

    override suspend fun markNotified(eventIds: List<String>, notifiedAt: Long, notificationBatchId: String) {
        val targetIds = eventIds.toSet()
        val updated = readEvents().map { event ->
            if (event.eventId in targetIds) {
                event.copy(notifiedAt = notifiedAt, notificationBatchId = notificationBatchId)
            } else {
                event
            }
        }
        rewrite(updated)
    }

    fun rawText(): String = if (file.exists()) file.readText() else ""

    private fun prune() {
        val cutoff = now() - RETENTION_DAYS * DAY_MILLIS
        val retainedByAge = readEvents().filter { event ->
            event.timestamp < EPOCH_LIKE_TIMESTAMP || event.timestamp >= cutoff
        }
        rewrite(retainedByAge.takeLast(MAX_EVENTS))
    }

    private fun readEvents(): List<AiUsageEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line -> parseEvent(line) }
    }

    private fun rewrite(events: List<AiUsageEvent>) {
        file.parentFile?.mkdirs()
        file.writeText(events.joinToString(separator = "\n", postfix = if (events.isEmpty()) "" else "\n") { it.toJsonLine() })
    }

    private companion object {
        const val MAX_EVENTS = 1_000
        const val RETENTION_DAYS = 90L
        const val DAY_MILLIS = 86_400_000L
        const val EPOCH_LIKE_TIMESTAMP = 31_536_000_000L
    }
}

private fun AiUsageEvent.toJsonLine(): String = renderJsonObject(
    linkedMapOf(
        "eventId" to eventId,
        "timestamp" to timestamp,
        "taskType" to taskType.name,
        "triggerSurface" to triggerSurface.name,
        "triggerMode" to triggerMode.name,
        "providerId" to providerId,
        "providerLabel" to providerLabel,
        "model" to model,
        "executionMode" to executionMode.name,
        "providerSelectionReason" to providerSelectionReason.name,
        "fallbackOccurred" to fallbackOccurred,
        "fallbackReason" to fallbackReason,
        "dataSensitivity" to dataSensitivity.name,
        "payloadPolicy" to payloadPolicy.name,
        "tokenCount" to tokenCount,
        "success" to success,
        "failureReason" to failureReason,
        "notifiedAt" to notifiedAt,
        "notificationBatchId" to notificationBatchId,
    ),
)

private fun parseEvent(line: String): AiUsageEvent? {
    val values = parseFlatJsonObject(line)
    return runCatching {
        AiUsageEvent(
            eventId = values.stringValue("eventId"),
            timestamp = values.longValue("timestamp"),
            taskType = enumValueOf(values.stringValue("taskType")),
            triggerSurface = enumValueOf(values.stringValue("triggerSurface")),
            triggerMode = enumValueOf(values.stringValue("triggerMode")),
            providerId = values.stringValue("providerId"),
            providerLabel = values.stringValue("providerLabel"),
            model = values.stringValue("model"),
            executionMode = enumValueOf(values.stringValue("executionMode")),
            providerSelectionReason = enumValueOf(values.stringValue("providerSelectionReason")),
            fallbackOccurred = values.booleanValue("fallbackOccurred"),
            fallbackReason = values.nullableStringValue("fallbackReason"),
            dataSensitivity = enumValueOf(values.stringValue("dataSensitivity")),
            payloadPolicy = enumValueOf(values.stringValue("payloadPolicy")),
            tokenCount = values.intValueOrNull("tokenCount"),
            success = values.booleanValue("success"),
            failureReason = values.nullableStringValue("failureReason"),
            notifiedAt = values.longValueOrNull("notifiedAt"),
            notificationBatchId = values.nullableStringValue("notificationBatchId"),
        )
    }.getOrNull()
}

private fun parseFlatJsonObject(line: String): Map<String, String?> {
    val result = linkedMapOf<String, String?>()
    var index = 0
    while (index < line.length) {
        val keyStart = line.indexOf('"', index)
        if (keyStart < 0) break
        val keyEnd = line.indexOf('"', keyStart + 1)
        if (keyEnd < 0) break
        val key = line.substring(keyStart + 1, keyEnd)
        val colon = line.indexOf(':', keyEnd + 1)
        if (colon < 0) break
        val valueStart = colon + 1
        val parsed = parseJsonValue(line, valueStart)
        result[key] = parsed.first
        index = parsed.second
    }
    return result
}

private fun parseJsonValue(line: String, start: Int): Pair<String?, Int> {
    var index = start
    while (index < line.length && line[index].isWhitespace()) index += 1
    if (line.startsWith("null", index)) return null to index + 4
    if (index < line.length && line[index] == '"') {
        val builder = StringBuilder()
        index += 1
        while (index < line.length) {
            val character = line[index]
            if (character == '\\' && index + 1 < line.length) {
                builder.append(line[index + 1])
                index += 2
            } else if (character == '"') {
                return builder.toString() to index + 1
            } else {
                builder.append(character)
                index += 1
            }
        }
    }
    val end = generateSequence(index) { next -> (next + 1).takeIf { it < line.length } }
        .firstOrNull { line[it] == ',' || line[it] == '}' }
        ?: line.length
    return line.substring(index, end).trim() to end + 1
}

private fun Map<String, String?>.stringValue(key: String): String = this[key].orEmpty()
private fun Map<String, String?>.nullableStringValue(key: String): String? = this[key]?.takeIf { it != "null" }
private fun Map<String, String?>.longValue(key: String): Long = this[key]?.toLongOrNull() ?: 0L
private fun Map<String, String?>.longValueOrNull(key: String): Long? = this[key]?.toLongOrNull()
private fun Map<String, String?>.intValueOrNull(key: String): Int? = this[key]?.toIntOrNull()
private fun Map<String, String?>.booleanValue(key: String): Boolean = this[key].toBoolean()

private fun renderJsonObject(fields: Map<String, Any?>): String =
    fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${renderJsonString(key)}:${renderJsonValue(value)}"
    }

private fun renderJsonValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> renderJsonString(value)
    is Number -> value.toString()
    is Boolean -> value.toString()
    else -> renderJsonString(value.toString())
}

private fun renderJsonString(value: String): String = buildString {
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
