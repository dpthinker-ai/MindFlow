package com.mindflow.app.data.reviewchat

internal object ReviewChatModelQueryPlanner {
    fun parse(content: String): ReviewChatModelQueryPlan? {
        val jsonText = content.trim()
        if (!jsonText.startsWith("{")) return null

        val operation = OPERATION_REGEX.find(jsonText)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.lowercase()
            ?.toOperation()
            ?: return null

        return ReviewChatModelQueryPlan(
            operation = operation,
            entityTerms = parseEntityTerms(jsonText),
            wantsCategories = parseBoolean(jsonText, "wants_categories"),
            wantsExamples = parseBoolean(jsonText, "wants_examples"),
            wantsLinks = parseBoolean(jsonText, "wants_links"),
        )
    }

    fun buildPlanningPrompt(question: String): String = buildString {
        appendLine("用户问题：$question")
        appendLine("请把这个问题规划成一个简短 JSON。")
        appendLine("只返回一个 JSON 对象，不要解释，不要 markdown，不要代码块。")
        appendLine("JSON schema:")
        appendLine("{\"operation\":\"external|count|list|full_text|timeline|analyze\",\"entity_terms\":[\"string\"],\"wants_categories\":true|false,\"wants_examples\":true|false,\"wants_links\":true|false}")
        appendLine("规则：")
        appendLine("1. 如果问题是在问总数、数量、跨度、第一条、完整内容、分类、分析，请优先判断任务，不要误提炼无关主题词。")
        appendLine("2. 如果问题是在问“所有记录”“全部历史”“整体分类”，entity_terms 必须是空数组。")
        appendLine("3. 只有当问题明确指定了主题，例如“抖音”“人生态度”“产品方向”，才把主题放进 entity_terms。")
        appendLine("4. “有哪些类别/分类/归类” 这类问题，operation 用 list，同时 wants_categories=true。")
        appendLine("5. “第一条/最早/什么时候开始” 用 timeline。")
        appendLine("6. “完整内容/全文/原文” 用 full_text。")
        appendLine("7. 外部实时问题如天气、新闻、股价，用 external。")
    }

    private fun parseEntityTerms(jsonText: String): List<String> {
        val body = ENTITY_TERMS_REGEX.find(jsonText)?.groupValues?.get(1)?.trim().orEmpty()
        if (body.isBlank()) return emptyList()

        return body.split(',')
            .map { it.trim().trim('"') }
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun parseBoolean(
        jsonText: String,
        key: String,
    ): Boolean = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        .find(jsonText)
        ?.groupValues
        ?.get(1)
        ?.equals("true", ignoreCase = true)
        ?: false

    private fun String.toOperation(): ReviewChatQueryOperation? = when (this) {
        "external" -> ReviewChatQueryOperation.EXTERNAL
        "count" -> ReviewChatQueryOperation.COUNT
        "list" -> ReviewChatQueryOperation.LIST
        "full_text" -> ReviewChatQueryOperation.FULL_TEXT
        "timeline" -> ReviewChatQueryOperation.TIMELINE
        "analyze" -> ReviewChatQueryOperation.ANALYZE
        else -> null
    }

    private val OPERATION_REGEX =
        Regex("\"operation\"\\s*:\\s*\"([^\"]+)\"")

    private val ENTITY_TERMS_REGEX =
        Regex("\"entity_terms\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL))
}
