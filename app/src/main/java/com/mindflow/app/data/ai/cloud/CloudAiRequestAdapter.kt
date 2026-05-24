package com.mindflow.app.data.ai.cloud

import com.mindflow.app.data.model.AiSettings

data class CloudAiPreparedRequest(
    val provider: CloudAiProviderSpec,
    val url: String,
    val authHeaders: List<String>,
    val model: String,
    val body: String,
)

object CloudAiRequestAdapter {
    fun build(
        settings: AiSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double,
        thinkingEnabled: Boolean,
    ): CloudAiPreparedRequest {
        val provider = CloudAiProviderRegistry.resolve(
            providerId = settings.providerId,
            baseUrl = settings.baseUrl,
        )
        val baseUrl = settings.baseUrl.trim().ifBlank { provider.baseUrl }.normalizedBaseUrl()
        val model = provider.deprecatedModelAliases[settings.model.trim()]
            ?: settings.model.trim().ifBlank { provider.defaultModel }
        val fields = linkedMapOf<String, Any?>(
            "model" to model,
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "stream" to false,
            "messages" to listOf(
                linkedMapOf("role" to "system", "content" to systemPrompt),
                linkedMapOf("role" to "user", "content" to userPrompt),
            ),
        )
        if (provider.requestCapabilities.supportsThinking) {
            fields["thinking"] = linkedMapOf("type" to if (thinkingEnabled) "enabled" else "disabled")
        }

        return CloudAiPreparedRequest(
            provider = provider,
            url = "$baseUrl${provider.chatPath.ensureLeadingSlash()}",
            authHeaders = buildAuthCandidates(
                apiKey = settings.apiKey,
                authScheme = provider.authScheme,
            ),
            model = model,
            body = renderJsonObject(fields),
        )
    }

    private fun buildAuthCandidates(
        apiKey: String,
        authScheme: CloudAiAuthScheme,
    ): List<String> {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return emptyList()
        val rawKey = trimmed.removePrefix("Bearer").trim()
        return when (authScheme) {
            CloudAiAuthScheme.BEARER_ONLY -> listOf("Bearer $rawKey")
            CloudAiAuthScheme.RAW_KEY_ONLY -> listOf(rawKey)
            CloudAiAuthScheme.RAW_KEY_THEN_BEARER -> listOf(rawKey, "Bearer $rawKey").distinct()
            CloudAiAuthScheme.BEARER_THEN_RAW_KEY -> listOf("Bearer $rawKey", rawKey).distinct()
        }
    }

    private fun String.ensureLeadingSlash(): String =
        if (startsWith('/')) this else "/$this"

    private fun renderJsonObject(fields: Map<String, Any?>): String =
        fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${renderJsonString(key)}:${renderJsonValue(value)}"
        }

    private fun renderJsonArray(values: List<*>): String =
        values.joinToString(prefix = "[", postfix = "]") { value -> renderJsonValue(value) }

    private fun renderJsonValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> renderJsonString(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        is List<*> -> renderJsonArray(value)
        is Map<*, *> -> renderJsonObject(
            value.entries.associate { (key, nestedValue) -> key.toString() to nestedValue },
        )
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
}
