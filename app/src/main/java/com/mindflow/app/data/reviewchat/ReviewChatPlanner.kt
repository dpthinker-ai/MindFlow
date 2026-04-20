package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.knowledgebrain.MemoryLayerChatAssembler
import com.mindflow.app.data.knowledgebrain.MemoryLayerChatContext
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val reviewChatStopWords = setOf(
    "把", "最近", "一下", "一下子", "什么", "怎么", "为什么", "哪些", "哪里", "之前",
    "关于", "这个", "那个", "最近两周", "最近一周", "我们", "你们", "我的", "你的", "时候",
)

private val reviewChatHistoryHints = listOf(
    "之前", "以前", "过去", "历史", "曾经", "最早", "早些", "那会", "那时候", "去年", "上次",
)

private val reviewChatScopeHints = listOf(
    "记录", "笔记", "回看", "聊过", "提过", "写过", "那天", "那周", "某天", "完整", "全文",
    "原文", "图谱", "主题", "问题线", "方向", "知识层", "thread",
)

private val reviewChatExternalHints = listOf(
    "天气", "气温", "下雨", "台风", "新闻", "热搜", "股价", "股票", "汇率", "彩票",
    "体育比赛", "球赛", "nba", "足球", "电影票房", "明星八卦", "实时路况", "航班", "高铁",
)

private val reviewChatDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

internal fun extractReviewChatKeywords(question: String): List<String> =
    Regex("[\\p{IsHan}A-Za-z0-9]{2,}")
        .findAll(question)
        .flatMap { match ->
            tokenizeReviewChatQueryChunk(match.value).asSequence()
        }
        .filter { token -> token.isNotBlank() && token !in reviewChatStopWords }
        .distinct()
        .toList()

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
    priorMessages: List<ReviewChatMessage> = emptyList(),
    memoryContext: MemoryLayerChatContext = MemoryLayerChatContext(
        memoryDigestSnippets = emptyList(),
        memoryThreadSnippets = emptyList(),
        rawNoteSnippets = emptyList(),
        rawNoteDetails = emptyList(),
    ),
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
        conversationSnippets = buildConversationSnippets(priorMessages),
        memoryDigestSnippets = memoryContext.memoryDigestSnippets,
        memoryThreadSnippets = memoryContext.memoryThreadSnippets,
        knowledgeBaseSnippets = knowledgeBaseSnippets,
        wikiSnippets = wikiSnippets,
        rawNoteSnippets = (memoryContext.rawNoteSnippets + rawSnippets).distinct(),
        rawNoteDetails = memoryContext.rawNoteDetails,
        structuredSnippets = structuredSnippets,
    )
}

private fun tokenizeReviewChatQueryChunk(token: String): List<String> {
    val normalized = token.trim().lowercase()
    if (normalized.isBlank()) return emptyList()
    val isHan = normalized.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
    if (!isHan || normalized.length <= 4) return listOf(normalized)

    val pieces = linkedSetOf<String>()
    for (window in 2..4) {
        if (normalized.length < window) continue
        for (index in 0..normalized.length - window) {
            pieces += normalized.substring(index, index + window)
        }
    }
    pieces += normalized
    return pieces.toList()
}

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
    val ranked = notes
        .map { note ->
            RankedReviewChatNote(
                note = note,
                score = scoreQuestionMatch(
                    question = question,
                    keywords = keywords,
                    haystack = listOf(
                        note.topic,
                        note.content,
                        note.folderKey.orEmpty(),
                    ) + note.tags,
                ),
            )
        }
        .sortedWith(
            compareByDescending<RankedReviewChatNote> { it.score }
                .thenByDescending { it.note.updatedAt },
        )

    val directMatches = ranked.filter { it.score > 0 }.take(limit)
    if (directMatches.size >= limit) {
        return directMatches.map { it.note.toReviewChatSnippet() }
    }

    val supplemental = buildSupplementalRawSnippets(
        intent = intent,
        question = question,
        ranked = ranked.filterNot { candidate -> directMatches.any { it.note.id == candidate.note.id } },
        limit = limit - directMatches.size,
    )

    return (directMatches.map { it.note } + supplemental)
        .distinctBy(NoteEntity::id)
        .take(limit)
        .map(NoteEntity::toReviewChatSnippet)
}

private fun buildConversationSnippets(
    priorMessages: List<ReviewChatMessage>,
): List<String> = priorMessages
    .takeLast(4)
    .map { message ->
        val role = if (message.role == ReviewChatMessageRole.USER) "用户" else "助手"
        "$role｜${message.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(140)}"
    }

private fun buildSupplementalRawSnippets(
    intent: ReviewChatIntent,
    question: String,
    ranked: List<RankedReviewChatNote>,
    limit: Int,
): List<NoteEntity> {
    if (limit <= 0 || ranked.isEmpty()) return emptyList()

    val shouldDiversifyAcrossTimeline =
        intent == ReviewChatIntent.RECALL ||
            ranked.firstOrNull()?.score == 0 ||
            reviewChatHistoryHints.any { question.contains(it) }

    if (!shouldDiversifyAcrossTimeline) {
        return ranked.take(limit).map(RankedReviewChatNote::note)
    }

    val sortedByTime = ranked
        .sortedBy { it.note.updatedAt }
        .map(RankedReviewChatNote::note)
    if (sortedByTime.size <= limit) return sortedByTime

    return (0 until limit)
        .map { index ->
            val fraction = if (limit == 1) 0.0 else index.toDouble() / (limit - 1).toDouble()
            val targetIndex = (fraction * (sortedByTime.lastIndex)).toInt()
            sortedByTime[targetIndex]
        }
        .distinctBy(NoteEntity::id)
}

private fun NoteEntity.toReviewChatSnippet(): String {
    val date = Instant.ofEpochMilli(updatedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(reviewChatDateFormatter)
    val compactContent = content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)
    return "记录｜$date｜${topic.ifBlank { "未命名记录" }}｜$compactContent"
}

private data class RankedReviewChatNote(
    val note: NoteEntity,
    val score: Int,
)

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
    private val runOnDevice: suspend (ReviewChatOnDeviceRequest) -> AiChatResult,
    private val memoryLayerChatAssembler: MemoryLayerChatAssembler? = null,
) {
    suspend fun answer(request: ReviewChatTurnRequest): ReviewChatTurnResult {
        val intent = classifyReviewChatIntent(request.question)
        val notes = loadNotes()
        val weeklyReview = loadWeeklyReview()
        val maintenanceSnapshot = loadMaintenanceSnapshot()
        val wikiSnapshot = loadWikiSnapshot()
        val memoryContext = memoryLayerChatAssembler?.assemble(
            question = request.question,
            priorMessages = request.priorMessages,
        ) ?: MemoryLayerChatContext(
            memoryDigestSnippets = emptyList(),
            memoryThreadSnippets = emptyList(),
            rawNoteSnippets = emptyList(),
            rawNoteDetails = emptyList(),
        )

        if (shouldReturnRawRecordDirectly(request.question, memoryContext)) {
            val answer = buildFullRecordAnswer(memoryContext.rawNoteDetails)
            return ReviewChatTurnResult(
                answer = answer.trim(),
                provider = ReviewChatProvider.LOCAL_MEMORY,
                fallbackOccurred = false,
                providerLine = buildReviewChatProviderLine(ReviewChatProvider.LOCAL_MEMORY, fallbackOccurred = false),
                sessionSummary = "${request.question.take(40)}｜${memoryContext.rawNoteDetails.first().title}",
                titleSuggestion = request.question.take(18),
                referencedNoteId = memoryContext.rawNoteDetails.singleOrNull()?.noteId,
            )
        }

        if (shouldReturnScopedRecordListDirectly(request.question, intent, memoryContext)) {
            return ReviewChatTurnResult(
                answer = buildScopedRecordListAnswer(request.question, memoryContext.rawNoteDetails),
                provider = ReviewChatProvider.LOCAL_MEMORY,
                fallbackOccurred = false,
                providerLine = buildReviewChatProviderLine(ReviewChatProvider.LOCAL_MEMORY, fallbackOccurred = false),
                sessionSummary = "${request.question.take(40)}｜${memoryContext.rawNoteDetails.size} 条记录",
                titleSuggestion = request.question.take(18),
            )
        }

        if (looksClearlyOutsideHistoryScope(request.question)) {
            return ReviewChatTurnResult(
                answer = buildOutOfScopeReviewChatAnswer(),
                provider = ReviewChatProvider.LOCAL_MEMORY,
                fallbackOccurred = false,
                providerLine = buildReviewChatProviderLine(ReviewChatProvider.LOCAL_MEMORY, fallbackOccurred = false),
                sessionSummary = "范围外问题",
                titleSuggestion = request.question.take(18),
            )
        }

        val packet = buildReviewChatContextPacket(
            question = request.question,
            intent = intent,
            notes = notes,
            weeklyReview = weeklyReview,
            maintenanceSnapshot = maintenanceSnapshot,
            wikiSnapshot = wikiSnapshot,
            sessionSummary = request.priorMessages
                .takeLast(2)
                .joinToString("\n") { it.content.take(120) },
            priorMessages = request.priorMessages,
            memoryContext = memoryContext,
        )
        val cloudPrompt = ReviewChatPromptFactory.cloud(packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(packet)
        val attempts = when (resolveExecutionMode()) {
            AiExecutionMode.AUTOMATIC -> listOf(ReviewChatProvider.CLOUD, ReviewChatProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(ReviewChatProvider.CLOUD)
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(ReviewChatProvider.ON_DEVICE)
        }

        var lastFailure: AiChatResult.Failure? = null
        attempts.forEachIndexed { index, provider ->
            val result = when (provider) {
                ReviewChatProvider.CLOUD -> {
                    if (isCloudConfigured()) runCloud(cloudPrompt) else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "云侧未配置")
                    }
                }
                ReviewChatProvider.ON_DEVICE -> {
                    if (isOnDeviceReady()) runOnDevice(
                        ReviewChatOnDeviceRequest(
                            sessionId = request.sessionId,
                            prompt = onDevicePrompt,
                            resetConversation = request.priorMessages.isEmpty(),
                        )
                    ) else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "端侧未就绪")
                    }
                }
                ReviewChatProvider.LOCAL_MEMORY -> {
                    AiChatResult.Failure(AiFailureReason.CONFIG, "本地知识层仅支持直接记录展开")
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
                    referencedNoteId = memoryContext.rawNoteDetails.firstOrNull()?.noteId,
                )
            }
            if (result is AiChatResult.Failure) {
                lastFailure = result
            }
        }
        error(lastFailure?.message ?: "No provider returned a usable review chat answer")
    }
}

private fun shouldReturnRawRecordDirectly(
    question: String,
    memoryContext: MemoryLayerChatContext,
): Boolean {
    val wantsFullRecord = listOf("完整", "全文", "原文", "全部内容").any(question::contains)
    return wantsFullRecord && memoryContext.rawNoteDetails.isNotEmpty()
}

private fun shouldReturnScopedRecordListDirectly(
    question: String,
    intent: ReviewChatIntent,
    memoryContext: MemoryLayerChatContext,
): Boolean {
    return intent == ReviewChatIntent.RECALL &&
        requestedDateForReviewChat(question) != null &&
        memoryContext.rawNoteDetails.isNotEmpty() &&
        !listOf("完整", "全文", "原文", "全部内容").any(question::contains)
}

private fun looksClearlyOutsideHistoryScope(
    question: String,
): Boolean {
    if (reviewChatHistoryHints.any(question::contains) || reviewChatScopeHints.any(question::contains)) {
        return false
    }
    return reviewChatExternalHints.any(question::contains)
}

private fun buildOutOfScopeReviewChatAnswer(): String = """
这段聊天更适合基于你的历史记录、回看沉淀和图谱来聊。

你刚才这句更像泛聊天问题，我手上的本地材料里没有对应上下文，所以不应该假装回答。

可以换成这几类问法：
- 我最近关于这个主题写过什么
- 某一天/某一周我当时在想什么
- 把某条问题线从最早到现在串一下
- 直接把某条记录的完整内容发给我
""".trim()

private fun requestedDateForReviewChat(question: String): LocalDate? {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when {
        "今天" in question -> today
        "昨天" in question || "昨日" in question -> today.minusDays(1)
        "前天" in question -> today.minusDays(2)
        else -> {
            val match = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})").find(question) ?: return null
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            LocalDate.of(today.year, month, day)
        }
    }
}

private fun buildFullRecordAnswer(details: List<ReviewChatRawNoteDetail>): String = buildString {
    details.forEachIndexed { index, detail ->
        appendLine("${detail.dateLabel}｜${detail.title}")
        appendLine()
        appendLine(detail.fullContent.trim())
        if (index != details.lastIndex) {
            appendLine()
            appendLine("——")
            appendLine()
        }
    }
}.trim()

private fun buildScopedRecordListAnswer(
    question: String,
    details: List<ReviewChatRawNoteDetail>,
): String {
    val scopeLabel = when {
        "今天" in question -> "今天"
        "昨天" in question || "昨日" in question -> "昨天"
        "前天" in question -> "前天"
        else -> details.firstOrNull()?.dateLabel ?: "这一天"
    }
    return buildString {
        appendLine("${scopeLabel}你记录了 ${details.size} 条：")
        appendLine()
        details.forEach { detail ->
            val compactContent = detail.fullContent
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .let { if (it.length <= 120) it else it.take(119).trimEnd() + "…" }
            appendLine("- ${detail.dateLabel}｜${detail.title}｜$compactContent")
        }
    }.trim()
}
