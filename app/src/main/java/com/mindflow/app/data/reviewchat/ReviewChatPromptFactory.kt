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

        appendPromptSection(this, "查询结果", packet.querySummarySnippets)
        appendPromptSection(this, "确定结果", packet.deterministicAnswerSnippets)
        appendPromptSection(this, "分类候选", packet.categoryDigestSnippets)
        appendPromptSection(this, "近期会话摘要", packet.sessionSummary.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty())
        appendCollectionOverviewSection(this, packet.collectionOverview)
        appendPromptSection(this, "近期会话", packet.conversationSnippets)
        appendHistoryAnchorSection(this, packet.historyAnchors)
        appendPromptSection(this, "LM Knowledge Base", packet.knowledgeBaseSnippets)
        appendPromptSection(this, "LLM Wiki", packet.wikiSnippets)
        appendEvidenceSection(this, "原始记录", packet.rawNoteEvidence)
        if (packet.rawNoteDetails.isNotEmpty()) {
            appendLine("完整记录：")
            packet.rawNoteDetails.forEach { detail ->
                appendLine("### ${detail.dateLabel}｜${detail.title}")
                appendLine(detail.fullContent)
            }
        }

        appendLine("回答要求：")
        appendLine("0. ${buildReviewChatStructuredOutputSchema(packet.questionMode, packet.wantsCategories)}")
        appendLine("1. 先直接回答当前问题，不要重复上一轮。")
        when (packet.questionMode) {
            ReviewChatQuestionMode.EXTERNAL -> {
                appendLine("2. 这是外部或通用问题，不要引用个人历史记录，也不要附历史记录链接。")
                appendLine("3. 如果问题需要实时天气、新闻、股价等信息，要直接说明你无法获取实时数据；如果能回答，就给简短通用建议。")
                appendLine("4. 输出协议：第一段用 `【答复】` 直接回答；如果要补充建议，再用 `【下一步】`，并把每条建议单独写成 `- ` 列表。")
                appendLine("5. 默认使用 Markdown；不要输出 Markdown 表格，也不要使用 Markdown 标题。")
            }
            ReviewChatQuestionMode.COLLECTION_OVERVIEW -> {
                appendLine("2. 这是全局统计或整体概览问题，优先使用“集合概览”和时间范围信息直接回答。")
                appendLine("3. 如果“确定结果”里有精确数字或精确时间，必须直接使用，不要自己重算。")
                appendLine("4. 如果用户没有明确要求“列出记录/举例/展示命中的记录”，就不要罗列示例记录。")
                if (packet.wantsCategories) {
                    appendLine("5. 这是分类问题，输出协议：先用 `【答复】` 直接回答，再用 `【类别】` 列出主要类别，每条都单独写成 `- 类别：说明`。")
                    appendLine("6. `【类别】` 下每个类别必须独立成行，严禁把多个 `- 类别` 写在同一行。")
                    appendLine("7. 类别只能来自命中原始记录的内容主题，不要把“时间范围”“统计信息”“历史记录”“查询结果”“集合概览”当成类别。")
                    appendLine("8. 不要输出 `【依据】` 或 `【下一步】`，也不要输出 Markdown 表格。")
                } else {
                    appendLine("5. 输出协议：先用 `【答复】` 直接回答；如需解释原因，用 `【依据】`，每条依据单独写成 `- ` 列表。")
                    appendLine("6. 只有在用户明确要求举例或列记录时，才追加 `【记录】`，每条记录写成 `- 日期《标题》：摘要`。不要输出 Markdown 表格，也不要照抄证据里的字段标签，更不要输出 `-记录｜日期｜标题｜摘要` 这种证据格式。")
                }
            }
            ReviewChatQuestionMode.RECORD_LOOKUP -> {
                appendLine("2. 这是记录查询问题，只根据命中的原始记录回答，不要扩展分析。")
                appendLine("3. 如果“确定结果”里已经给出命中条数或范围，先沿用这个精确结果。")
                if (packet.wantsCategories) {
                    appendLine("4. 这是分类查询，输出协议：先用 `【答复】` 做一句总览，再用 `【类别】` 归纳主要类别，每条单独写成 `- 类别：包含的信息`。")
                    appendLine("5. `【类别】` 下每个类别必须独立成行，严禁把多个 `- 类别` 写在同一行。")
                    appendLine("6. 类别只能来自命中原始记录的内容主题，不要把“时间范围”“统计信息”“历史记录”“查询结果”“集合概览”“确定结果”当成类别。")
                    appendLine("7. 优先综合“分类候选”的所有批次，再参考原始记录，不要只根据少量示例记录下结论。")
                    appendLine("8. 如果用户没有明确要求逐条列记录，就不要追加 `【记录】`。不要输出 `【依据】` 或 `【下一步】`，也不要输出 Markdown 表格。")
                } else {
                    appendLine("4. 输出协议：先用 `【答复】` 做一句总览，再用 `【记录】` 按时间顺序逐条列出，每条单独一行。")
                    appendLine("5. `【记录】` 下每条都写成 `- 日期《标题》：摘要`。不要引用未命中的日期或记录，也不要输出 Markdown 表格，不要逐字复述证据中的前缀或分隔格式。")
                }
            }
            ReviewChatQuestionMode.FULL_RECORD -> {
                appendLine("2. 这是完整内容问题，优先返回命中的完整记录，不要先做摘要。")
                appendLine("3. 输出协议：先用 `【答复】` 说明命中了几条，再用 `【完整记录】` 分段展开。")
                appendLine("4. `【完整记录】` 内每条记录单独分段，先写 `日期《标题》`，下一行再贴内容；不要输出 Markdown 表格，也不要使用 Markdown 标题。")
            }
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                appendLine("2. 这是时间线锚点问题，第一句必须直接回答最早时间或开始时间。")
                appendLine("3. 如果“确定结果”里已经给出最早时间或标题，必须直接使用，不要自己推断。")
                appendLine("4. 输出协议：先用 `【答复】` 直接回答时间；如果需要补充依据，再用 `【时间线】`，每条锚点单独写成 `- 日期《标题》：摘要`。")
                appendLine("5. 不要输出 Markdown 表格，也不要使用 Markdown 标题。")
            }
            ReviewChatQuestionMode.ANALYSIS -> {
                appendLine("2. 这是分析问题，可以综合原始记录、LM Knowledge Base 和 LLM Wiki。")
                appendLine("3. 输出协议：固定使用三段 `【答复】`、`【依据】`、`【下一步】`。")
                appendLine("4. `【依据】` 和 `【下一步】` 里的每条内容都单独写成 `- ` 列表，不要写成表格，也不要使用 Markdown 标题。")
                appendLine("5. 如果材料跨不同时间，要点出变化，不要只盯最近两天。")
                appendLine("6. 不要输出 Markdown 表格，改用项目列表或分段。")
            }
        }
        if (packet.questionMode != ReviewChatQuestionMode.EXTERNAL) {
            appendLine("7. 如果现有材料不足以支持结论，要明确说材料不足，不要假装看过不存在的内容。")
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
            title = "查询结果",
            lines = packet.querySummarySnippets,
            maxItems = 4,
            itemMaxChars = 80,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "确定结果",
            lines = packet.deterministicAnswerSnippets,
            maxItems = 3,
            itemMaxChars = 120,
        )
        appendSectionWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "分类候选",
            lines = packet.categoryDigestSnippets,
            maxItems = 8,
            itemMaxChars = 80,
        )

        appendCollectionOverviewWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            overview = packet.collectionOverview,
        )

        appendHistoryAnchorsWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            anchors = packet.historyAnchors,
            maxItems = 3,
            itemMaxChars = 130,
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
        appendEvidenceItemsWithinBudget(
            builder = prompt,
            budget = ON_DEVICE_PROMPT_CHAR_BUDGET,
            title = "原始记录",
            items = packet.rawNoteEvidence,
            maxItems = when (packet.intent) {
                ReviewChatIntent.RECALL -> 5
                ReviewChatIntent.DISCUSS -> 4
                ReviewChatIntent.SYNTHESIZE -> 4
            },
            itemMaxChars = 180,
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
                appendLine(buildReviewChatStructuredOutputSchema(packet.questionMode, packet.wantsCategories))
                appendLine("回答要求：先直接回答当前问题，不要重复上一轮。")
                when (packet.questionMode) {
                    ReviewChatQuestionMode.EXTERNAL -> {
                        appendLine("补充要求：不要引用个人历史记录，也不要给历史记录链接。")
                        appendLine("如果问题需要实时天气、新闻、股价等信息，就明确说明你无法获取实时数据；如果可以，给简短通用建议。")
                        appendLine("输出协议：先写 `【答复】`；如要补充建议，再写 `【下一步】` 并用 `- ` 列表。不要使用基于历史材料的拒答口径，也不要输出表格或 Markdown 标题。")
                    }
                    ReviewChatQuestionMode.COLLECTION_OVERVIEW -> {
                        appendLine("补充要求：这是全局统计或整体概览问题，优先根据集合概览直接回答。")
                        appendLine("如果“确定结果”里有精确数字或精确时间，必须直接使用，不要自己重算。")
                        appendLine("如果用户没有明确要求列出记录，就不要罗列示例记录。")
                        if (packet.wantsCategories) {
                            appendLine("输出协议：先写 `【答复】`，再写 `【类别】`，每条都用 `- 类别：说明`。`【类别】` 下每个类别必须独立成行，严禁把多个 `- 类别` 写在同一行。类别只能来自命中原始记录的内容主题，不要把“时间范围”“统计信息”“历史记录”“查询结果”“集合概览”当成类别。不要输出 `【依据】` 或 `【下一步】`，也不要输出表格。")
                        } else {
                            appendLine("输出协议：先写 `【答复】` 直接给准确数字或时间；如需解释，再写 `【依据】` 并用 `- ` 列表。只有明确要求举例时才加 `【记录】`。不要输出表格，也不要照抄证据字段标签。")
                        }
                    }
                    ReviewChatQuestionMode.RECORD_LOOKUP -> {
                        appendLine("补充要求：这是记录查询问题，只列命中的记录，不要扩展成分析。")
                        appendLine("如果“确定结果”里已经给出命中条数或范围，先沿用这个精确结果。")
                        if (packet.wantsCategories) {
                            appendLine("输出协议：先写 `【答复】`，再写 `【类别】`，每条都用 `- 类别：包含的信息`。`【类别】` 下每个类别必须独立成行，严禁把多个 `- 类别` 写在同一行。类别只能来自命中原始记录的内容主题，不要把“时间范围”“统计信息”“历史记录”“查询结果”“集合概览”“确定结果”当成类别。优先综合“分类候选”的所有批次，再参考原始记录，不要只根据少量示例记录下结论。如果用户没有明确要求逐条列记录，就不要写 `【记录】`。不要输出 `【依据】` 或 `【下一步】`，也不要输出表格。")
                        } else {
                            appendLine("输出协议：先写 `【答复】`，再写 `【记录】`，每条记录都用 `- 日期《标题》：摘要`。不要输出表格，也不要逐字复述证据前缀。")
                        }
                    }
                    ReviewChatQuestionMode.FULL_RECORD -> {
                        appendLine("补充要求：这是完整内容问题，优先返回命中的完整记录，不要只给摘要。")
                        appendLine("输出协议：先写 `【答复】`，再写 `【完整记录】`，逐条分段展开。不要输出表格，也不要使用 Markdown 标题。")
                    }
                    ReviewChatQuestionMode.TIMELINE_ANCHOR -> {
                        appendLine("补充要求：这是时间线锚点问题，优先回答最早时间或开始时间。")
                        appendLine("如果“确定结果”里已经给出最早时间或标题，必须直接使用，不要自己推断。")
                        appendLine("输出协议：先写 `【答复】`，再按需写 `【时间线】`，每条锚点都用 `- 日期《标题》：摘要`。不要输出表格，也不要使用 Markdown 标题。")
                    }
                    ReviewChatQuestionMode.ANALYSIS -> {
                        appendLine("补充要求：这是分析问题，可以综合原始记录、LM Knowledge Base 和 LLM Wiki。")
                        appendLine("输出协议：固定写 `【答复】`、`【依据】`、`【下一步】` 三段。`【依据】` 和 `【下一步】` 下每条都用 `- ` 列表，不要使用 Markdown 标题，也不要输出表格。")
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
                "raw_note_count" to packet.rawNoteEvidence.size.toString(),
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

    private fun appendCollectionOverviewSection(
        builder: StringBuilder,
        overview: ReviewChatCollectionOverview?,
    ) {
        if (overview == null) return
        builder.appendLine("集合概览：")
        builder.appendLine("- 统计范围：${overview.scopeLabel}")
        builder.appendLine("- 记录总数：共 ${overview.totalCount} 条记录")
        if (overview.earliestDateLabel != null && overview.latestDateLabel != null) {
            builder.appendLine("- 时间范围：最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
        }
        if (overview.last7DaysCount != null && overview.last30DaysCount != null) {
            builder.appendLine("- 近期分布：最近 7 天 ${overview.last7DaysCount} 条，最近 30 天 ${overview.last30DaysCount} 条")
        }
    }

    private fun appendHistoryAnchorSection(
        builder: StringBuilder,
        anchors: List<ReviewChatTimelineAnchor>,
    ) {
        if (anchors.isEmpty()) return
        builder.appendLine("历史锚点：")
        anchors.forEach { anchor ->
            builder.appendLine("- ${anchor.label}：${anchor.item.dateLabel}《${anchor.item.title}》")
            builder.appendLine("  摘要：${anchor.item.summary}")
        }
    }

    private fun appendEvidenceSection(
        builder: StringBuilder,
        title: String,
        items: List<ReviewChatEvidenceItem>,
    ) {
        if (items.isEmpty()) return
        builder.appendLine("$title：")
        items.forEach { item ->
            builder.appendLine("- ${item.dateLabel}《${item.title}》")
            builder.appendLine("  摘要：${item.summary}")
        }
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

    private fun appendCollectionOverviewWithinBudget(
        builder: StringBuilder,
        budget: Int,
        overview: ReviewChatCollectionOverview?,
    ) {
        if (overview == null) return
        val section = buildString {
            appendLine("集合概览：")
            appendLine("- 统计范围：${overview.scopeLabel}")
            appendLine("- 记录总数：共 ${overview.totalCount} 条记录")
            if (overview.earliestDateLabel != null && overview.latestDateLabel != null) {
                appendLine("- 时间范围：最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
            }
            if (overview.last7DaysCount != null && overview.last30DaysCount != null) {
                appendLine("- 近期分布：最近 7 天 ${overview.last7DaysCount} 条，最近 30 天 ${overview.last30DaysCount} 条")
            }
        }.trimEnd()
        appendWithinBudget(builder = builder, budget = budget, line = section)
    }

    private fun appendHistoryAnchorsWithinBudget(
        builder: StringBuilder,
        budget: Int,
        anchors: List<ReviewChatTimelineAnchor>,
        maxItems: Int,
        itemMaxChars: Int,
    ) {
        if (anchors.isEmpty()) return
        val section = buildString {
            appendLine("历史锚点：")
            anchors.take(maxItems).forEach { anchor ->
                val summary = compactForOnDevice(anchor.item.summary, maxChars = itemMaxChars)
                appendLine("- ${anchor.label}：${anchor.item.dateLabel}《${anchor.item.title}》")
                appendLine("  摘要：$summary")
            }
        }.trimEnd()
        appendWithinBudget(builder = builder, budget = budget, line = section)
    }

    private fun appendEvidenceItemsWithinBudget(
        builder: StringBuilder,
        budget: Int,
        title: String,
        items: List<ReviewChatEvidenceItem>,
        maxItems: Int,
        itemMaxChars: Int,
    ) {
        if (items.isEmpty()) return
        val section = buildString {
            appendLine("$title：")
            items.take(maxItems).forEach { item ->
                val summary = compactForOnDevice(item.summary, maxChars = itemMaxChars)
                appendLine("- ${item.dateLabel}《${item.title}》")
                appendLine("  摘要：$summary")
            }
        }.trimEnd()
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
