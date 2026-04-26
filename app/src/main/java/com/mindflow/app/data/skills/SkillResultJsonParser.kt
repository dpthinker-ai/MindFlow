package com.mindflow.app.data.skills

internal object SkillResultJsonParser {
    fun parse(
        skill: SkillPackage,
        raw: String,
    ): SkillResult {
        val trimmed = raw.trim()
        val candidate = extractJsonCandidate(trimmed) ?: return SkillResult(result = trimmed)
        val root = runCatching { SkillMiniJsonParser(candidate).parseObject() }.getOrNull()
            ?: return SkillResult(result = trimmed)

        return SkillResult(
            result = root.stringValue("result"),
            error = root.stringValue("error"),
            image = root.objectValue("image").takeIf { it.isNotEmpty() }?.toImageSpec(),
            webview = root.objectValue("webview").takeIf { it.isNotEmpty() }?.toWebViewSpec(skill),
            metadata = root.objectValue("metadata").toMetadataMap(),
            dataJson = (root["data"] as? SkillJsonValue.JsonObject)?.let(::renderSkillJsonObject),
        )
    }

    private fun Map<String, SkillJsonValue>.toImageSpec(): SkillImageSpec? {
        val base64 = stringValue("base64") ?: return null
        return SkillImageSpec(base64 = base64)
    }

    private fun Map<String, SkillJsonValue>.toWebViewSpec(skill: SkillPackage): SkillWebViewSpec? {
        val url = stringValue("url") ?: return null
        return SkillWebViewSpec(
            url = resolveWebViewUrl(skill, url),
            iframe = booleanValue("iframe") ?: false,
            aspectRatio = numberValue("aspectRatio")?.toFloat() ?: 1.333f,
        )
    }

    private fun Map<String, SkillJsonValue>.toMetadataMap(): Map<String, String> =
        entries.mapNotNull { (key, value) ->
            val rendered = when (value) {
                is SkillJsonValue.JsonString -> value.value
                is SkillJsonValue.JsonNumber -> value.value.toString()
                is SkillJsonValue.JsonBoolean -> value.value.toString()
                else -> null
            }?.takeIf { it.isNotBlank() }
            rendered?.let { key to it }
        }.toMap(linkedMapOf())

    private fun resolveWebViewUrl(
        skill: SkillPackage,
        rawUrl: String,
    ): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://")) {
            return trimmed
        }
        val relative = trimmed.removePrefix("./").removePrefix("/")
        return "file:///android_asset/${skill.assetBasePath}/$relative"
    }

    private fun extractJsonCandidate(content: String): String? {
        if (content.startsWith("{") && content.endsWith("}")) return content
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        return if (start >= 0 && end > start) content.substring(start, end + 1) else null
    }

    private fun renderSkillJsonObject(
        value: SkillJsonValue.JsonObject,
    ): String = renderSkillJsonValue(value)

    private fun renderSkillJsonValue(
        value: SkillJsonValue,
    ): String = when (value) {
        is SkillJsonValue.JsonObject -> value.values.entries.joinToString(
            prefix = "{",
            postfix = "}",
        ) { (key, nestedValue) ->
            "${renderSkillJsonString(key)}:${renderSkillJsonValue(nestedValue)}"
        }

        is SkillJsonValue.JsonArray -> value.items.joinToString(
            prefix = "[",
            postfix = "]",
        ) { item -> renderSkillJsonValue(item) }

        is SkillJsonValue.JsonString -> renderSkillJsonString(value.value)
        is SkillJsonValue.JsonNumber -> value.value.toString()
        is SkillJsonValue.JsonBoolean -> value.value.toString()
        SkillJsonValue.JsonNull -> "null"
    }

    private fun renderSkillJsonString(
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

private fun Map<String, SkillJsonValue>.stringValue(key: String): String? =
    (this[key] as? SkillJsonValue.JsonString)?.value?.takeIf { it.isNotBlank() }

private fun Map<String, SkillJsonValue>.booleanValue(key: String): Boolean? =
    (this[key] as? SkillJsonValue.JsonBoolean)?.value

private fun Map<String, SkillJsonValue>.numberValue(key: String): Double? =
    (this[key] as? SkillJsonValue.JsonNumber)?.value

private fun Map<String, SkillJsonValue>.objectValue(key: String): Map<String, SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonObject)?.values ?: emptyMap()
