package com.mindflow.app.data.model

object NoteTagCodec {
    private const val SEPARATOR = '\u001F'

    fun normalize(rawTags: List<String>, maxCount: Int = 3): List<String> {
        val seen = LinkedHashSet<String>()
        rawTags.forEach { raw ->
            normalizeOne(raw)?.let { normalized ->
                if (seen.size < maxCount) {
                    seen += normalized
                }
            }
        }
        return seen.toList()
    }

    fun normalizeOne(raw: String): String? {
        val cleaned = raw
            .trim()
            .trim('#', ',', '，', '、', ';', '；', '|', '/', ' ')
            .replace(Regex("\\s+"), " ")
            .replace(SEPARATOR.toString(), "")
            .take(16)
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    fun encode(tags: List<String>): String {
        val normalized = normalize(tags)
        if (normalized.isEmpty()) return ""
        return buildString {
            append(SEPARATOR)
            append(normalized.joinToString(SEPARATOR.toString()))
            append(SEPARATOR)
        }
    }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .trim(SEPARATOR)
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .let(::normalize)
    }

    fun likePattern(tag: String): String? =
        normalizeOne(tag)?.let { "%$SEPARATOR$it$SEPARATOR%" }

    fun parseAiOutput(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .lineSequence()
            .flatMap { line ->
                line.split(Regex("[,，、/|；;\\n]")).asSequence()
            }
            .map { it.trim().removePrefix("-").trim() }
            .toList()
            .let(::normalize)
    }
}
