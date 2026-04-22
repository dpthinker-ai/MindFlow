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
            add("命中｜共 $hitCount 条记录")
            if (overview?.earliestDateLabel != null && overview.latestDateLabel != null && hitCount > 0) {
                add("时间范围｜最早 ${overview.earliestDateLabel}，最近 ${overview.latestDateLabel}")
            }
        }
    }
}
