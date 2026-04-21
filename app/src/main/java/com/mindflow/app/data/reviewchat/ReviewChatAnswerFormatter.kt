package com.mindflow.app.data.reviewchat

import com.mindflow.app.markdown.SimpleMarkdown

internal fun normalizeReviewChatAnswerForDisplay(content: String): String {
    var normalized = SimpleMarkdown.normalizeForDisplay(content)
    if (normalized.isBlank()) return normalized

    normalized = normalized
        .replace(Regex("(?<!\\n)(#{1,6}\\s)"), "\n$1")
        .replace(Regex("(?<!\\n)(\\d+\\.\\s+)"), "\n$1")
        .replace(Regex("(?<!\\n)(\\d+[、）)]\\s*)"), "\n$1")
        .replace(Regex("(?<!\\n)([-*•]\\s+)"), "\n$1")
        .replace(Regex("(?<!\\n)([一二三四五六七八九十]+、)"), "\n$1")
        .replace(Regex("([；;])(?=(另外|同时|其次|然后|最后|补充|还有|\\d+[、）)]\\s*|\\d+\\.\\s+|[-*•]\\s+|[一二三四五六七八九十]+、))"), "$1\n")
        .replace(Regex("([。！？；])(?=(#{1,6}\\s|\\d+\\.\\s+|[-*•]\\s+|[一二三四五六七八九十]+、))"), "$1\n")
        .replace(Regex("([。！？])\\n(?=(\\d+\\.\\s+|[-*•]\\s+|[一二三四五六七八九十]+、))"), "$1\n\n")
        .replace(Regex("(?m)^[ \\t]*\\*{1,3}[ \\t]*$"), "")
        .replace(Regex("(?<=\\S)(\\*{1,2})(?=\\s*(?:\\n|$))"), "")
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
