package com.mindflow.app.data.reviewchat

enum class ReviewChatSkillModelPass {
    NONE,
    SUMMARIZE_COMPACT_RESULT,
    ANALYZE_RECORDS,
}

data class ReviewChatSkillInvocation(
    val skillId: String,
    val scriptName: String,
    val intent: String,
    val query: String,
    val timeScopeLabel: String,
    val needsCard: Boolean,
    val modelPass: ReviewChatSkillModelPass,
)

data class ReviewChatSkillCoverage(
    val timeScopeLabel: String,
    val scopedCount: Int,
    val matchedCount: Int,
    val processedCount: Int,
    val complete: Boolean,
    val startDateLabel: String? = null,
    val endDateLabel: String? = null,
    val nextCursor: String? = null,
)

data class ReviewChatSkillRecord(
    val id: Long,
    val dateLabel: String,
    val title: String,
    val summary: String,
)

data class ReviewChatSkillFacts(
    val coverage: ReviewChatSkillCoverage,
    val deterministicResults: List<String>,
    val categoryBatches: List<String>,
    val recordPreview: List<ReviewChatSkillRecord>,
)

data class ReviewChatSkillResult(
    val invocation: ReviewChatSkillInvocation,
    val facts: ReviewChatSkillFacts,
    val responseRules: List<String>,
    val error: String? = null,
)
