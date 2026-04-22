package com.mindflow.app.data.reviewchat

import com.mindflow.app.markdown.SimpleMarkdown

internal fun normalizeReviewChatAnswerForDisplay(content: String): String {
    val normalizedInput = SimpleMarkdown.normalizeForDisplay(content).trim()
    if (normalizedInput.isBlank()) return normalizedInput

    val rendered = parseStructuredReviewChatResponse(normalizedInput)
        ?.let(::renderStructuredReviewChatResponse)
        ?: normalizedInput

    var normalized = normalizeLegacyReviewChatMarkdown(rendered)
    if (normalized.isBlank()) return normalized

    normalized = normalized
        .replace(Regex("(?m)^(#{1,6})\\s*(结论|依据|下一步)(?=\\S)"), "$1 $2\n")
        .replace(Regex("(?m)^(结论|依据|下一步)(?=[^：:\\n])"), "$1：")
        .replace(Regex("(?m)^(结论|依据|下一步)(?=(根据|原始|如果|当前|需要|可以|没有|已|先))"), "$1：")
        .replace(Regex("(?<!\\n)(#{1,6}\\s)"), "\n$1")
        .replace(Regex("(?<!\\n)(\\d+\\.\\s+)"), "\n$1")
        .replace(Regex("(?<=[\\n：:；;。！？])(\\d+[、）)](?:[ \\t]+)?)"), "\n$1")
        .replace(Regex("(?<!\\n)(-\\s+\\d{4}-\\d{2}-\\d{2}《)"), "\n$1")
        .replace(Regex("(?<!\\n)\\*(?=\\d{4}-\\d{2}-\\d{2}《)"), "\n- ")
        .replace(Regex("(?<!\\n)(?<![ \\t])([-*•][ \\t]+)"), "\n$1")
        .replace(Regex("(?<!\\n)([一二三四五六七八九十]+、)"), "\n$1")
        .replace(Regex("([。！？；])((?:结论|依据|下一步))(?=(\\n|\\d+\\.\\s+|\\d+[、）)]|建议|如果|根据|原始|当前|需要|可以|没有|已|先))"), "$1\n\n$2：")
        .replace(Regex("(?<=[。！？；])((?:依据|下一步)(?=(原始|根据|如果|当前|需要|可以|没有|已|先)))"), "\n$1")
        .replace(Regex("(?m)^(结论|依据|下一步)(?=(根据|原始|如果|当前|需要|可以|没有|已|先))"), "$1：")
        .replace(Regex("([；;])(?=(另外|同时|其次|然后|最后|补充|还有|\\d+[、）)](?:[ \\t]+)?|\\d+\\.\\s+|[-*•][ \\t]+|[一二三四五六七八九十]+、))"), "$1\n")
        .replace(Regex("([。！？；])(?=(#{1,6}\\s|\\d+\\.\\s+|[-*•][ \\t]+|[一二三四五六七八九十]+、|(?:结论|依据|下一步)[：:]))"), "$1\n")
        .replace(Regex("([。！？])\\n(?=(\\d+\\.\\s+|[-*•][ \\t]+|[一二三四五六七八九十]+、|(?:结论|依据|下一步)[：:]))"), "$1\n\n")
        .replace(Regex("(?<!\\n)((?:结论|依据|下一步)[：:])"), "\n$1")
        .replace(Regex("(?m)^[ \\t]*\\*{1,3}[ \\t]*$"), "")
        .replace(Regex("(?<=\\S)(\\*{1,2})(?=\\s*(?:\\n|$))"), "")
        .replace(Regex("(?<=\\S)\\s+(\\*{1,2})(?=\\s*(?:\\n|$))"), "")
        .replace(Regex("(?:(?<=\\n)|^)(\\*{1,2})(?=\\S)"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    if (!normalized.contains('\n') && normalized.length > 140) {
        normalized = normalized
            .replace(Regex("([。！？])(?=(另外|同时|其次|然后|最后|补充|还有))"), "$1\n\n")
            .replace(Regex("([。！？])(?=(\\d+\\.\\s+|\\d+[、）)]\\s*|[一二三四五六七八九十]+、))"), "$1\n\n")
            .trim()
    }

    return normalized
}

private fun normalizeLegacyReviewChatMarkdown(content: String): String = normalizeEvidenceEchoes(
    normalizeInlineCategorySectionBullets(
        normalizeRecordSummarySubBullets(normalizeMarkdownTables(content))
    )
)

private fun parseStructuredReviewChatResponse(content: String): ReviewChatStructuredResponse? {
    val prepared = content
        .replace(Regex("(?<!\\n)(【(?:答复|结论|依据|下一步|记录|完整记录|时间线|类别)】)"), "\n$1")
        .trim()
    if (!STRUCTURED_SECTION_REGEX.containsMatchIn(prepared)) return null

    val sections = mutableListOf<MutableStructuredSection>()
    var current: MutableStructuredSection? = null

    prepared.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach

        val sectionMatch = STRUCTURED_SECTION_REGEX.matchEntire(trimmed)
        if (sectionMatch != null) {
            current = MutableStructuredSection(title = sectionMatch.groupValues[1])
                .also(sections::add)
            sectionMatch.groupValues[2]
                .trim()
                .takeIf(String::isNotBlank)
                ?.let { current.appendStructuredLine(it) }
            return@forEach
        }

        val target = current ?: return@forEach
        val bulletContent = BULLET_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
            ?: ORDERED_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
        if (bulletContent != null) {
            target.items += bulletContent.trim()
        } else {
            target.appendStructuredLine(trimmed)
        }
    }

    return sections.takeIf { it.isNotEmpty() }?.let { parsed ->
        ReviewChatStructuredResponse(
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

private fun renderStructuredReviewChatResponse(
    response: ReviewChatStructuredResponse,
): String = buildString {
    response.sections.forEachIndexed { index, section ->
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

private fun normalizeEvidenceEchoes(content: String): String {
    if (!content.contains("记录｜")) return content

    val separated = content
        .replace(Regex("([：。！？；])(?=-?记录｜)"), "$1\n")
        .replace(Regex("(?<!\\n)(-?记录｜)"), "\n$1")
        .trim()

    val evidenceRegex = Regex("(?m)^-?记录｜([^｜\\n]+)｜([^｜\\n]+)｜")
    return evidenceRegex.replace(separated) { match ->
        val date = match.groupValues[1].trim()
        val title = match.groupValues[2].trim()
        "- $date《$title》\n  摘要："
    }.trim()
}

private fun normalizeMarkdownTables(content: String): String {
    val lines = content.lines()
    if (lines.none { it.contains('|') }) return content

    val output = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val next = lines.getOrNull(index + 1).orEmpty()
        val isHeader = line.trim().startsWith("|") && line.contains("|")
        val isSeparator = Regex("^\\s*\\|?(\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$").matches(next)
        if (isHeader && isSeparator) {
            val headers = splitTableLine(line)
            index += 2
            while (index < lines.size) {
                val rowLine = lines[index]
                if (!rowLine.trim().startsWith("|") || !rowLine.contains("|")) break
                val cells = splitTableLine(rowLine)
                if (cells.isNotEmpty()) {
                    val row = headers.zip(cells).joinToString("；") { (header, cell) ->
                        "$header：$cell"
                    }
                    output += "- $row"
                }
                index += 1
            }
            continue
        }
        output += line
        index += 1
    }
    return output.joinToString("\n")
}

private fun normalizeRecordSummarySubBullets(content: String): String {
    val lines = content.lines()
    if (lines.none { it.startsWith("* ") || it.startsWith("• ") }) return content

    val rewritten = mutableListOf<String>()
    var insideRecordItem = false

    lines.forEach { line ->
        when {
            RECORD_ITEM_REGEX.matches(line.trim()) -> {
                insideRecordItem = true
                rewritten += line
            }
            insideRecordItem && line.startsWith("* ") -> {
                rewritten += "  - " + line.removePrefix("* ").trim()
            }
            insideRecordItem && line.startsWith("• ") -> {
                rewritten += "  - " + line.removePrefix("• ").trim()
            }
            line.isBlank() -> {
                insideRecordItem = false
                rewritten += line
            }
            !line.startsWith(" ") && !line.startsWith("\t") -> {
                insideRecordItem = false
                rewritten += line
            }
            else -> {
                rewritten += line
            }
        }
    }

    return rewritten.joinToString("\n")
}

private fun normalizeInlineCategorySectionBullets(content: String): String {
    val lines = content.lines()
    if (lines.none { it.trimStart().startsWith("类别：") }) return content

    val rewritten = mutableListOf<String>()
    var insideCategorySection = false

    lines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("类别：") -> {
                insideCategorySection = true
                rewritten += "类别："
                val remainder = trimmed.removePrefix("类别：").trim()
                if (remainder.isNotBlank()) {
                    rewritten += splitInlineBulletItems(remainder).map { "- $it" }
                }
            }
            insideCategorySection && trimmed.isBlank() -> {
                insideCategorySection = false
                rewritten += line
            }
            insideCategorySection && SECTION_LABEL_REGEX.matches(trimmed) -> {
                insideCategorySection = false
                rewritten += line
            }
            insideCategorySection -> {
                val items = splitInlineBulletItems(trimmed)
                if (items.isNotEmpty()) {
                    rewritten += items.map { "- $it" }
                } else {
                    rewritten += line
                }
            }
            else -> rewritten += line
        }
    }

    return rewritten.joinToString("\n")
}

private fun splitTableLine(line: String): List<String> =
    line.trim()
        .trim('|')
        .split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() }

private data class ReviewChatStructuredResponse(
    val sections: List<ReviewChatStructuredSection>,
)

private data class ReviewChatStructuredSection(
    val title: String,
    val body: List<String>,
    val items: List<String>,
)

private data class MutableStructuredSection(
    val title: String,
    val body: MutableList<String> = mutableListOf(),
    val items: MutableList<String> = mutableListOf(),
) {
    fun appendFreeLine(line: String) {
        if (line.isBlank()) return
        if (items.isNotEmpty()) {
            val lastIndex = items.lastIndex
            items[lastIndex] = items[lastIndex] + " " + line
        } else {
            body += line
        }
    }

    fun appendStructuredLine(line: String) {
        if (line.isBlank()) return
        if (title in ITEM_SECTION_TITLES) {
            val itemsFromLine = splitInlineBulletItems(line)
            if (itemsFromLine.isNotEmpty()) {
                items += itemsFromLine
                return
            }
        }
        appendFreeLine(line)
    }
}

private val STRUCTURED_SECTION_REGEX =
    Regex(
        pattern = "^【(答复|结论|依据|下一步|记录|完整记录|时间线|类别)】\\s*(.*)$",
        option = RegexOption.MULTILINE,
    )

private val BULLET_LINE_REGEX = Regex("^[-*•]\\s*(.+)$")

private val ORDERED_LINE_REGEX =
    Regex("^(?:\\d+[.、）)]|[一二三四五六七八九十]+、)\\s*(.+)$")

private val RECORD_ITEM_REGEX = Regex("^-\\s+\\d{4}-\\d{2}-\\d{2}《.+》.*$")

private val SECTION_LABEL_REGEX =
    Regex("^(答复|结论|依据|下一步|记录|完整记录|时间线|类别)：.*$")

private val ITEM_SECTION_TITLES = setOf("依据", "下一步", "记录", "完整记录", "时间线", "类别")

private fun splitInlineBulletItems(content: String): List<String> {
    val exploded = explodeInlineBulletMarkers(content).trim()
    if (!exploded.contains('\n') && !BULLET_LINE_REGEX.matches(exploded)) return emptyList()

    return exploded
        .lines()
        .mapNotNull { line ->
            BULLET_LINE_REGEX.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
        }
}

private fun explodeInlineBulletMarkers(content: String): String =
    content.replace(
        Regex("(?<!\\n)(?<!\\d)([-*•])(?=\\s*[\\p{IsHan}A-Z《【])"),
        "\n$1",
    )
