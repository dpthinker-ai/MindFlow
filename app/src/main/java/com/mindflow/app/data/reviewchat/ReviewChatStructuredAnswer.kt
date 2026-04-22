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
                .takeIf(String::isNotBlank)
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

internal fun renderReviewChatStructuredAnswerAsMarkdown(
    answer: ReviewChatStructuredAnswer,
): String = buildString {
    answer.sections.forEachIndexed { index, section ->
        if (index > 0) appendLine()
        if (section.title == "答复") {
            section.body
                .joinToString("\n")
                .trim()
                .takeIf(String::isNotBlank)
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
                ?.takeIf(String::isNotBlank)
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
