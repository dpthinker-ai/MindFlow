package com.mindflow.app.data.reviewchat

import com.mindflow.app.markdown.SimpleMarkdown

internal fun normalizeReviewChatAnswerForDisplay(content: String): String {
    var normalized = normalizeEvidenceEchoes(
        normalizeMarkdownTables(SimpleMarkdown.normalizeForDisplay(content))
    )
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
