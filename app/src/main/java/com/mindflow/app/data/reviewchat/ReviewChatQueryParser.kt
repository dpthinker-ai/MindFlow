package com.mindflow.app.data.reviewchat

internal object ReviewChatQueryParser {
    fun parse(question: String): ReviewChatParsedQuery {
        val requestedDate = requestedDateForReviewChat(question)
        val requestedMonth = requestedMonthForReviewChat(question)
        val wantsFullRecord = listOf("完整", "全文", "原文", "全部内容").any(question::contains)
        val wantsTimelineAnchor = listOf("第一条", "第一次", "最早", "最初", "一开始", "什么时候开始").any(question::contains)
        val wantsCount = reviewChatCountHints.any(question::contains)
        val wantsLinks = wantsReviewChatLinks(question)
        val wantsExamples = wantsReviewChatListExamples(question)
        val isExternalQuestion = reviewChatExternalHints.any(question::contains)
        val intent = classifyReviewChatIntent(question)
        val mode = when {
            isExternalQuestion -> ReviewChatQuestionMode.EXTERNAL
            wantsFullRecord -> ReviewChatQuestionMode.FULL_RECORD
            wantsTimelineAnchor -> ReviewChatQuestionMode.TIMELINE_ANCHOR
            wantsCount -> ReviewChatQuestionMode.COLLECTION_OVERVIEW
            requestedDate != null || requestedMonth != null ||
                listOf("哪几条", "有哪些记录", "我只看", "都记了什么", "写了什么").any(question::contains) ->
                ReviewChatQuestionMode.RECORD_LOOKUP
            else -> ReviewChatQuestionMode.ANALYSIS
        }

        val operation = when (mode) {
            ReviewChatQuestionMode.EXTERNAL -> ReviewChatQueryOperation.EXTERNAL
            ReviewChatQuestionMode.COLLECTION_OVERVIEW -> ReviewChatQueryOperation.COUNT
            ReviewChatQuestionMode.RECORD_LOOKUP -> ReviewChatQueryOperation.LIST
            ReviewChatQuestionMode.FULL_RECORD -> ReviewChatQueryOperation.FULL_TEXT
            ReviewChatQuestionMode.TIMELINE_ANCHOR -> ReviewChatQueryOperation.TIMELINE
            ReviewChatQuestionMode.ANALYSIS -> ReviewChatQueryOperation.ANALYZE
        }

        val timeScope = when {
            requestedDate != null -> ReviewChatTimeScope.Day(requestedDate)
            requestedMonth != null -> ReviewChatTimeScope.Month(requestedMonth)
            else -> ReviewChatTimeScope.AllTime
        }

        return ReviewChatParsedQuery(
            question = question,
            mode = mode,
            operation = operation,
            intent = intent,
            timeScope = timeScope,
            keywords = extractReviewChatKeywords(question),
            entityTerms = extractReviewChatEntityTerms(question),
            wantsTimelineAnchor = wantsTimelineAnchor,
            wantsCount = wantsCount,
            wantsFullRecord = wantsFullRecord,
            wantsLinks = wantsLinks,
            wantsExamples = wantsExamples,
            isExternalQuestion = isExternalQuestion,
        )
    }
}
