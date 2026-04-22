package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.local.entity.NoteEntity

internal data class ReviewChatCorpusContext(
    val query: ReviewChatParsedQuery,
    val selection: ReviewChatCorpusSelection,
    val collectionOverview: ReviewChatCollectionOverview?,
    val historyAnchors: List<ReviewChatTimelineAnchor>,
    val rawNoteEvidence: List<ReviewChatEvidenceItem>,
    val rawNoteDetails: List<ReviewChatRawNoteDetail>,
    val referencedNotes: List<ReviewChatReferencedNote>,
    val querySummarySnippets: List<String>,
    val deterministicAnswerSnippets: List<String>,
    val categoryDigestSnippets: List<String>,
)

internal object ReviewChatCorpusQueryEngine {
    fun build(
        query: ReviewChatParsedQuery,
        notes: List<NoteEntity>,
    ): ReviewChatCorpusContext {
        val selection = buildReviewChatCorpusSelection(
            question = query.question,
            mode = query.mode,
            notes = notes,
            entityTerms = query.entityTerms,
        )
        val collectionOverview = if (!query.isExternalQuestion) {
            buildCollectionOverview(
                question = query.question,
                notes = selection.queryNotes,
            )
        } else {
            null
        }
        val historyAnchors = buildHistoryAnchors(
            question = query.question,
            notes = selection.queryNotes,
        )
        val rawNoteEvidence = buildRawNoteEvidence(
            mode = query.mode,
            intent = query.intent,
            question = query.question,
            notes = selection.queryNotes,
        )
        val rawNoteDetails = buildDirectRawNoteDetails(
            question = query.question,
            mode = query.mode,
            notes = selection.queryNotes,
        )
        val referencedNotes = buildReferencedNotes(
            question = query.question,
            intent = query.intent,
            notes = selection.queryNotes,
            directRawNoteDetails = rawNoteDetails,
        )
        return ReviewChatCorpusContext(
            query = query,
            selection = selection,
            collectionOverview = collectionOverview,
            historyAnchors = historyAnchors,
            rawNoteEvidence = rawNoteEvidence,
            rawNoteDetails = rawNoteDetails,
            referencedNotes = referencedNotes,
            querySummarySnippets = buildQuerySummarySnippets(
                query = query,
                selection = selection,
                overview = collectionOverview,
            ),
            deterministicAnswerSnippets = buildDeterministicAnswerSnippets(
                query = query,
                selection = selection,
                overview = collectionOverview,
                historyAnchors = historyAnchors,
                rawNoteDetails = rawNoteDetails,
            ),
            categoryDigestSnippets = buildCategoryDigestSnippets(
                query = query,
                selection = selection,
            ),
        )
    }

    private fun buildQuerySummarySnippets(
        query: ReviewChatParsedQuery,
        selection: ReviewChatCorpusSelection,
        overview: ReviewChatCollectionOverview?,
    ): List<String> {
        if (query.isExternalQuestion) return emptyList()

        val scopeLabel = when (val scope = query.timeScope) {
            ReviewChatTimeScope.AllTime -> "全部历史"
            is ReviewChatTimeScope.Day -> scope.date.format(reviewChatDateFormatter)
            is ReviewChatTimeScope.Month -> "${scope.month.monthValue}月"
            is ReviewChatTimeScope.Range -> scope.label
        }
        val operationLabel = when (query.operation) {
            ReviewChatQueryOperation.EXTERNAL -> "外部问题"
            ReviewChatQueryOperation.COUNT -> "统计"
            ReviewChatQueryOperation.LIST -> "列记录"
            ReviewChatQueryOperation.FULL_TEXT -> "完整内容"
            ReviewChatQueryOperation.TIMELINE -> "时间锚点"
            ReviewChatQueryOperation.ANALYZE -> "分析"
        }
        val hitCount = overview?.totalCount ?: selection.queryNotes.size

        return buildList {
            add("操作｜$operationLabel")
            add("范围｜$scopeLabel")
            if (query.entityTerms.isNotEmpty()) {
                add("主题｜${query.entityTerms.joinToString("、")}")
            }
            if (query.wantsCategories) {
                add("任务｜归纳命中记录的主要类别，不要把时间范围或统计口径当成类别")
            }
            add("命中｜共 $hitCount 条记录")
            if (overview?.earliestDateLabel != null && overview.latestDateLabel != null && hitCount > 0) {
                add("时间范围｜最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
            }
        }
    }

    private fun buildDeterministicAnswerSnippets(
        query: ReviewChatParsedQuery,
        selection: ReviewChatCorpusSelection,
        overview: ReviewChatCollectionOverview?,
        historyAnchors: List<ReviewChatTimelineAnchor>,
        rawNoteDetails: List<ReviewChatRawNoteDetail>,
    ): List<String> {
        if (query.isExternalQuestion) return emptyList()

        val scopeLabel = when (val scope = query.timeScope) {
            ReviewChatTimeScope.AllTime -> "全部历史"
            is ReviewChatTimeScope.Day -> scope.date.format(reviewChatDateFormatter)
            is ReviewChatTimeScope.Month -> "${scope.month.monthValue}月"
            is ReviewChatTimeScope.Range -> scope.label
        }
        val subjectLabel = query.entityTerms.takeIf { it.isNotEmpty() }?.joinToString("、")

        return buildList {
            when (query.operation) {
                ReviewChatQueryOperation.COUNT -> {
                    val totalCount = overview?.totalCount ?: selection.queryNotes.size
                    add(
                        buildString {
                            append("直接答案｜")
                            if (subjectLabel != null) {
                                append("关于")
                                append(subjectLabel)
                                append("的记录")
                            } else {
                                append(scopeLabel)
                                append("的记录")
                            }
                            append("共 ")
                            append(totalCount)
                            append(" 条")
                        }
                    )
                    if (overview?.earliestDateLabel != null && overview.latestDateLabel != null && totalCount > 0) {
                        add("精确时间范围｜最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
                    }
                }

                ReviewChatQueryOperation.TIMELINE -> {
                    val earliest = historyAnchors.firstOrNull { it.label == "最早记录" } ?: historyAnchors.firstOrNull()
                    earliest?.let { anchor ->
                        add(
                            buildString {
                                append("直接答案｜")
                                if (subjectLabel != null) {
                                    append(subjectLabel)
                                    append("的最早记录在 ")
                                } else {
                                    append("最早记录在 ")
                                }
                                append(anchor.item.dateLabel)
                                append("，标题《")
                                append(anchor.item.title)
                                append("》")
                            }
                        )
                    }
                }

                ReviewChatQueryOperation.LIST -> {
                    if (hasExplicitScopedLookup(query)) {
                        add("直接答案｜$scopeLabel 共 ${selection.queryNotes.size} 条记录")
                    }
                    if (query.wantsCategories) {
                        add("分类范围｜当前分类必须覆盖 ${selection.queryNotes.size} 条命中记录")
                    }
                }

                ReviewChatQueryOperation.FULL_TEXT -> {
                    if (rawNoteDetails.isNotEmpty()) {
                        add("直接答案｜命中 ${rawNoteDetails.size} 条完整记录")
                    }
                }

                ReviewChatQueryOperation.EXTERNAL,
                ReviewChatQueryOperation.ANALYZE -> Unit
            }
        }
    }

    private fun hasExplicitScopedLookup(query: ReviewChatParsedQuery): Boolean =
        query.timeScope != ReviewChatTimeScope.AllTime ||
            query.entityTerms.isNotEmpty()

    private fun buildCategoryDigestSnippets(
        query: ReviewChatParsedQuery,
        selection: ReviewChatCorpusSelection,
    ): List<String> {
        if (!query.wantsCategories || selection.queryNotes.isEmpty()) return emptyList()
        val sorted = selection.queryNotes.sortedBy(NoteEntity::createdAt)
        return sorted.chunked(10).mapIndexed { index, chunk ->
            val startDate = chunk.first().createdLocalDate().format(reviewChatDateFormatter)
            val endDate = chunk.last().createdLocalDate().format(reviewChatDateFormatter)
            val titles = chunk.joinToString("、") { note ->
                note.topic.ifBlank { "未命名记录" }.take(16)
            }
            buildString {
                append("批次")
                append(index + 1)
                append("｜")
                append(startDate)
                if (endDate != startDate) {
                    append("~")
                    append(endDate)
                }
                append("｜")
                append(titles)
            }
        }
    }
}
