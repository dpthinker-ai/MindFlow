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
    val wantsBriefAnswer: Boolean,
    val isExternalQuestion: Boolean,
)

data class ReviewChatModelQueryPlan(
    val operation: ReviewChatQueryOperation,
    val entityTerms: List<String> = emptyList(),
    val wantsCategories: Boolean = false,
    val wantsExamples: Boolean = false,
    val wantsLinks: Boolean = false,
)

enum class ReviewChatMessageRole {
    USER,
    ASSISTANT,
}

enum class ReviewChatProvider {
    LOCAL_MEMORY,
    SYSTEM,
    CLOUD,
    ON_DEVICE,
}

data class ReviewChatMessage(
    val role: ReviewChatMessageRole,
    val content: String,
    val structuredAnswer: ReviewChatStructuredAnswer? = null,
    val provider: ReviewChatProvider? = null,
    val createdAt: Long,
    val referencedNoteId: Long? = null,
    val referencedNotes: List<ReviewChatReferencedNote> = emptyList(),
    val skillWebView: ReviewChatSkillWebView? = null,
)

data class ReviewChatTurnRequest(
    val sessionId: String = "review-chat",
    val question: String,
    val priorMessages: List<ReviewChatMessage>,
)

data class ReviewChatTurnResult(
    val answer: String,
    val structuredAnswer: ReviewChatStructuredAnswer? = null,
    val provider: ReviewChatProvider,
    val fallbackOccurred: Boolean,
    val providerLine: String,
    val sessionSummary: String,
    val titleSuggestion: String,
    val referencedNoteId: Long? = null,
    val referencedNotes: List<ReviewChatReferencedNote> = emptyList(),
    val skillWebView: ReviewChatSkillWebView? = null,
)

sealed interface ReviewChatTurnEvent {
    data class Status(
        val message: String,
        val provider: ReviewChatProvider? = null,
        val providerLine: String = "",
    ) : ReviewChatTurnEvent

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

data class ReviewChatSkillWebView(
    val url: String,
    val iframe: Boolean = false,
    val aspectRatio: Float = 1.333f,
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
    val wantsBriefAnswer: Boolean,
    val querySummarySnippets: List<String>,
    val deterministicAnswerSnippets: List<String>,
    val categoryDigestSnippets: List<String>,
    val sessionSummary: String,
    val collectionOverview: ReviewChatCollectionOverview?,
    val conversationSnippets: List<String>,
    val historyAnchors: List<ReviewChatTimelineAnchor>,
    val memoryDigestSnippets: List<String>,
    val memoryThreadSnippets: List<String>,
    val availableSkillSnippets: List<String> = emptyList(),
    val knowledgeBaseSnippets: List<String>,
    val wikiSnippets: List<String>,
    val rawNoteEvidence: List<ReviewChatEvidenceItem>,
    val rawNoteDetails: List<ReviewChatRawNoteDetail>,
    val structuredSnippets: List<String>,
    val skillResult: ReviewChatSkillResult? = null,
)

fun buildReviewChatProviderLine(
    provider: ReviewChatProvider,
    fallbackOccurred: Boolean,
): String = when {
    provider == ReviewChatProvider.SYSTEM -> "系统提示"
    provider == ReviewChatProvider.CLOUD -> "本次由云侧完成"
    fallbackOccurred -> "云侧不可用，已回退端侧"
    else -> "本次由端侧完成"
}
