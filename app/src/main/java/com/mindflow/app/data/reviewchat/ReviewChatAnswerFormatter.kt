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
        .replace(Regex("(?<!\\n)([-*•][ \\t]+)"), "\n$1")
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
    normalizeMarkdownTables(content)
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
                ?.let(current::appendFreeLine)
            return@forEach
        }

        val target = current ?: return@forEach
        val bulletContent = BULLET_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
            ?: ORDERED_LINE_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
        if (bulletContent != null) {
            target.items += bulletContent.trim()
        } else {
            target.appendFreeLine(trimmed)
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
}

private val STRUCTURED_SECTION_REGEX =
    Regex(
        pattern = "^【(答复|结论|依据|下一步|记录|完整记录|时间线|类别)】\\s*(.*)$",
        option = RegexOption.MULTILINE,
    )

private val BULLET_LINE_REGEX = Regex("^[-*•]\\s+(.+)$")

private val ORDERED_LINE_REGEX =
    Regex("^(?:\\d+[.、）)]|[一二三四五六七八九十]+、)\\s*(.+)$")
