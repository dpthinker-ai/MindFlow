package com.mindflow.app.data.reviewchat

object ReviewChatPromptFactory {
    private const val ON_DEVICE_PROMPT_CHAR_BUDGET = 1_800

    fun cloud(packet: ReviewChatContextPacket): String = buildPrompt(packet)

    fun onDevice(packet: ReviewChatContextPacket): ReviewChatOnDevicePrompt = buildOnDevicePrompt(packet)

    private fun buildPrompt(packet: ReviewChatContextPacket): String = buildString {
        appendLine("你正在回答一个基于个人历史记录的回看问题。")
        appendLine("问题路径：${packet.questionMode.name}")
        appendLine("问题类型：${packet.intent.name}")
        appendLine("当前问题：${packet.question}")
        packet.sessionSummary.takeIf { it.isNotBlank() }?.let {
            appendLine("近期会话摘要：$it")
        }
        if (packet.conversationSnippets.isNotEmpty()) {
            appendLine("近期会话：")
            packet.conversationSnippets.forEach(::appendLine)
        }
        if (packet.historyAnchorSnippets.isNotEmpty()) {
            appendLine("历史锚点：")
            packet.historyAnchorSnippets.forEach(::appendLine)
        }
        if (packet.memoryDigestSnippets.isNotEmpty()) {
            appendLine("Memory Digest：")
            packet.memoryDigestSnippets.forEach(::appendLine)
        }
        if (packet.memoryThreadSnippets.isNotEmpty()) {
            appendLine("Memory Thread：")
            packet.memoryThreadSnippets.forEach(::appendLine)
        }
        if (packet.knowledgeBaseSnippets.isNotEmpty()) {
            appendLine("LM Knowledge Base：")
            packet.knowledgeBaseSnippets.forEach(::appendLine)
        }
        if (packet.wikiSnippets.isNotEmpty()) {
            appendLine("LLM Wiki：")
            packet.wikiSnippets.forEach(::appendLine)
        }
        if (packet.rawNoteSnippets.isNotEmpty()) {
            appendLine("原始记录：")
            packet.rawNoteSnippets.forEach(::appendLine)
        }
        if (packet.rawNoteDetails.isNotEmpty()) {
            appendLine("完整记录：")
            packet.rawNoteDetails.forEach { detail ->
                appendLine("- noteId=${detail.noteId}｜${detail.dateLabel}｜${detail.title}｜${detail.fullContent}")
            }
        }
        appendLine("回答要求：")
        appendLine("1. 先直接回答“当前问题”，不要重复上一轮回答。")
        when (packet.questionMode) {
            ReviewChatQuestionMode.RECORD_LOOKUP -> {
                appendLine("2. 这是记录查询问题，只根据命中的原始记录回答，不要扩展成分析。")
                appendLine("3. 如果命中了多条记录，用项目列表逐条列出，按时间顺序写。")
            }
            ReviewChatQuestionMode.FULL_RECORD -> {
                appendLine("2. 这是完整内容问题，优先返回命中的完整记录内容，不要先做摘要。")
                appendLine("3. 如果命中了多条记录，按时间顺序逐条展开。")
            }
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                appendLine("2. 这是时间线锚点问题，优先根据历史锚点和原始记录日期回答。")
                appendLine("3. 回答里必须直接点出最早时间或开始时间，不要泛泛总结。")
            }
            ReviewChatQuestionMode.ANALYSIS -> {
                appendLine("2. 这是分析问题，优先基于原始记录、LM Knowledge Base 和 LLM Wiki 回答。")
                appendLine("3. 如果材料跨了不同时间，点出时间变化，不要只盯最近两天。")
            }
        }
        appendLine("4. 如果现有材料不足以支持结论，要明确说材料不足，不要假装看过不存在的内容。")
        appendLine("5. 用中文回答，结构清楚，避免空话。")
        appendLine("6. 默认使用清晰的 Markdown 排版：先给一两句结论；如果涉及多条记录或多个时间点，用项目列表，每条单独一行。")
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
                "noteId=${detail.noteId}｜${detail.dateLabel}｜${detail.title}｜${compactForOnDevice(detail.fullContent, maxChars = 320)}"
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
                appendLine("你是一个端侧本地历史助手，只能基于给定的个人记录、LM Knowledge Base 和 LLM Wiki 回答。")
                appendLine("问题路径：${packet.questionMode.name}")
                appendLine("问题类型：${packet.intent.name}")
                appendLine("回答要求：先直接回答当前问题，不要重复上一轮。")
                when (packet.questionMode) {
                    ReviewChatQuestionMode.RECORD_LOOKUP -> {
                        appendLine("补充要求：这是记录查询问题，只列命中的记录，不要扩展成分析。")
                    }
                    ReviewChatQuestionMode.FULL_RECORD -> {
                        appendLine("补充要求：这是完整内容问题，优先返回命中的完整记录，不要只给摘要。")
                    }
                    ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                        appendLine("补充要求：这是时间线锚点问题，优先回答最早时间或开始时间。")
                    }
                    ReviewChatQuestionMode.ANALYSIS -> {
                        appendLine("补充要求：这是分析问题，可以综合原始记录、LM Knowledge Base 和 LLM Wiki。")
                    }
                }
                appendLine("材料不足时直接说明材料不足。")
                appendLine("格式要求：默认用简洁 Markdown；如果涉及多条记录或多个时间点，用项目列表逐条换行。")
            }.trim(),
            userMessage = prompt.toString().trim(),
            extraContext = mapOf(
                "question_mode" to packet.questionMode.name,
                "intent" to packet.intent.name,
                "has_raw_note_details" to packet.rawNoteDetails.isNotEmpty().toString(),
                "raw_note_count" to packet.rawNoteSnippets.size.toString(),
            ),
        )
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
            append(title)
            append("：")
            append(normalized.joinToString("；"))
        }
        appendWithinBudget(builder = builder, budget = budget, line = section)
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
