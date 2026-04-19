package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem

private val reviewChatStopWords = setOf(
    "把", "最近", "一下", "一下子", "什么", "怎么", "为什么", "哪些", "哪里", "之前",
    "关于", "这个", "那个", "最近两周", "最近一周", "我们", "你们", "我的", "你的", "时候",
)

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
    val keywords = extractReviewChatKeywords(question)
    val knowledgeBaseSnippets = buildKnowledgeBaseSnippets(
        intent = intent,
        weeklyReview = weeklyReview,
        maintenanceSnapshot = maintenanceSnapshot,
    )
    val wikiSnippets = buildWikiSnippets(
        intent = intent,
        question = question,
        keywords = keywords,
        wikiSnapshot = wikiSnapshot,
    )
    val rawSnippets = buildRawNoteSnippets(
        intent = intent,
        question = question,
        keywords = keywords,
        notes = notes,
    )
    val structuredSnippets = knowledgeBaseSnippets + wikiSnippets
    return ReviewChatContextPacket(
        intent = intent,
        question = question,
        sessionSummary = sessionSummary,
        knowledgeBaseSnippets = knowledgeBaseSnippets,
        wikiSnippets = wikiSnippets,
        rawNoteSnippets = rawSnippets,
        structuredSnippets = structuredSnippets,
    )
}

private fun extractReviewChatKeywords(question: String): List<String> =
    Regex("[\\p{IsHan}A-Za-z0-9]{2,}")
        .findAll(question)
        .map { it.value.trim() }
        .filter { token -> token.isNotBlank() && token !in reviewChatStopWords }
        .toList()

private fun buildKnowledgeBaseSnippets(
    intent: ReviewChatIntent,
    weeklyReview: WeeklyReviewState,
    maintenanceSnapshot: LocalKnowledgeMaintenanceSnapshot,
): List<String> {
    val ordered = buildList {
        maintenanceSnapshot.currentJudgement.line.takeIf { it.isNotBlank() }?.let { add("当前判断｜$it") }
        maintenanceSnapshot.recentAbsorption.line.takeIf { it.isNotBlank() }?.let { add("最近吸收｜$it") }
        maintenanceSnapshot.newConnection.line.takeIf { it.isNotBlank() }?.let { add("新连接｜$it") }
        maintenanceSnapshot.openQuestion.line.takeIf { it.isNotBlank() }?.let { add("待厘清问题｜$it") }
        weeklyReview.lines.forEach { add("周回看｜$it") }
    }
    val limit = when (intent) {
        ReviewChatIntent.SYNTHESIZE -> 5
        ReviewChatIntent.DISCUSS -> 4
        ReviewChatIntent.RECALL -> 3
    }
    return ordered.take(limit)
}

private fun buildWikiSnippets(
    intent: ReviewChatIntent,
    question: String,
    keywords: List<String>,
    wikiSnapshot: DirectionWikiSnapshot,
): List<String> {
    val directions = wikiSnapshot.directions.values
        .sortedWith(
            compareByDescending<DirectionWikiDirectionSummary> { direction ->
                scoreQuestionMatch(
                    question = question,
                    keywords = keywords,
                    haystack = listOf(
                        direction.title,
                        direction.conclusionLine,
                        direction.assetSummary,
                        direction.threadKey,
                    ),
                )
            }.thenByDescending { it.updatedAt },
        )
        .take(
            when (intent) {
                ReviewChatIntent.SYNTHESIZE -> 3
                ReviewChatIntent.DISCUSS -> 2
                ReviewChatIntent.RECALL -> 3
            },
        )
        .map { direction ->
            "方向｜${direction.title}｜${direction.conclusionLine.ifBlank { direction.assetSummary }}"
        }

    val knowledgeItems = wikiSnapshot.knowledgeItems
        .sortedWith(
            compareByDescending<KnowledgeLayerSearchItem> { item ->
                scoreQuestionMatch(
                    question = question,
                    keywords = keywords,
                    haystack = listOf(
                        item.title,
                        item.summary,
                        item.supportLine,
                        item.type.label,
                    ),
                )
            }.thenByDescending { it.updatedAt },
        )
        .take(
            when (intent) {
                ReviewChatIntent.SYNTHESIZE -> 4
                ReviewChatIntent.DISCUSS -> 3
                ReviewChatIntent.RECALL -> 4
            },
        )
        .map { item ->
            "${item.type.label}｜${item.title}｜${item.summary.ifBlank { item.supportLine }}"
        }

    return directions + knowledgeItems
}

private fun buildRawNoteSnippets(
    intent: ReviewChatIntent,
    question: String,
    keywords: List<String>,
    notes: List<NoteEntity>,
): List<String> {
    val limit = when (intent) {
        ReviewChatIntent.SYNTHESIZE -> 4
        ReviewChatIntent.DISCUSS -> 5
        ReviewChatIntent.RECALL -> 6
    }
    return notes
        .sortedWith(
            compareByDescending<NoteEntity> { note ->
                scoreQuestionMatch(
                    question = question,
                    keywords = keywords,
                    haystack = listOf(
                        note.topic,
                        note.content,
                        note.folderKey.orEmpty(),
                    ) + note.tags,
                )
            }.thenByDescending { it.updatedAt },
        )
        .take(limit)
        .map { note ->
            "记录｜${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}"
        }
}

private fun scoreQuestionMatch(
    question: String,
    keywords: List<String>,
    haystack: List<String>,
): Int {
    val text = haystack.filter { it.isNotBlank() }.joinToString(" ").lowercase()
    if (text.isBlank()) return 0
    var score = 0
    keywords.forEach { keyword ->
        val normalized = keyword.lowercase()
        if (normalized in text) score += 3
        if (text in normalized && text.length > 3) score += 1
    }
    if (question.lowercase() in text && question.length >= 4) {
        score += 2
    }
    return score
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
