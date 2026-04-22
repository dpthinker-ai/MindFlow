package com.mindflow.app.data.reviewchat

import java.time.LocalDate
import java.time.YearMonth

enum class ReviewChatIntent {
    SYNTHESIZE,
    DISCUSS,
    RECALL,
}

enum class ReviewChatQuestionMode {
    EXTERNAL,
    COLLECTION_OVERVIEW,
    RECORD_LOOKUP,
    FULL_RECORD,
    TIMELINE_ANCHOR,
    ANALYSIS,
}

enum class ReviewChatQueryOperation {
    EXTERNAL,
    COUNT,
    LIST,
    FULL_TEXT,
    TIMELINE,
    ANALYZE,
}

sealed interface ReviewChatTimeScope {
    data object AllTime : ReviewChatTimeScope

    data class Day(val date: LocalDate) : ReviewChatTimeScope

    data class Month(val month: YearMonth) : ReviewChatTimeScope

    data class Range(
        val start: LocalDate,
        val endInclusive: LocalDate,
        val label: String,
    ) : ReviewChatTimeScope
}

data class ReviewChatParsedQuery(
    val question: String,
    val mode: ReviewChatQuestionMode,
    val operation: ReviewChatQueryOperation,
    val intent: ReviewChatIntent,
    val timeScope: ReviewChatTimeScope,
    val keywords: List<String>,
    val entityTerms: List<String>,
    val wantsTimelineAnchor: Boolean,
    val wantsCount: Boolean,
    val wantsFullRecord: Boolean,
    val wantsLinks: Boolean,
    val wantsExamples: Boolean,
    val wantsCategories: Boolean,
    val isExternalQuestion: Boolean,
)

enum class ReviewChatMessageRole {
    USER,
    ASSISTANT,
}

enum class ReviewChatProvider {
    LOCAL_MEMORY,
    CLOUD,
    ON_DEVICE,
}

data class ReviewChatMessage(
    val role: ReviewChatMessageRole,
    val content: String,
    val provider: ReviewChatProvider? = null,
    val createdAt: Long,
    val referencedNoteId: Long? = null,
    val referencedNotes: List<ReviewChatReferencedNote> = emptyList(),
)

data class ReviewChatTurnRequest(
    val sessionId: String = "review-chat",
    val question: String,
    val priorMessages: List<ReviewChatMessage>,
)

data class ReviewChatTurnResult(
    val answer: String,
    val provider: ReviewChatProvider,
    val fallbackOccurred: Boolean,
    val providerLine: String,
    val sessionSummary: String,
    val titleSuggestion: String,
    val referencedNoteId: Long? = null,
    val referencedNotes: List<ReviewChatReferencedNote> = emptyList(),
)

sealed interface ReviewChatTurnEvent {
    data class Partial(
        val content: String,
        val provider: ReviewChatProvider,
        val providerLine: String,
    ) : ReviewChatTurnEvent

    data class Complete(
        val result: ReviewChatTurnResult,
    ) : ReviewChatTurnEvent
}

data class ReviewChatOnDeviceRequest(
    val sessionId: String,
    val prompt: String,
    val systemInstruction: String = "",
    val extraContext: Map<String, String> = emptyMap(),
    val resetConversation: Boolean,
)

data class ReviewChatOnDevicePrompt(
    val systemInstruction: String,
    val userMessage: String,
    val extraContext: Map<String, String> = emptyMap(),
)

data class ReviewChatRawNoteDetail(
    val noteId: Long,
    val title: String,
    val dateLabel: String,
    val fullContent: String,
)

data class ReviewChatCollectionOverview(
    val scopeLabel: String,
    val totalCount: Int,
    val earliestDateLabel: String? = null,
    val latestDateLabel: String? = null,
    val last7DaysCount: Int? = null,
    val last30DaysCount: Int? = null,
)

data class ReviewChatEvidenceItem(
    val noteId: Long,
    val dateLabel: String,
    val title: String,
    val summary: String,
)

data class ReviewChatTimelineAnchor(
    val label: String,
    val item: ReviewChatEvidenceItem,
)

data class ReviewChatReferencedNote(
    val noteId: Long,
    val title: String,
    val dateLabel: String,
)

data class ReviewChatContextPacket(
    val questionMode: ReviewChatQuestionMode,
    val intent: ReviewChatIntent,
    val question: String,
    val isExternalQuestion: Boolean,
    val wantsCount: Boolean,
    val wantsCategories: Boolean,
    val querySummarySnippets: List<String>,
    val sessionSummary: String,
    val collectionOverview: ReviewChatCollectionOverview?,
    val conversationSnippets: List<String>,
    val historyAnchors: List<ReviewChatTimelineAnchor>,
    val memoryDigestSnippets: List<String>,
    val memoryThreadSnippets: List<String>,
    val knowledgeBaseSnippets: List<String>,
    val wikiSnippets: List<String>,
    val rawNoteEvidence: List<ReviewChatEvidenceItem>,
    val rawNoteDetails: List<ReviewChatRawNoteDetail>,
    val structuredSnippets: List<String>,
)

fun buildReviewChatProviderLine(
    provider: ReviewChatProvider,
    fallbackOccurred: Boolean,
): String = when {
    provider == ReviewChatProvider.CLOUD -> "本次由云侧完成"
    fallbackOccurred -> "云侧不可用，已回退端侧"
    else -> "本次由端侧完成"
}
