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
    CLOUD,
    ON_DEVICE,
}

data class ReviewChatMessage(
    val role: ReviewChatMessageRole,
    val content: String,
    val provider: ReviewChatProvider? = null,
    val createdAt: Long,
)

data class ReviewChatTurnRequest(
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
)

data class ReviewChatContextPacket(
    val intent: ReviewChatIntent,
    val question: String,
    val sessionSummary: String,
    val rawNoteSnippets: List<String>,
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
