package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.skills.SkillInvocation
import com.mindflow.app.data.skills.SkillJsonValue
import com.mindflow.app.data.skills.SkillMiniJsonParser
import com.mindflow.app.data.skills.SkillModelPass
import com.mindflow.app.data.skills.SkillResult
import com.mindflow.app.data.skills.SkillWebViewSpec

internal object ReviewChatHistorySkill {
    private const val SKILL_ID = "history-query"
    private const val SKILL_ENTRY = "scripts/index.html"
    private const val SCRIPT_NAME = "skills/history-query/scripts/index.html"

    fun run(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): ReviewChatSkillResult? {
        if (query.isExternalQuestion) return null
        return ReviewChatSkillResult(
            invocation = buildInvocation(query),
            facts = ReviewChatSkillFacts(
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
            ),
            responseRules = buildResponseRules(query),
        )
    }

    fun shouldUseRuntime(
        query: ReviewChatParsedQuery,
    ): Boolean = !query.isExternalQuestion &&
        (
            query.wantsCount ||
                query.wantsCategories ||
                query.wantsFullRecord ||
                query.wantsTimelineAnchor ||
                query.mode in setOf(
                    ReviewChatQuestionMode.COLLECTION_OVERVIEW,
                    ReviewChatQuestionMode.RECORD_LOOKUP,
                    ReviewChatQuestionMode.FULL_RECORD,
                    ReviewChatQuestionMode.TIMELINE_ANCHOR,
                )
            )

    fun buildRuntimeInvocation(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): SkillInvocation? {
        if (!shouldUseRuntime(query)) return null
        return SkillInvocation(
            skillId = SKILL_ID,
            scriptName = SKILL_ENTRY,
            data = buildRuntimeInvocationPayload(query, corpusContext),
            modelPass = query.toRuntimeModelPass(),
        )
    }

    fun fromRuntime(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
        runtimeResult: SkillResult,
    ): ReviewChatHistorySkillRuntimeResult? {
        if (!runtimeResult.isSuccess) return null
        val dataRoot = runtimeResult.dataJson
            ?.let { raw -> runCatching { SkillMiniJsonParser(raw).parseObject() }.getOrNull() }
            ?: return null

        val coverage = parseCoverage(dataRoot.objectValue("coverage"), query, corpusContext)
        val records = parseRuntimeRecords(dataRoot.arrayValue("records"))
        val skillResult = ReviewChatSkillResult(
            invocation = buildInvocation(query),
            facts = ReviewChatSkillFacts(
                coverage = coverage,
                deterministicResults = corpusContext.deterministicAnswerSnippets,
                categoryBatches = corpusContext.categoryDigestSnippets,
                recordPreview = records.map { record ->
                    ReviewChatSkillRecord(
                        id = record.id,
                        dateLabel = record.dateLabel,
                        title = record.title,
                        summary = record.summary,
                    )
                }.ifEmpty {
                    corpusContext.rawNoteEvidence.map { evidence ->
                        ReviewChatSkillRecord(
                            id = evidence.noteId,
                            dateLabel = evidence.dateLabel,
                            title = evidence.title,
                            summary = evidence.summary,
                        )
                    }
                },
            ),
            responseRules = buildResponseRules(query),
        )
        return ReviewChatHistorySkillRuntimeResult(
            skillResult = skillResult,
            rawNoteDetails = if (query.mode == ReviewChatQuestionMode.FULL_RECORD) {
                records.mapNotNull(ReviewChatRuntimeRecord::toRawNoteDetail)
            } else {
                emptyList()
            },
            skillWebView = runtimeResult.webview?.toReviewChatSkillWebView(),
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

    private fun buildInvocation(
        query: ReviewChatParsedQuery,
    ): ReviewChatSkillInvocation = ReviewChatSkillInvocation(
        skillId = SKILL_ID,
        scriptName = SCRIPT_NAME,
        intent = query.toSkillIntent(),
        query = query.question,
        timeScopeLabel = query.timeScope.toSkillScopeLabel(),
        needsCard = query.mode in setOf(
            ReviewChatQuestionMode.COLLECTION_OVERVIEW,
            ReviewChatQuestionMode.RECORD_LOOKUP,
            ReviewChatQuestionMode.TIMELINE_ANCHOR,
        ),
        modelPass = query.toModelPass(),
    )

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

    private fun buildRuntimeInvocationPayload(
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): String {
        val pageSize = when (query.mode) {
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> 1
            ReviewChatQuestionMode.FULL_RECORD -> 20
            ReviewChatQuestionMode.RECORD_LOOKUP -> when {
                query.wantsCategories -> 24
                query.wantsExamples -> 12
                else -> 8
            }

            ReviewChatQuestionMode.COLLECTION_OVERVIEW -> 6
            else -> 6
        }
        val sort = when (query.mode) {
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> "created_at_asc"
            else -> "created_at_asc"
        }
        return renderJsonObject(
            linkedMapOf(
                "intent" to runtimeIntent(query),
                "query" to query.question,
                "timeScope" to renderTimeScopePayload(query.timeScope),
                "entityTerms" to corpusContext.selection.entityTerms,
                "pageSize" to pageSize,
                "cursor" to null,
                "includeContent" to (query.mode == ReviewChatQuestionMode.FULL_RECORD),
                "sort" to sort,
            ),
        )
    }

    private fun renderTimeScopePayload(
        scope: ReviewChatTimeScope,
    ): Map<String, Any?> = when (scope) {
        ReviewChatTimeScope.AllTime -> linkedMapOf("type" to "all_time")
        is ReviewChatTimeScope.Day -> linkedMapOf(
            "type" to "day",
            "date" to scope.date.format(reviewChatDateFormatter),
        )

        is ReviewChatTimeScope.Month -> linkedMapOf(
            "type" to "month",
            "month" to scope.month.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
        )

        is ReviewChatTimeScope.Range -> linkedMapOf(
            "type" to "range",
            "start" to scope.start.format(reviewChatDateFormatter),
            "end" to scope.endInclusive.format(reviewChatDateFormatter),
        )
    }

    private fun runtimeIntent(
        query: ReviewChatParsedQuery,
    ): String = when {
        query.wantsCategories -> "categories"
        query.operation == ReviewChatQueryOperation.COUNT -> "count"
        query.operation == ReviewChatQueryOperation.TIMELINE -> "timeline"
        else -> "lookup"
    }

    private fun parseCoverage(
        raw: Map<String, SkillJsonValue>,
        query: ReviewChatParsedQuery,
        corpusContext: ReviewChatCorpusContext,
    ): ReviewChatSkillCoverage {
        val fallback = buildCoverage(query, corpusContext)
        if (raw.isEmpty()) return fallback
        val startDate = raw.objectValue("dateRange").stringValue("start")
        val endDate = raw.objectValue("dateRange").stringValue("end")
        return ReviewChatSkillCoverage(
            timeScopeLabel = query.timeScope.toSkillScopeLabel(),
            scopedCount = raw.numberValue("totalCount")?.toInt() ?: fallback.scopedCount,
            matchedCount = raw.numberValue("matchedCount")?.toInt() ?: fallback.matchedCount,
            processedCount = raw.numberValue("processedCount")?.toInt() ?: fallback.processedCount,
            complete = raw.booleanValue("complete") ?: fallback.complete,
            startDateLabel = startDate ?: fallback.startDateLabel,
            endDateLabel = endDate ?: fallback.endDateLabel,
            nextCursor = raw.stringValue("nextCursor"),
        )
    }

    private fun parseRuntimeRecords(
        rawRecords: List<SkillJsonValue>,
    ): List<ReviewChatRuntimeRecord> = rawRecords.mapNotNull { item ->
        val record = item.objectValues()
        val id = record.stringValue("id")?.toLongOrNull() ?: return@mapNotNull null
        val date = record.stringValue("date") ?: return@mapNotNull null
        val title = record.stringValue("title") ?: return@mapNotNull null
        val summary = record.stringValue("summary").orEmpty()
        ReviewChatRuntimeRecord(
            id = id,
            dateLabel = date,
            title = title,
            summary = summary,
            content = record.stringValue("content"),
        )
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

    private fun ReviewChatParsedQuery.toRuntimeModelPass(): SkillModelPass = when {
        wantsCategories || operation == ReviewChatQueryOperation.ANALYZE -> SkillModelPass.ANALYZE_RECORDS
        wantsCount || operation == ReviewChatQueryOperation.LIST || wantsTimelineAnchor ->
            SkillModelPass.SUMMARIZE_COMPACT_RESULT

        else -> SkillModelPass.NONE
    }

    private fun ReviewChatTimeScope.toSkillScopeLabel(): String = when (this) {
        ReviewChatTimeScope.AllTime -> "全部历史"
        is ReviewChatTimeScope.Day -> date.format(reviewChatDateFormatter)
        is ReviewChatTimeScope.Month -> "${month.monthValue}月"
        is ReviewChatTimeScope.Range -> label
    }

    private fun renderJsonObject(
        fields: Map<String, Any?>,
    ): String = fields.entries.joinToString(
        prefix = "{",
        postfix = "}",
    ) { (key, value) ->
        "${renderJsonString(key)}:${renderJsonValue(value)}"
    }

    private fun renderJsonArray(
        values: List<*>,
    ): String = values.joinToString(
        prefix = "[",
        postfix = "]",
    ) { value -> renderJsonValue(value) }

    private fun renderJsonValue(
        value: Any?,
    ): String = when (value) {
        null -> "null"
        is String -> renderJsonString(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        is List<*> -> renderJsonArray(value)
        is Map<*, *> -> renderJsonObject(
            value.entries.associate { (key, nestedValue) ->
                key.toString() to nestedValue
            },
        )

        else -> renderJsonString(value.toString())
    }

    private fun renderJsonString(
        value: String,
    ): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

internal data class ReviewChatHistorySkillRuntimeResult(
    val skillResult: ReviewChatSkillResult,
    val rawNoteDetails: List<ReviewChatRawNoteDetail> = emptyList(),
    val skillWebView: ReviewChatSkillWebView? = null,
)

private fun SkillWebViewSpec.toReviewChatSkillWebView(): ReviewChatSkillWebView =
    ReviewChatSkillWebView(
        url = url,
        iframe = iframe,
        aspectRatio = aspectRatio,
    )

private data class ReviewChatRuntimeRecord(
    val id: Long,
    val dateLabel: String,
    val title: String,
    val summary: String,
    val content: String?,
) {
    fun toRawNoteDetail(): ReviewChatRawNoteDetail? {
        val fullContent = content?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return ReviewChatRawNoteDetail(
            noteId = id,
            title = title,
            dateLabel = dateLabel,
            fullContent = fullContent,
        )
    }
}

private fun Map<String, SkillJsonValue>.stringValue(key: String): String? =
    (this[key] as? SkillJsonValue.JsonString)?.value?.takeIf { it.isNotBlank() }

private fun Map<String, SkillJsonValue>.booleanValue(key: String): Boolean? =
    (this[key] as? SkillJsonValue.JsonBoolean)?.value

private fun Map<String, SkillJsonValue>.numberValue(key: String): Double? =
    (this[key] as? SkillJsonValue.JsonNumber)?.value

private fun Map<String, SkillJsonValue>.objectValue(key: String): Map<String, SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonObject)?.values ?: emptyMap()

private fun SkillJsonValue.objectValues(): Map<String, SkillJsonValue> =
    (this as? SkillJsonValue.JsonObject)?.values ?: emptyMap()

private fun Map<String, SkillJsonValue>.arrayValue(key: String): List<SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonArray)?.items ?: emptyList()
