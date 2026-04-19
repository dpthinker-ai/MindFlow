package com.mindflow.app.data.reviewchat

object ReviewChatPromptFactory {
    private const val ON_DEVICE_PROMPT_CHAR_BUDGET = 1_800

    fun cloud(packet: ReviewChatContextPacket): String = buildPrompt(packet)

    fun onDevice(packet: ReviewChatContextPacket): String = buildOnDevicePrompt(packet)

    private fun buildPrompt(packet: ReviewChatContextPacket): String = buildString {
        appendLine("你正在回答一个基于个人历史记录的回看问题。")
        appendLine("问题类型：${packet.intent.name}")
        appendLine("当前问题：${packet.question}")
        packet.sessionSummary.takeIf { it.isNotBlank() }?.let {
            appendLine("近期会话摘要：$it")
        }
        if (packet.conversationSnippets.isNotEmpty()) {
            appendLine("近期会话：")
            packet.conversationSnippets.forEach(::appendLine)
        }
        if (packet.memoryDigestSnippets.isNotEmpty()) {
            appendLine("Memory Digest：")
            packet.memoryDigestSnippets.forEach(::appendLine)
        }
        if (packet.memoryThreadSnippets.isNotEmpty()) {
            appendLine("Memory Thread：")
            packet.memoryThreadSnippets.forEach(::appendLine)
        }
        appendLine("LM Knowledge Base：")
        packet.knowledgeBaseSnippets.forEach(::appendLine)
        appendLine("LLM Wiki：")
        packet.wikiSnippets.forEach(::appendLine)
        appendLine("原始记录：")
        packet.rawNoteSnippets.forEach(::appendLine)
        if (packet.rawNoteDetails.isNotEmpty()) {
            appendLine("完整记录：")
            packet.rawNoteDetails.forEach { detail ->
                appendLine("- noteId=${detail.noteId}｜${detail.dateLabel}｜${detail.title}｜${detail.fullContent}")
            }
        }
        appendLine("回答要求：")
        appendLine("1. 先直接回答“当前问题”，不要重复上一轮回答。")
        appendLine("2. 优先基于 Memory Layer、本地知识压缩和结构化沉淀回答，再用原始记录补细节。")
        appendLine("3. 如果材料跨了不同时间，点出时间变化，不要只盯最近两天。")
        appendLine("4. 如果用户明确要完整内容，优先返回完整记录内容，而不是只做摘要。")
        appendLine("5. 如果现有材料不足以支持结论，要明确说材料不足，不要假装看过不存在的内容。")
        appendLine("6. 用中文回答，结构清楚，避免空话。")
    }

    private fun buildOnDevicePrompt(packet: ReviewChatContextPacket): String {
        val prompt = StringBuilder()

        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "你是一个端侧本地历史助手，只能基于给定的个人记录、Memory Layer 和知识沉淀回答。",
        )
        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "问题类型：${packet.intent.name}",
        )
        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "当前问题：${compactForOnDevice(packet.question, maxChars = 220)}",
        )
        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "回答要求：先直接回答当前问题，不要重复上一轮。",
        )
        appendWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            line = "补充要求：优先利用 Memory Thread / Digest，再用原始记录补细节；如果材料不足就明确说材料不足；如果用户明确要完整内容，优先返回完整记录。",
        )
        packet.sessionSummary.takeIf { it.isNotBlank() }?.let {
            appendWithinBudget(
                builder = prompt,
                budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
                line = "近期会话摘要：${compactForOnDevice(it, maxChars = 180)}",
            )
        }

        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "近期会话",
            lines = packet.conversationSnippets.takeLast(4),
            maxItems = 4,
            itemMaxChars = 120,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "Memory Digest",
            lines = packet.memoryDigestSnippets,
            maxItems = when (packet.intent) {
                ReviewChatIntent.RECALL -> 4
                ReviewChatIntent.DISCUSS -> 3
                ReviewChatIntent.SYNTHESIZE -> 3
            },
            itemMaxChars = 140,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "Memory Thread",
            lines = packet.memoryThreadSnippets,
            maxItems = when (packet.intent) {
                ReviewChatIntent.RECALL -> 4
                ReviewChatIntent.DISCUSS -> 4
                ReviewChatIntent.SYNTHESIZE -> 4
            },
            itemMaxChars = 160,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "LM Knowledge Base",
            lines = packet.knowledgeBaseSnippets,
            maxItems = 3,
            itemMaxChars = 120,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "LLM Wiki",
            lines = packet.wikiSnippets,
            maxItems = 4,
            itemMaxChars = 140,
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
            itemMaxChars = 140,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "完整记录",
            lines = packet.rawNoteDetails.map { detail ->
                "noteId=${detail.noteId}｜${detail.dateLabel}｜${detail.title}｜${compactForOnDevice(detail.fullContent, maxChars = 260)}"
            },
            maxItems = 2,
            itemMaxChars = 320,
        )

        return prompt.toString().trim()
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
