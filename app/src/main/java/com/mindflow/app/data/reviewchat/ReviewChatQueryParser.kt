package com.mindflow.app.data.reviewchat

internal object ReviewChatQueryParser {
    fun parse(
        question: String,
        modelPlan: ReviewChatModelQueryPlan? = null,
    ): ReviewChatParsedQuery {
        val requestedDate = requestedDateForReviewChat(question)
        val requestedMonth = requestedMonthForReviewChat(question)
        val requestedRange = requestedDateRangeForReviewChat(question)
        val fallbackWantsFullRecord = listOf("完整", "全文", "原文", "全部内容").any(question::contains)
        val fallbackWantsTimelineAnchor = listOf("第一条", "第一次", "最早", "最初", "一开始", "什么时候开始").any(question::contains)
        val fallbackWantsCount = reviewChatCountHints.any(question::contains)
        val fallbackWantsLinks = wantsReviewChatLinks(question)
        val fallbackWantsExamples = wantsReviewChatListExamples(question)
        val fallbackWantsCategories = wantsReviewChatCategories(question)
        val fallbackIsExternalQuestion = reviewChatExternalHints.any(question::contains)
        val fallbackMode = when {
            fallbackIsExternalQuestion -> ReviewChatQuestionMode.EXTERNAL
            fallbackWantsFullRecord -> ReviewChatQuestionMode.FULL_RECORD
            fallbackWantsTimelineAnchor -> ReviewChatQuestionMode.TIMELINE_ANCHOR
            fallbackWantsCount -> ReviewChatQuestionMode.COLLECTION_OVERVIEW
            requestedDate != null || requestedMonth != null || requestedRange != null || fallbackWantsCategories ||
                listOf("哪几条", "有哪些记录", "我只看", "都记了什么", "写了什么").any(question::contains) ->
                ReviewChatQuestionMode.RECORD_LOOKUP
            else -> ReviewChatQuestionMode.ANALYSIS
        }
        val plannedOperation = modelPlan?.operation
        val mode = plannedOperation?.toQuestionMode() ?: fallbackMode
        val operation = plannedOperation ?: when (mode) {
            ReviewChatQuestionMode.EXTERNAL -> ReviewChatQueryOperation.EXTERNAL
            ReviewChatQuestionMode.COLLECTION_OVERVIEW -> ReviewChatQueryOperation.COUNT
            ReviewChatQuestionMode.RECORD_LOOKUP -> ReviewChatQueryOperation.LIST
            ReviewChatQuestionMode.FULL_RECORD -> ReviewChatQueryOperation.FULL_TEXT
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> ReviewChatQueryOperation.TIMELINE
            ReviewChatQuestionMode.ANALYSIS -> ReviewChatQueryOperation.ANALYZE
        }
        val wantsFullRecord = modelPlan?.operation == ReviewChatQueryOperation.FULL_TEXT || fallbackWantsFullRecord
        val wantsTimelineAnchor = modelPlan?.operation == ReviewChatQueryOperation.TIMELINE || fallbackWantsTimelineAnchor
        val wantsCount = modelPlan?.operation == ReviewChatQueryOperation.COUNT || fallbackWantsCount
        val wantsLinks = modelPlan?.wantsLinks ?: fallbackWantsLinks
        val wantsExamples = modelPlan?.wantsExamples ?: fallbackWantsExamples
        val wantsCategories = modelPlan?.wantsCategories ?: fallbackWantsCategories
        val isExternalQuestion = modelPlan?.operation == ReviewChatQueryOperation.EXTERNAL || fallbackIsExternalQuestion
        val intent = questionModeIntent(question = question, mode = mode)

        val timeScope = when {
            requestedDate != null -> ReviewChatTimeScope.Day(requestedDate)
            requestedMonth != null -> ReviewChatTimeScope.Month(requestedMonth)
            requestedRange != null -> requestedRange
            else -> ReviewChatTimeScope.AllTime
        }

        return ReviewChatParsedQuery(
            question = question,
            mode = mode,
            operation = operation,
            intent = intent,
            timeScope = timeScope,
            keywords = extractReviewChatKeywords(question),
            entityTerms = modelPlan?.entityTerms ?: extractReviewChatEntityTerms(question),
            wantsTimelineAnchor = wantsTimelineAnchor,
            wantsCount = wantsCount,
            wantsFullRecord = wantsFullRecord,
            wantsLinks = wantsLinks,
            wantsExamples = wantsExamples,
            wantsCategories = wantsCategories,
            isExternalQuestion = isExternalQuestion,
        )
    }

    private fun questionModeIntent(
        question: String,
        mode: ReviewChatQuestionMode,
    ): ReviewChatIntent = when (mode) {
        ReviewChatQuestionMode.ANALYSIS -> classifyReviewChatIntent(question)
        ReviewChatQuestionMode.EXTERNAL,
        ReviewChatQuestionMode.COLLECTION_OVERVIEW,
        ReviewChatQuestionMode.RECORD_LOOKUP,
        ReviewChatQuestionMode.FULL_RECORD,
        ReviewChatQuestionMode.TIMELINE_ANCHOR,
            -> ReviewChatIntent.RECALL
    }

    private fun ReviewChatQueryOperation.toQuestionMode(): ReviewChatQuestionMode = when (this) {
        ReviewChatQueryOperation.EXTERNAL -> ReviewChatQuestionMode.EXTERNAL
        ReviewChatQueryOperation.COUNT -> ReviewChatQuestionMode.COLLECTION_OVERVIEW
        ReviewChatQueryOperation.LIST -> ReviewChatQuestionMode.RECORD_LOOKUP
        ReviewChatQueryOperation.FULL_TEXT -> ReviewChatQuestionMode.FULL_RECORD
        ReviewChatQueryOperation.TIMELINE -> ReviewChatQuestionMode.TIMELINE_ANCHOR
        ReviewChatQueryOperation.ANALYZE -> ReviewChatQuestionMode.ANALYSIS
    }
}
