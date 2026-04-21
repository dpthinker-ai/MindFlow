package com.mindflow.app.data.reviewchat

object ReviewChatPromptFactory {
    private const val ON_DEVICE_PROMPT_CHAR_BUDGET = 1_800

    fun cloud(packet: ReviewChatContextPacket): String = buildPrompt(packet)

    fun onDevice(packet: ReviewChatContextPacket): ReviewChatOnDevicePrompt = buildOnDevicePrompt(packet)

    private fun buildPrompt(packet: ReviewChatContextPacket): String = buildString {
        appendLine(
            when (packet.questionMode) {
                ReviewChatQuestionMode.EXTERNAL -> "你正在回答一个通用问题，不要假装拥有实时外部信息。"
                else -> "你正在回答一个基于个人历史记录的回看问题。"
            }
        )
        appendLine("当前问题：${packet.question}")
        appendLine("问题路径：${packet.questionMode.name}")
        appendLine("问题类型：${packet.intent.name}")

        appendPromptSection(this, "近期会话摘要", packet.sessionSummary.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty())
        appendPromptSection(this, "近期会话", packet.conversationSnippets)
        appendPromptSection(this, "历史锚点", packet.historyAnchorSnippets)
        appendPromptSection(this, "LM Knowledge Base", packet.knowledgeBaseSnippets)
        appendPromptSection(this, "LLM Wiki", packet.wikiSnippets)
        appendPromptSection(this, "原始记录", packet.rawNoteSnippets)
        if (packet.rawNoteDetails.isNotEmpty()) {
            appendLine("完整记录：")
            packet.rawNoteDetails.forEach { detail ->
                appendLine("### ${detail.dateLabel}｜${detail.title}")
                appendLine(detail.fullContent)
            }
        }

        appendLine("回答要求：")
        appendLine("1. 先直接回答当前问题，不要重复上一轮。")
        when (packet.questionMode) {
            ReviewChatQuestionMode.EXTERNAL -> {
                appendLine("2. 这是外部或通用问题，不要引用个人历史记录，也不要附历史记录链接。")
                appendLine("3. 如果问题需要实时天气、新闻、股价等信息，要直接说明你无法获取实时数据；如果能回答，就给简短通用建议。")
                appendLine("4. 默认使用 Markdown；如果有多点内容，用项目列表逐条换行。")
            }
            ReviewChatQuestionMode.RECORD_LOOKUP -> {
                appendLine("2. 这是记录查询问题，只根据命中的原始记录回答，不要扩展分析。")
                appendLine("3. 输出格式固定为：先一句总览，然后用项目列表逐条列出，每条单独一行，按时间顺序写。")
                appendLine("4. 不要引用未命中的日期或记录。")
            }
            ReviewChatQuestionMode.FULL_RECORD -> {
                appendLine("2. 这是完整内容问题，优先返回命中的完整记录，不要先做摘要。")
                appendLine("3. 输出格式固定为：先一句说明命中了几条，然后每条用三级标题分开，再贴对应内容。")
            }
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                appendLine("2. 这是时间线锚点问题，第一句必须直接回答最早时间或开始时间。")
                appendLine("3. 如果需要补充依据，用项目列表列 1 到 3 个关键时间锚点。")
            }
            ReviewChatQuestionMode.ANALYSIS -> {
                appendLine("2. 这是分析问题，可以综合原始记录、LM Knowledge Base 和 LLM Wiki。")
                appendLine("3. 输出格式固定为：`## 结论`、`## 依据`、`## 下一步` 三段。")
                appendLine("4. 如果材料跨不同时间，要点出变化，不要只盯最近两天。")
            }
        }
        if (packet.questionMode != ReviewChatQuestionMode.EXTERNAL) {
            appendLine("5. 如果现有材料不足以支持结论，要明确说材料不足，不要假装看过不存在的内容。")
        }
    }

    private fun buildOnDevicePrompt(packet: ReviewChatContextPacket): ReviewChatOnDevicePrompt {
        val prompt = StringBuilder()
        val recentUserSnippets = packet.conversationSnippets
            .filter { it.startsWith("用户｜") }
            .takeLast(2)

        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "当前问题：${compactForOnDevice(packet.question, maxChars = 220)}",
        )

        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "历史锚点",
            lines = packet.historyAnchorSnippets,
            maxItems = 3,
            itemMaxChars = 110,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "最近问题",
            lines = recentUserSnippets,
            maxItems = 2,
            itemMaxChars = 80,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "完整记录",
            lines = packet.rawNoteDetails.map { detail ->
                "${detail.dateLabel}｜${detail.title}｜${compactForOnDevice(detail.fullContent, maxChars = 320)}"
            },
            maxItems = 2,
            itemMaxChars = 380,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "原始记录",
            lines = packet.rawNoteSnippets,
            maxItems = when (packet.intent) {
                ReviewChatIntent.RECALL -> 5
                ReviewChatIntent.DISCUSS -> 4
                ReviewChatIntent.SYNTHESIZE -> 4
            },
            itemMaxChars = 150,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "LM Knowledge Base",
            lines = packet.knowledgeBaseSnippets,
            maxItems = 2,
            itemMaxChars = 120,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "LLM Wiki",
            lines = packet.wikiSnippets,
            maxItems = 3,
            itemMaxChars = 140,
        )

        return ReviewChatOnDevicePrompt(
            systemInstruction = buildString {
                appendLine(
                    when (packet.questionMode) {
                        ReviewChatQuestionMode.EXTERNAL -> "你是一个通用助手，但没有联网和实时外部信息能力。"
                        else -> "你是一个端侧本地历史助手，只能基于给定材料回答。"
                    }
                )
                appendLine("问题路径：${packet.questionMode.name}")
                appendLine("问题类型：${packet.intent.name}")
                appendLine("回答要求：先直接回答当前问题，不要重复上一轮。")
                when (packet.questionMode) {
                    ReviewChatQuestionMode.EXTERNAL -> {
                        appendLine("补充要求：不要引用个人历史记录，也不要给历史记录链接。")
                        appendLine("如果问题需要实时天气、新闻、股价等信息，就明确说明你无法获取实时数据；如果可以，给简短通用建议。")
                        appendLine("输出格式：一到两段清晰中文，不要使用基于历史材料的拒答口径。")
                    }
                    ReviewChatQuestionMode.RECORD_LOOKUP -> {
                        appendLine("补充要求：这是记录查询问题，只列命中的记录，不要扩展成分析。")
                        appendLine("输出格式：先一句总览，再用项目列表逐条换行。")
                    }
                    ReviewChatQuestionMode.FULL_RECORD -> {
                        appendLine("补充要求：这是完整内容问题，优先返回命中的完整记录，不要只给摘要。")
                        appendLine("输出格式：先一句说明命中了几条，再逐条分段展开。")
                    }
                    ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                        appendLine("补充要求：这是时间线锚点问题，优先回答最早时间或开始时间。")
                        appendLine("输出格式：第一句直接回答时间，再补 1 到 3 个时间锚点。")
                    }
                    ReviewChatQuestionMode.ANALYSIS -> {
                        appendLine("补充要求：这是分析问题，可以综合原始记录、LM Knowledge Base 和 LLM Wiki。")
                        appendLine("输出格式：`结论`、`依据`、`下一步` 三段，默认用简洁 Markdown。")
                    }
                }
                if (packet.questionMode != ReviewChatQuestionMode.EXTERNAL) {
                    appendLine("材料不足时直接说明材料不足。")
                }
            }.trim(),
            userMessage = prompt.toString().trim(),
            extraContext = mapOf(
                "question_mode" to packet.questionMode.name,
                "intent" to packet.intent.name,
                "is_external_question" to packet.isExternalQuestion.toString(),
                "has_raw_note_details" to packet.rawNoteDetails.isNotEmpty().toString(),
                "raw_note_count" to packet.rawNoteSnippets.size.toString(),
            ),
        )
    }

    private fun appendPromptSection(
        builder: StringBuilder,
        title: String,
        lines: List<String>,
    ) {
        if (lines.isEmpty()) return
        builder.appendLine("$title：")
        lines.forEach(builder::appendLine)
    }

    private fun appendSectionWithinBudget(
        builder: StringBuilder,
        budget: Int,
        title: String,
        lines: List<String>,
        maxItems: Int,
        itemMaxChars: Int,
    ) {
        val normalized = lines
            .asSequence()
            .map { compactForOnDevice(it, maxChars = itemMaxChars) }
            .filter { it.isNotBlank() }
            .take(maxItems)
            .toList()
        if (normalized.isEmpty()) return

        val section = buildString {
            appendLine("$title：")
            normalized.forEach { item ->
                appendLine("- $item")
            }
        }
        appendWithinBudget(builder = builder, budget = budget, line = section.trimEnd())
    }

    private fun appendWithinBudget(
        builder: StringBuilder,
        budget: Int,
        line: String,
    ) {
        if (line.isBlank()) return
        val candidate = if (builder.isEmpty()) line else "${builder}\n$line"
        if (candidate.length <= budget) {
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(line)
        }
    }

    private fun compactForOnDevice(
        text: String,
        maxChars: Int,
    ): String {
        val normalized = text
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length <= maxChars) return normalized
        return normalized.take(maxChars - 1).trimEnd() + "…"
    }
}
