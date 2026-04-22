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

internal fun finalizeReviewChatStructuredAnswer(
    packet: ReviewChatContextPacket,
    rawAnswer: String,
    candidate: ReviewChatStructuredAnswer?,
): ReviewChatStructuredAnswer? {
    val normalized = candidate?.let {
        normalizeReviewChatStructuredAnswer(
            answer = it,
            mode = packet.questionMode,
            wantsCategories = packet.wantsCategories,
        )
    }

    val mergedSections = linkedMapOf<String, ReviewChatStructuredSection>()
    normalized?.sections
        ?.map { section -> canonicalizeReviewChatStructuredSection(section) }
        ?.forEach { section ->
            mergedSections[section.title] = mergeReviewChatStructuredSection(
                existing = mergedSections[section.title],
                incoming = section,
            )
        }

    if ("答复" !in mergedSections) {
        rawAnswer.trim()
            .takeIf { it.isNotBlank() }
            ?.let { summary ->
                mergedSections["答复"] = ReviewChatStructuredSection(
                    title = "答复",
                    body = listOf(summary),
                    items = emptyList(),
                )
            }
    }

    buildDeterministicReviewChatSection(packet, normalized).forEach { section ->
        mergedSections[section.title] = mergeReviewChatStructuredSection(
            existing = mergedSections[section.title],
            incoming = section,
        )
    }

    val orderedTitles = listOf("答复") + buildReviewChatAllowedSectionTitles(
        mode = packet.questionMode,
        wantsCategories = packet.wantsCategories,
    )
    val sections = orderedTitles
        .mapNotNull { title -> mergedSections[title] }
        .map(::canonicalizeReviewChatStructuredSection)
        .filterNot { it.body.isEmpty() && it.items.isEmpty() }

    return sections.takeIf { it.isNotEmpty() }?.let(::ReviewChatStructuredAnswer)
}

internal fun buildReviewChatStructuredOutputSchema(
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
): String {
    val template = buildReviewChatStructuredOutputTemplateJson(mode, wantsCategories)
    return "只返回一个 JSON 对象，不要 Markdown、不要代码块。严格使用这个模板里的 title 和顺序：$template。可以留空数组，但不要新增字段。"
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
    appendLine("2. sections 里的 title 必须和模板完全一致，不要改名，也不要新增 section。")
    appendLine("3. items 必须是数组，每个列表项单独拆开，不要把多个 bullet 挤在同一字符串里。")
    appendLine("4. body 和 items 都是数组；没有内容时用空数组。")
    appendLine("5. 不要输出模板以外的字段。")
    appendLine("原回答：")
    appendLine(rawAnswer.take(4_000))
}

internal fun buildReviewChatStructuredOutputTemplateJson(
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
): String = buildString {
    append("{\"summary\":\"一句话回答\",\"sections\":[")
    append(
        buildReviewChatAllowedSectionTitles(mode, wantsCategories).joinToString(",") { title ->
            buildReviewChatStructuredTemplateSectionJson(title)
        }
    )
    append("]}")
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

private fun buildReviewChatStructuredTemplateSectionJson(title: String): String {
    val bodyExample = when (title) {
        "完整记录" -> "\"2026-04-13《标题》\\n原文内容\""
        else -> ""
    }
    val itemsExample = when (title) {
        "依据", "下一步" -> "\"一条列表项\""
        "记录" -> "\"2026-04-13《标题》：摘要\""
        "时间线" -> "\"最早记录：2026-04-13《标题》：摘要\""
        "类别" -> "\"类别：包含的信息\""
        else -> ""
    }
    return buildString {
        append("{\"title\":\"")
        append(title)
        append("\",\"body\":[")
        append(bodyExample)
        append("],\"items\":[")
        append(itemsExample)
        append("]}")
    }
}

private fun normalizeReviewChatStructuredAnswer(
    answer: ReviewChatStructuredAnswer,
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
): ReviewChatStructuredAnswer {
    val mergedSections = linkedMapOf<String, ReviewChatStructuredSection>()
    answer.sections
        .map(::canonicalizeReviewChatStructuredSection)
        .forEach { section ->
            mergedSections[section.title] = mergeReviewChatStructuredSection(
                existing = mergedSections[section.title],
                incoming = section,
            )
        }

    val orderedTitles = listOf("答复") + buildReviewChatAllowedSectionTitles(mode, wantsCategories)
    return ReviewChatStructuredAnswer(
        sections = orderedTitles
            .mapNotNull { title -> mergedSections[title] }
            .filterNot { it.body.isEmpty() && it.items.isEmpty() }
    )
}

private fun canonicalizeReviewChatStructuredSection(
    section: ReviewChatStructuredSection,
): ReviewChatStructuredSection =
    ReviewChatStructuredSection(
        title = canonicalReviewChatSectionTitle(section.title),
        body = section.body.mapNotNull { it.trim().takeIf(String::isNotBlank) },
        items = section.items.mapNotNull { it.trim().takeIf(String::isNotBlank) },
    )

private fun canonicalReviewChatSectionTitle(title: String): String = when (title.trim()) {
    "结论" -> "答复"
    else -> title.trim()
}

private fun mergeReviewChatStructuredSection(
    existing: ReviewChatStructuredSection?,
    incoming: ReviewChatStructuredSection,
): ReviewChatStructuredSection {
    if (existing == null) return incoming
    return ReviewChatStructuredSection(
        title = incoming.title,
        body = (existing.body + incoming.body).distinct(),
        items = (existing.items + incoming.items).distinct(),
    )
}

private fun buildDeterministicReviewChatSection(
    packet: ReviewChatContextPacket,
    existingAnswer: ReviewChatStructuredAnswer?,
): List<ReviewChatStructuredSection> = buildList {
    val existingTitles = existingAnswer?.sections?.map { canonicalReviewChatSectionTitle(it.title) }?.toSet().orEmpty()
    when (packet.questionMode) {
        ReviewChatQuestionMode.COLLECTION_OVERVIEW -> {
            if (!packet.wantsCategories && "依据" !in existingTitles) {
                buildCollectionOverviewEvidenceSection(packet.collectionOverview)?.let(::add)
            }
        }

        ReviewChatQuestionMode.RECORD_LOOKUP -> {
            if (!packet.wantsCategories && "记录" !in existingTitles) {
                buildRecordEvidenceSection(packet.rawNoteEvidence)?.let(::add)
            }
        }

        ReviewChatQuestionMode.FULL_RECORD -> {
            if ("完整记录" !in existingTitles) {
                buildFullRecordSection(packet.rawNoteDetails)?.let(::add)
            }
        }

        ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
            if ("时间线" !in existingTitles) {
                buildTimelineSection(packet.historyAnchors)?.let(::add)
            }
        }

        ReviewChatQuestionMode.EXTERNAL,
        ReviewChatQuestionMode.ANALYSIS -> Unit
    }
}

private fun buildCollectionOverviewEvidenceSection(
    overview: ReviewChatCollectionOverview?,
): ReviewChatStructuredSection? {
    if (overview == null) return null
    val items = buildList {
        add("统计范围：${overview.scopeLabel}")
        add("记录总数：共 ${overview.totalCount} 条记录")
        if (overview.earliestDateLabel != null && overview.latestDateLabel != null && overview.totalCount > 0) {
            add("时间跨度：最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
        }
        overview.last7DaysCount?.let { add("最近 7 天：$it 条") }
        overview.last30DaysCount?.let { add("最近 30 天：$it 条") }
    }
    return items.takeIf { it.isNotEmpty() }?.let {
        ReviewChatStructuredSection(
            title = "依据",
            body = emptyList(),
            items = it,
        )
    }
}

private fun buildRecordEvidenceSection(
    evidence: List<ReviewChatEvidenceItem>,
): ReviewChatStructuredSection? =
    evidence.takeIf { it.isNotEmpty() }?.let { items ->
        ReviewChatStructuredSection(
            title = "记录",
            body = emptyList(),
            items = items.map { item ->
                "${item.dateLabel}《${item.title}》：${item.summary}"
            },
        )
    }

private fun buildFullRecordSection(
    details: List<ReviewChatRawNoteDetail>,
): ReviewChatStructuredSection? =
    details.takeIf { it.isNotEmpty() }?.let { records ->
        ReviewChatStructuredSection(
            title = "完整记录",
            body = records.map { detail ->
                "${detail.dateLabel}《${detail.title}》\n${detail.fullContent.trim()}"
            },
            items = emptyList(),
        )
    }

private fun buildTimelineSection(
    anchors: List<ReviewChatTimelineAnchor>,
): ReviewChatStructuredSection? =
    anchors.takeIf { it.isNotEmpty() }?.let { items ->
        ReviewChatStructuredSection(
            title = "时间线",
            body = emptyList(),
            items = items.map { anchor ->
                "${anchor.label}：${anchor.item.dateLabel}《${anchor.item.title}》：${anchor.item.summary}"
            },
        )
    }

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
