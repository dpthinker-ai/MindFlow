package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiSnapshot

internal fun classifyReviewChatIntent(question: String): ReviewChatIntent {
    val normalized = question.lowercase()
    return when {
        listOf("串", "归纳", "共同", "主线", "矛盾").any(normalized::contains) -> ReviewChatIntent.SYNTHESIZE
        listOf("为什么", "值不值得", "该不该", "怎么推进", "分歧").any(normalized::contains) -> ReviewChatIntent.DISCUSS
        else -> ReviewChatIntent.RECALL
    }
}

internal fun buildReviewChatContextPacket(
    question: String,
    intent: ReviewChatIntent,
    notes: List<NoteEntity>,
    weeklyReview: WeeklyReviewState,
    maintenanceSnapshot: LocalKnowledgeMaintenanceSnapshot,
    wikiSnapshot: DirectionWikiSnapshot,
    sessionSummary: String,
): ReviewChatContextPacket {
    val rawSnippets = notes
        .sortedByDescending { it.updatedAt }
        .take(6)
        .map { note ->
            "记录｜${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}"
        }
    val structuredSnippets = buildList {
        weeklyReview.lines.forEach { add("周回看｜$it") }
        maintenanceSnapshot.currentJudgement.line.takeIf { it.isNotBlank() }?.let { add("当前判断｜$it") }
        maintenanceSnapshot.recentAbsorption.line.takeIf { it.isNotBlank() }?.let { add("最近吸收｜$it") }
        wikiSnapshot.directions.values
            .sortedByDescending { it.updatedAt }
            .take(3)
            .forEach { add("方向｜${it.title}｜${it.conclusionLine.ifBlank { it.assetSummary }}") }
        wikiSnapshot.knowledgeItems
            .sortedByDescending { it.updatedAt }
            .take(4)
            .forEach { add("${it.type.label}｜${it.title}｜${it.summary.ifBlank { it.supportLine }}") }
    }
    return ReviewChatContextPacket(
        intent = intent,
        question = question,
        sessionSummary = sessionSummary,
        rawNoteSnippets = rawSnippets,
        structuredSnippets = structuredSnippets,
    )
}

class ReviewChatPlanner(
    private val loadNotes: suspend () -> List<NoteEntity>,
    private val loadWeeklyReview: suspend () -> WeeklyReviewState,
    private val loadMaintenanceSnapshot: suspend () -> LocalKnowledgeMaintenanceSnapshot,
    private val loadWikiSnapshot: suspend () -> DirectionWikiSnapshot,
    private val resolveExecutionMode: suspend () -> AiExecutionMode,
    private val isCloudConfigured: suspend () -> Boolean,
    private val isOnDeviceReady: suspend () -> Boolean,
    private val runCloud: suspend (String) -> AiChatResult,
    private val runOnDevice: suspend (String) -> AiChatResult,
) {
    suspend fun answer(request: ReviewChatTurnRequest): ReviewChatTurnResult {
        val intent = classifyReviewChatIntent(request.question)
        val packet = buildReviewChatContextPacket(
            question = request.question,
            intent = intent,
            notes = loadNotes(),
            weeklyReview = loadWeeklyReview(),
            maintenanceSnapshot = loadMaintenanceSnapshot(),
            wikiSnapshot = loadWikiSnapshot(),
            sessionSummary = request.priorMessages
                .takeLast(2)
                .joinToString("\n") { it.content.take(120) },
        )
        val cloudPrompt = ReviewChatPromptFactory.cloud(packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(packet)
        val attempts = when (resolveExecutionMode()) {
            AiExecutionMode.AUTOMATIC -> listOf(ReviewChatProvider.CLOUD, ReviewChatProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(ReviewChatProvider.CLOUD)
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(ReviewChatProvider.ON_DEVICE)
        }

        attempts.forEachIndexed { index, provider ->
            val result = when (provider) {
                ReviewChatProvider.CLOUD -> {
                    if (isCloudConfigured()) runCloud(cloudPrompt) else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "云侧未配置")
                    }
                }
                ReviewChatProvider.ON_DEVICE -> {
                    if (isOnDeviceReady()) runOnDevice(onDevicePrompt) else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "端侧未就绪")
                    }
                }
            }
            if (result is AiChatResult.Success) {
                val fallbackOccurred = index > 0
                return ReviewChatTurnResult(
                    answer = result.content.trim(),
                    provider = provider,
                    fallbackOccurred = fallbackOccurred,
                    providerLine = buildReviewChatProviderLine(provider, fallbackOccurred),
                    sessionSummary = "${request.question.take(40)}｜${result.content.take(80)}",
                    titleSuggestion = request.question.take(18),
                )
            }
        }
        error("No provider returned a usable review chat answer")
    }
}
