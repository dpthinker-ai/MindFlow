package com.mindflow.app.data.ai

import java.io.File

class AiTaskTraceRecorder(
    private val directory: File,
) {
    private val traceFile: File by lazy {
        directory.mkdirs()
        File(directory, "ai-task-traces.jsonl")
    }

    private val latestSuccessFile: File by lazy {
        directory.mkdirs()
        File(directory, "latest-successful-provider.json")
    }

    @Synchronized
    fun append(
        taskType: AiTaskType,
        meta: AiTaskMeta,
    ) {
        val payload = linkedMapOf(
            "taskType" to taskType.name,
            "providerUsed" to meta.providerUsed.name,
            "fallbackOccurred" to meta.fallbackOccurred,
            "fallbackReason" to meta.fallbackReason,
            "latencyMs" to meta.latencyMs,
            "qualitySignals" to meta.qualitySignals,
        )
        traceFile.appendText(renderJsonObject(payload) + "\n")
        latestSuccessFile.writeText(renderJsonObject(payload))
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
}
