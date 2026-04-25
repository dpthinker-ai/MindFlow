package com.mindflow.app.data.reviewchat

internal object ReviewChatHistorySkill {
    private const val SKILL_ID = "history-query"
    private const val SCRIPT_NAME = "skills/history-query/SKILL.md"

    fun run(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): ReviewChatSkillResult? {
        if (query.isExternalQuestion) return null

        val invocation = ReviewChatSkillInvocation(
            skillId = SKILL_ID,
            scriptName = SCRIPT_NAME,
            intent = query.toSkillIntent(),
            query = query.question,
            timeScopeLabel = query.timeScope.toSkillScopeLabel(),
            needsCard = false,
            modelPass = query.toModelPass(),
        )
        val facts = ReviewChatSkillFacts(
            coverage = buildCoverage(query, corpusContext),
            deterministicResults = corpusContext.deterministicAnswerSnippets,
            categoryBatches = corpusContext.categoryDigestSnippets,
            recordPreview = corpusContext.rawNoteEvidence.map { evidence ->
                ReviewChatSkillRecord(
                    id = evidence.noteId,
                    dateLabel = evidence.dateLabel,
                    title = evidence.title,
                    summary = evidence.summary,
                )
            },
        )
        return ReviewChatSkillResult(
            invocation = invocation,
            facts = facts,
            responseRules = buildResponseRules(query),
        )
    }

    fun toPromptLines(
        result: ReviewChatSkillResult,
        maxCategoryBatches: Int,
        maxRecords: Int,
    ): List<String> = buildList {
        val coverage = result.facts.coverage
        add("skill｜${result.invocation.skillId}")
        add("script｜${result.invocation.scriptName}")
        add("intent｜${result.invocation.intent}")
        add("scope｜${coverage.timeScopeLabel}")
        add(
            buildString {
                append("coverage｜范围内 ")
                append(coverage.scopedCount)
                append(" 条；命中 ")
                append(coverage.matchedCount)
                append(" 条；已处理 ")
                append(coverage.processedCount)
                append(" 条；完整覆盖=")
                append(coverage.complete)
                if (coverage.startDateLabel != null && coverage.endDateLabel != null) {
                    append("；时间跨度 ")
                    append(coverage.startDateLabel)
                    append("~")
                    append(coverage.endDateLabel)
                }
                coverage.nextCursor?.let { append("；nextCursor=$it") }
            }
        )
        result.facts.deterministicResults.forEach { add("result｜$it") }
        result.facts.categoryBatches
            .take(maxCategoryBatches)
            .forEach { add("category_batch｜$it") }
        result.facts.recordPreview
            .take(maxRecords)
            .forEach { record ->
                add("record｜${record.dateLabel}《${record.title}》：${record.summary}")
            }
        result.responseRules.forEach { add("rule｜$it") }
        result.error?.let { add("error｜$it") }
    }

    private fun buildCoverage(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): ReviewChatSkillCoverage {
        val scopedNotes = corpusContext.selection.scopedNotes
        val queryNotes = corpusContext.selection.queryNotes
        val processedCount = when {
            query.wantsCategories -> queryNotes.size
            query.operation in setOf(
                ReviewChatQueryOperation.COUNT,
                ReviewChatQueryOperation.LIST,
                ReviewChatQueryOperation.TIMELINE,
                ReviewChatQueryOperation.FULL_TEXT,
            ) -> queryNotes.size
            else -> corpusContext.rawNoteEvidence.size
        }
        val sorted = queryNotes.ifEmpty { scopedNotes }.sortedBy { it.createdAt }
        return ReviewChatSkillCoverage(
            timeScopeLabel = query.timeScope.toSkillScopeLabel(),
            scopedCount = scopedNotes.size,
            matchedCount = queryNotes.size,
            processedCount = processedCount,
            complete = processedCount == queryNotes.size,
            startDateLabel = sorted.firstOrNull()?.createdLocalDate()?.format(reviewChatDateFormatter),
            endDateLabel = sorted.lastOrNull()?.createdLocalDate()?.format(reviewChatDateFormatter),
            nextCursor = null,
        )
    }

    private fun buildResponseRules(query: ReviewChatParsedQuery): List<String> = buildList {
        add("只回答用户当前问题，不要主动添加“依据”或“下一步”。")
        add("用户没有明确要原始链接时，不要输出历史记录链接。")
        if (query.wantsCategories) {
            add("分类必须覆盖 coverage 里的全部命中记录，每个类别单独成一条。")
            add("不要把“时间范围”“统计信息”“历史记录”“查询结果”当成类别。")
        }
        if (query.wantsBriefAnswer) {
            add("用户要简短总结，只输出几句话，不要展开证据列表。")
        }
    }

    private fun ReviewChatParsedQuery.toSkillIntent(): String = when {
        wantsCategories -> "classify_history_records"
        wantsCount -> "count_history_records"
        wantsFullRecord -> "read_full_history_records"
        wantsTimelineAnchor -> "find_history_anchor"
        operation == ReviewChatQueryOperation.ANALYZE -> "analyze_history_records"
        else -> "lookup_history_records"
    }

    private fun ReviewChatParsedQuery.toModelPass(): ReviewChatSkillModelPass = when {
        wantsCategories || operation == ReviewChatQueryOperation.ANALYZE -> ReviewChatSkillModelPass.ANALYZE_RECORDS
        wantsCount || operation == ReviewChatQueryOperation.LIST || wantsTimelineAnchor ->
            ReviewChatSkillModelPass.SUMMARIZE_COMPACT_RESULT
        else -> ReviewChatSkillModelPass.NONE
    }

    private fun ReviewChatTimeScope.toSkillScopeLabel(): String = when (this) {
        ReviewChatTimeScope.AllTime -> "全部历史"
        is ReviewChatTimeScope.Day -> date.format(reviewChatDateFormatter)
        is ReviewChatTimeScope.Month -> "${month.monthValue}月"
        is ReviewChatTimeScope.Range -> label
    }
}
