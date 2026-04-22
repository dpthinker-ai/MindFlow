package com.mindflow.app.data.reviewchat

data class ReviewChatStructuredAnswer(
    val sections: List<ReviewChatStructuredSection>,
)

data class ReviewChatStructuredSection(
    val title: String,
    val body: List<String>,
    val items: List<String>,
)

internal fun parseReviewChatStructuredAnswer(content: String): ReviewChatStructuredAnswer? {
    parseReviewChatStructuredJson(content)?.let { return it }

    val prepared = content
        .replace(Regex("(?<!\\n)(【(?:答复|结论|依据|下一步|记录|完整记录|时间线|类别)】)"), "\n$1")
        .trim()
    if (!REVIEW_CHAT_STRUCTURED_SECTION_REGEX.containsMatchIn(prepared)) return null

    val sections = mutableListOf<MutableReviewChatStructuredSection>()
    var current: MutableReviewChatStructuredSection? = null

    prepared.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach

        val sectionMatch = REVIEW_CHAT_STRUCTURED_SECTION_REGEX.matchEntire(trimmed)
        if (sectionMatch != null) {
            current = MutableReviewChatStructuredSection(title = sectionMatch.groupValues[1])
                .also(sections::add)
            sectionMatch.groupValues[2]
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { current.appendStructuredLine(it) }
            return@forEach
        }

        val target = current ?: return@forEach
        val bulletContent = REVIEW_CHAT_BULLET_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
            ?: REVIEW_CHAT_ORDERED_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
        if (bulletContent != null) {
            target.items += bulletContent.trim()
        } else {
            target.appendStructuredLine(trimmed)
        }
    }

    return sections.takeIf { it.isNotEmpty() }?.let { parsed ->
        ReviewChatStructuredAnswer(
            sections = parsed.map { section ->
                ReviewChatStructuredSection(
                    title = section.title,
                    body = section.body.toList(),
                    items = section.items.toList(),
                )
            },
        )
    }
}

internal fun buildReviewChatStructuredOutputSchema(
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
): String {
    val allowedTitles = buildReviewChatAllowedSectionTitles(mode, wantsCategories)
        .joinToString("|")
    return """只返回一个 JSON 对象，不要 Markdown、不要代码块。schema: {"summary":"一句话回答","sections":[{"title":"$allowedTitles","body":["补充段落"],"items":["列表项"]}]}。sections 可为空数组；没有内容时用空数组。"""
}

internal fun buildReviewChatStructuringPrompt(
    packet: ReviewChatContextPacket,
    rawAnswer: String,
): String = buildString {
    appendLine("把下面这段回答整理成一个 JSON 对象。不要新增事实，只能重组原回答已经表达的信息。")
    appendLine("问题路径：${packet.questionMode.name}")
    appendLine(buildReviewChatStructuredOutputSchema(packet.questionMode, packet.wantsCategories))
    appendLine("规则：")
    appendLine("1. summary 只保留一句总答复。")
    appendLine("2. sections 里只保留和当前问题直接相关的段落。")
    appendLine("3. items 必须是数组，每个列表项单独拆开，不要把多个 bullet 挤在同一字符串里。")
    appendLine("4. 不要输出 schema 以外的字段。")
    appendLine("原回答：")
    appendLine(rawAnswer.take(4_000))
}

internal fun renderReviewChatStructuredAnswerAsMarkdown(
    answer: ReviewChatStructuredAnswer,
): String = buildString {
    answer.sections.forEachIndexed { index, section ->
        if (index > 0) appendLine()
        if (section.title == "答复") {
            section.body
                .joinToString("\n")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let(::appendLine)
            if (section.items.isNotEmpty()) {
                section.items.forEach { item -> appendLine("- $item") }
            }
            return@forEachIndexed
        }

        appendLine("${section.title}：")
        section.body.forEach { paragraph ->
            if (paragraph.isNotBlank()) appendLine(paragraph)
        }
        section.items.forEach { item -> appendLine("- $item") }
    }
}.trim()

internal fun splitInlineReviewChatBulletItems(content: String): List<String> {
    val exploded = explodeInlineReviewChatBulletMarkers(content).trim()
    if (!exploded.contains('\n') && !REVIEW_CHAT_BULLET_LINE_REGEX.matches(exploded)) return emptyList()

    return exploded
        .lines()
        .mapNotNull { line ->
            REVIEW_CHAT_BULLET_LINE_REGEX.matchEntire(line.trim())
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
}

private fun explodeInlineReviewChatBulletMarkers(content: String): String =
    content.replace(
        Regex("(?<!\\n)(?<!\\d)([-*•])(?=\\s*[\\p{IsHan}A-Z《【])"),
        "\n$1",
    )

private data class MutableReviewChatStructuredSection(
    val title: String,
    val body: MutableList<String> = mutableListOf(),
    val items: MutableList<String> = mutableListOf(),
) {
    fun appendStructuredLine(line: String) {
        if (line.isBlank()) return
        if (title in REVIEW_CHAT_ITEM_SECTION_TITLES) {
            val itemsFromLine = splitInlineReviewChatBulletItems(line)
            if (itemsFromLine.isNotEmpty()) {
                items += itemsFromLine
                return
            }
        }
        appendFreeLine(line)
    }

    private fun appendFreeLine(line: String) {
        if (line.isBlank()) return
        if (items.isNotEmpty()) {
            val lastIndex = items.lastIndex
            items[lastIndex] = items[lastIndex] + " " + line
        } else {
            body += line
        }
    }
}

internal val REVIEW_CHAT_STRUCTURED_SECTION_REGEX =
    Regex(
        pattern = "^【(答复|结论|依据|下一步|记录|完整记录|时间线|类别)】\\s*(.*)$",
        option = RegexOption.MULTILINE,
    )

internal val REVIEW_CHAT_BULLET_LINE_REGEX = Regex("^[-*•]\\s*(.+)$")

internal val REVIEW_CHAT_ORDERED_LINE_REGEX =
    Regex("^(?:\\d+[.、）)]|[一二三四五六七八九十]+、)\\s*(.+)$")

internal val REVIEW_CHAT_ITEM_SECTION_TITLES =
    setOf("依据", "下一步", "记录", "完整记录", "时间线", "类别")

private fun parseReviewChatStructuredJson(content: String): ReviewChatStructuredAnswer? {
    val candidate = extractJsonCandidate(content) ?: return null
    return runCatching {
        val json = ReviewChatMiniJsonParser(candidate).parseObject()
        val sections = mutableListOf<ReviewChatStructuredSection>()

        val summary = (json["summary"] as? ReviewChatJsonValue.JsonString)?.value
            ?.ifBlank { (json["answer"] as? ReviewChatJsonValue.JsonString)?.value.orEmpty() }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        summary?.let {
                sections += ReviewChatStructuredSection(
                    title = "答复",
                    body = listOf(it),
                    items = emptyList(),
                )
            }

        ((json["sections"] as? ReviewChatJsonValue.JsonArray)?.items)
            ?.let(::parseStructuredSections)
            ?.let(sections::addAll)

        sections.takeIf { it.isNotEmpty() }?.let(::ReviewChatStructuredAnswer)
    }.getOrNull()
}

private fun parseStructuredSections(array: List<ReviewChatJsonValue>): List<ReviewChatStructuredSection> =
    buildList {
        array.forEach { item ->
            val section = item as? ReviewChatJsonValue.JsonObject ?: return@forEach
            val title = (section.values["title"] as? ReviewChatJsonValue.JsonString)
                ?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            add(
                ReviewChatStructuredSection(
                    title = title,
                    body = parseFlexibleStringList(section.values["body"]),
                    items = parseFlexibleStringList(section.values["items"]),
                )
            )
        }
    }

private fun parseFlexibleStringList(
    value: ReviewChatJsonValue?,
): List<String> = when (value) {
    is ReviewChatJsonValue.JsonArray -> value.items
        .mapNotNull { (it as? ReviewChatJsonValue.JsonString)?.value?.trim()?.takeIf { text -> text.isNotBlank() } }
    is ReviewChatJsonValue.JsonString -> listOf(value.value.trim()).filter { it.isNotBlank() }
    else -> emptyList()
}

private fun extractJsonCandidate(content: String): String? {
    val trimmed = content.trim()
    val unfenced = trimmed
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    if (unfenced.startsWith("{") && unfenced.endsWith("}")) {
        return unfenced
    }

    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start >= 0 && end > start) {
        return unfenced.substring(start, end + 1)
    }
    return null
}

private fun buildReviewChatAllowedSectionTitles(
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
): List<String> = when (mode) {
    ReviewChatQuestionMode.EXTERNAL -> listOf("下一步")
    ReviewChatQuestionMode.COLLECTION_OVERVIEW -> if (wantsCategories) {
        listOf("类别")
    } else {
        listOf("依据", "记录")
    }
    ReviewChatQuestionMode.RECORD_LOOKUP -> if (wantsCategories) {
        listOf("类别", "记录")
    } else {
        listOf("记录")
    }
    ReviewChatQuestionMode.FULL_RECORD -> listOf("完整记录")
    ReviewChatQuestionMode.TIMELINE_ANCHOR -> listOf("时间线")
    ReviewChatQuestionMode.ANALYSIS -> listOf("依据", "下一步")
}

private sealed interface ReviewChatJsonValue {
    data class JsonObject(val values: Map<String, ReviewChatJsonValue>) : ReviewChatJsonValue
    data class JsonArray(val items: List<ReviewChatJsonValue>) : ReviewChatJsonValue
    data class JsonString(val value: String) : ReviewChatJsonValue
    data class JsonNumber(val value: Double) : ReviewChatJsonValue
    data class JsonBoolean(val value: Boolean) : ReviewChatJsonValue
    data object JsonNull : ReviewChatJsonValue
}

private class ReviewChatMiniJsonParser(
    private val raw: String,
) {
    private var index = 0

    fun parseObject(): Map<String, ReviewChatJsonValue> {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == raw.length) { "Unexpected trailing JSON content." }
        return (value as? ReviewChatJsonValue.JsonObject)?.values
            ?: throw IllegalArgumentException("Root JSON value must be an object.")
    }

    private fun parseValue(): ReviewChatJsonValue {
        skipWhitespace()
        return when (val current = currentChar()) {
            '{' -> parseObjectValue()
            '[' -> parseArrayValue()
            '"' -> ReviewChatJsonValue.JsonString(parseString())
            't' -> {
                consumeLiteral("true")
                ReviewChatJsonValue.JsonBoolean(true)
            }
            'f' -> {
                consumeLiteral("false")
                ReviewChatJsonValue.JsonBoolean(false)
            }
            'n' -> {
                consumeLiteral("null")
                ReviewChatJsonValue.JsonNull
            }
            '-', in '0'..'9' -> ReviewChatJsonValue.JsonNumber(parseNumber())
            else -> throw IllegalArgumentException("Unexpected JSON token '$current' at index $index.")
        }
    }

    private fun parseObjectValue(): ReviewChatJsonValue.JsonObject {
        expect('{')
        skipWhitespace()
        if (peekChar() == '}') {
            index++
            return ReviewChatJsonValue.JsonObject(emptyMap())
        }
        val values = linkedMapOf<String, ReviewChatJsonValue>()
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
        return ReviewChatJsonValue.JsonObject(values)
    }

    private fun parseArrayValue(): ReviewChatJsonValue.JsonArray {
        expect('[')
        skipWhitespace()
        if (peekChar() == ']') {
            index++
            return ReviewChatJsonValue.JsonArray(emptyList())
        }
        val items = mutableListOf<ReviewChatJsonValue>()
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
        return ReviewChatJsonValue.JsonArray(items)
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

    private fun consumeLiteral(literal: String) {
        require(raw.startsWith(literal, index)) { "Expected '$literal' at index $index." }
        index += literal.length
    }

    private fun expect(expected: Char) {
        require(currentChar() == expected) { "Expected '$expected' at index $index." }
        index++
    }

    private fun currentChar(): Char {
        require(index < raw.length) { "Unexpected end of JSON input." }
        return raw[index]
    }

    private fun peekChar(): Char = raw.getOrNull(index) ?: '\u0000'

    private fun skipWhitespace() {
        while (index < raw.length && raw[index].isWhitespace()) {
            index++
        }
    }
}
