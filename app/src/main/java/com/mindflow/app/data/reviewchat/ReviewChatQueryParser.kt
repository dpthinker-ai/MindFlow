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
        val fallbackWantsBriefAnswer = wantsReviewChatBriefAnswer(question)
        val fallbackIsExternalQuestion = reviewChatExternalHints.any(question::contains)
        val fallbackHasStatusFilter = requestedReviewChatStatusFilter(question).isNotEmpty()
        val fallbackWantsFocusSummary = listOf("关注什么", "在关注什么", "最近在关注", "近期关注").any(question::contains)
        val fallbackIntent = classifyReviewChatIntent(question)
        val fallbackWantsAnalysis =
            fallbackIntent != ReviewChatIntent.RECALL ||
                reviewChatDeepAnalysisHints.any(question::contains) ||
                fallbackWantsFocusSummary
        val fallbackEntityTerms = modelPlan?.entityTerms ?: extractReviewChatEntityTerms(question)
        val fallbackIsTopicSummary = isReviewChatTopicSummaryQuestion(
            question = question,
            entityTerms = fallbackEntityTerms,
            wantsCount = fallbackWantsCount,
            wantsCategories = fallbackWantsCategories,
            wantsFullRecord = fallbackWantsFullRecord,
            wantsTimelineAnchor = fallbackWantsTimelineAnchor,
            isExternalQuestion = fallbackIsExternalQuestion,
        )
        val fallbackMode = when {
            fallbackIsExternalQuestion -> ReviewChatQuestionMode.EXTERNAL
            fallbackWantsFullRecord -> ReviewChatQuestionMode.FULL_RECORD
            fallbackWantsTimelineAnchor -> ReviewChatQuestionMode.TIMELINE_ANCHOR
            fallbackWantsCount && !fallbackWantsCategories -> ReviewChatQuestionMode.COLLECTION_OVERVIEW
            fallbackIsTopicSummary -> ReviewChatQuestionMode.RECORD_LOOKUP
            fallbackHasStatusFilter -> ReviewChatQuestionMode.RECORD_LOOKUP
            fallbackWantsAnalysis -> ReviewChatQuestionMode.ANALYSIS
            requestedDate != null || requestedMonth != null || requestedRange != null || fallbackWantsCategories ||
                listOf("哪几条", "有哪些记录", "我只看", "都记了什么", "写了什么").any(question::contains) ->
                ReviewChatQuestionMode.RECORD_LOOKUP
            else -> ReviewChatQuestionMode.ANALYSIS
        }
        val plannedOperation = modelPlan?.operation
        val resolvedOperation = when {
            fallbackIsExternalQuestion -> ReviewChatQueryOperation.EXTERNAL
            fallbackWantsFullRecord -> ReviewChatQueryOperation.FULL_TEXT
            fallbackWantsTimelineAnchor -> ReviewChatQueryOperation.TIMELINE
            fallbackWantsCategories -> ReviewChatQueryOperation.LIST
            fallbackWantsCount -> ReviewChatQueryOperation.COUNT
            fallbackHasStatusFilter -> ReviewChatQueryOperation.LIST
            fallbackIsTopicSummary && plannedOperation == ReviewChatQueryOperation.ANALYZE -> ReviewChatQueryOperation.LIST
            plannedOperation == ReviewChatQueryOperation.ANALYZE &&
                !fallbackWantsAnalysis &&
                (requestedDate != null || requestedMonth != null || requestedRange != null) -> ReviewChatQueryOperation.LIST
            else -> plannedOperation
        }
        val mode = resolvedOperation?.toQuestionMode() ?: fallbackMode
        val operation = resolvedOperation ?: when (mode) {
            ReviewChatQuestionMode.EXTERNAL -> ReviewChatQueryOperation.EXTERNAL
            ReviewChatQuestionMode.COLLECTION_OVERVIEW -> ReviewChatQueryOperation.COUNT
            ReviewChatQuestionMode.RECORD_LOOKUP -> ReviewChatQueryOperation.LIST
            ReviewChatQuestionMode.FULL_RECORD -> ReviewChatQueryOperation.FULL_TEXT
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> ReviewChatQueryOperation.TIMELINE
            ReviewChatQuestionMode.ANALYSIS -> ReviewChatQueryOperation.ANALYZE
        }
        val wantsFullRecord = resolvedOperation == ReviewChatQueryOperation.FULL_TEXT || fallbackWantsFullRecord
        val wantsTimelineAnchor = resolvedOperation == ReviewChatQueryOperation.TIMELINE || fallbackWantsTimelineAnchor
        val wantsCount = resolvedOperation == ReviewChatQueryOperation.COUNT || fallbackWantsCount
        val wantsLinks = fallbackWantsLinks || (modelPlan?.wantsLinks ?: false)
        val wantsExamples = fallbackWantsExamples || (modelPlan?.wantsExamples ?: false)
        val wantsCategories = fallbackWantsCategories || (modelPlan?.wantsCategories ?: false)
        val wantsBriefAnswer = fallbackWantsBriefAnswer || fallbackIsTopicSummary
        val isExternalQuestion = resolvedOperation == ReviewChatQueryOperation.EXTERNAL || fallbackIsExternalQuestion
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
            entityTerms = fallbackEntityTerms,
            wantsTimelineAnchor = wantsTimelineAnchor,
            wantsCount = wantsCount,
            wantsFullRecord = wantsFullRecord,
            wantsLinks = wantsLinks,
            wantsExamples = wantsExamples,
            wantsCategories = wantsCategories,
            wantsBriefAnswer = wantsBriefAnswer,
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
