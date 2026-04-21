package com.mindflow.app.data.reviewchat

enum class ReviewChatIntent {
    SYNTHESIZE,
    DISCUSS,
    RECALL,
}

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

data class ReviewChatContextPacket(
    val intent: ReviewChatIntent,
    val question: String,
    val sessionSummary: String,
    val conversationSnippets: List<String>,
    val historyAnchorSnippets: List<String>,
    val memoryDigestSnippets: List<String>,
    val memoryThreadSnippets: List<String>,
    val knowledgeBaseSnippets: List<String>,
    val wikiSnippets: List<String>,
    val rawNoteSnippets: List<String>,
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
