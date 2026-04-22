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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

private val reviewChatStopWords = setOf(
    "把", "最近", "一下", "一下子", "什么", "怎么", "为什么", "哪些", "哪里", "之前",
    "关于", "这个", "那个", "最近两周", "最近一周", "我们", "你们", "我的", "你的", "时候",
    "分析", "总结", "归纳", "分类", "类别", "分为", "看看",
)

internal val reviewChatHistoryHints = listOf(
    "之前", "以前", "过去", "历史", "曾经", "最早", "早些", "那会", "那时候", "去年", "上次",
    "第一条", "第一次", "最初", "一开始", "什么时候开始",
)

internal val reviewChatScopeHints = listOf(
    "记录", "笔记", "回看", "聊过", "提过", "写过", "那天", "那周", "某天", "完整", "全文",
    "原文", "图谱", "主题", "问题线", "方向", "知识层", "thread", "本周末", "上周末", "周末",
)

internal val reviewChatCountHints = listOf(
    "多少条", "几条", "总共", "一共", "总数", "总量", "有多少条", "数量",
)

internal val reviewChatListHints = listOf(
    "列出", "列一下", "给我看", "有哪些", "哪几条", "命中的记录", "举例", "示例", "样例", "分别是", "哪些信息",
)

internal val reviewChatExternalHints = listOf(
    "天气", "气温", "下雨", "台风", "新闻", "热搜", "股价", "股票", "汇率", "彩票",
    "体育比赛", "球赛", "nba", "足球", "电影票房", "明星八卦", "实时路况", "航班", "高铁",
)

internal val reviewChatLinkHints = listOf(
    "链接", "原始链接", "原文链接", "打开原记录", "打开记录", "给我链接", "发我链接",
)

internal val reviewChatOperationPhrases = (
    reviewChatHistoryHints +
        reviewChatScopeHints +
        reviewChatCountHints +
        reviewChatListHints +
        reviewChatExternalHints +
        reviewChatLinkHints +
        listOf(
            "今天", "昨天", "前天", "最近", "最近两周", "最近一周",
            "完整内容", "全部内容", "完整", "全文", "原文",
            "第一条", "第一次", "最早", "最初", "一开始",
            "时间轴", "跨度", "统计", "查一下", "看一下", "帮我", "给我", "发我",
            "发给我",
            "总共有", "一共有", "多少", "几条", "全部", "所有", "只看",
            "列出", "举例", "示例", "样例", "打开", "命中", "那条", "那几条", "发给",
            "本周末", "上周末", "周末", "类别", "分类",
            "分析", "总结", "归纳", "分为",
        )
).distinct()
    .sortedByDescending(String::length)

internal val reviewChatEntityStopWords = reviewChatStopWords + setOf(
    "记录", "笔记", "聊天", "回看", "一下", "一下子", "帮忙", "看看", "统计", "查询", "类别", "分类", "周末",
    "分析", "总结", "归纳", "分为", "所有", "全部",
)

private val reviewChatGenericEntityTerms = setOf(
    "我这", "可以", "都有", "这些", "那些", "这个", "那个", "所有的", "全部的",
    "历史记录", "个人历史", "全部历史", "整个历史",
    "看看都",
)

internal val reviewChatDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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

internal data class ReviewChatQuestionProfile(
    val mode: ReviewChatQuestionMode,
    val intent: ReviewChatIntent,
    val requestedDate: LocalDate?,
    val keywords: List<String>,
    val entityTerms: List<String>,
    val wantsTimelineAnchor: Boolean,
    val wantsCount: Boolean,
    val wantsCategories: Boolean,
    val isExternalQuestion: Boolean,
)

internal fun buildReviewChatQuestionProfile(question: String): ReviewChatQuestionProfile {
    val parsedQuery = ReviewChatQueryParser.parse(question)
    val requestedDate = when (val scope = parsedQuery.timeScope) {
        is ReviewChatTimeScope.Day -> scope.date
        else -> null
    }
    return ReviewChatQuestionProfile(
        mode = parsedQuery.mode,
        intent = parsedQuery.intent,
        requestedDate = requestedDate,
        keywords = parsedQuery.keywords,
        entityTerms = parsedQuery.entityTerms,
        wantsTimelineAnchor = parsedQuery.wantsTimelineAnchor,
        wantsCount = parsedQuery.wantsCount,
        wantsCategories = parsedQuery.wantsCategories,
        isExternalQuestion = parsedQuery.isExternalQuestion,
    )
}

internal data class ReviewChatCorpusSelection(
    val scopedNotes: List<NoteEntity>,
    val queryNotes: List<NoteEntity>,
    val entityTerms: List<String>,
)

internal fun wantsReviewChatListExamples(question: String): Boolean =
    reviewChatListHints.any(question::contains)

internal fun buildReviewChatContextPacket(
    question: String,
    intent: ReviewChatIntent,
    notes: List<NoteEntity>,
    weeklyReview: WeeklyReviewState,
    maintenanceSnapshot: LocalKnowledgeMaintenanceSnapshot,
    wikiSnapshot: DirectionWikiSnapshot,
    sessionSummary: String,
    priorMessages: List<ReviewChatMessage> = emptyList(),
    rawNoteDetails: List<ReviewChatRawNoteDetail> = emptyList(),
    parsedQueryOverride: ReviewChatParsedQuery? = null,
    corpusContextOverride: ReviewChatCorpusContext? = null,
): ReviewChatContextPacket {
    val parsedQuery = (parsedQueryOverride ?: ReviewChatQueryParser.parse(question)).copy(intent = intent)
    val corpusContext = corpusContextOverride ?: ReviewChatCorpusQueryEngine.build(parsedQuery, notes)
    val effectiveRawNoteDetails = rawNoteDetails.ifEmpty { corpusContext.rawNoteDetails }
    val knowledgeBaseSnippets = if (parsedQuery.mode == ReviewChatQuestionMode.ANALYSIS) {
        buildKnowledgeBaseSnippets(
            intent = intent,
            weeklyReview = weeklyReview,
            maintenanceSnapshot = maintenanceSnapshot,
        )
    } else {
        emptyList()
    }
    val wikiSnippets = if (parsedQuery.mode == ReviewChatQuestionMode.ANALYSIS) {
        buildWikiSnippets(
            intent = intent,
            question = question,
            keywords = parsedQuery.keywords,
            wikiSnapshot = wikiSnapshot,
        )
    } else {
        emptyList()
    }
    val structuredSnippets = knowledgeBaseSnippets + wikiSnippets
    return ReviewChatContextPacket(
        questionMode = parsedQuery.mode,
        intent = intent,
        question = question,
        isExternalQuestion = parsedQuery.isExternalQuestion,
        wantsCount = parsedQuery.wantsCount,
        wantsCategories = parsedQuery.wantsCategories,
        querySummarySnippets = corpusContext.querySummarySnippets,
        deterministicAnswerSnippets = corpusContext.deterministicAnswerSnippets,
        categoryDigestSnippets = corpusContext.categoryDigestSnippets,
        sessionSummary = sessionSummary.takeIf { parsedQuery.mode == ReviewChatQuestionMode.ANALYSIS }.orEmpty(),
        collectionOverview = corpusContext.collectionOverview,
        conversationSnippets = if (parsedQuery.mode == ReviewChatQuestionMode.ANALYSIS) {
            buildConversationSnippets(priorMessages)
        } else {
            emptyList()
        },
        historyAnchors = if (
            parsedQuery.mode == ReviewChatQuestionMode.TIMELINE_ANCHOR ||
            (parsedQuery.mode == ReviewChatQuestionMode.ANALYSIS && corpusContext.historyAnchors.isNotEmpty())
        ) {
            corpusContext.historyAnchors
        } else {
            emptyList()
        },
        memoryDigestSnippets = emptyList(),
        memoryThreadSnippets = emptyList(),
        knowledgeBaseSnippets = knowledgeBaseSnippets,
        wikiSnippets = wikiSnippets,
        rawNoteEvidence = corpusContext.rawNoteEvidence,
        rawNoteDetails = effectiveRawNoteDetails,
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

internal fun extractReviewChatEntityTerms(question: String): List<String> {
    val stripped = reviewChatOperationPhrases.fold(question.lowercase()) { current, phrase ->
        current.replace(phrase.lowercase(), " ")
    }.replace(Regex("\\d{1,2}\\s*月\\s*\\d{0,2}(?:日|号)?"), " ")
        .replace(Regex("\\d{1,2}\\s*月(?:份)?"), " ")
        .replace("所有的记录", " ")
        .replace("全部的记录", " ")
        .replace("历史的记录", " ")
        .replace("历史记录", " ")
        .replace("都有哪些类别", " ")
        .replace("看看都有哪些类别", " ")
        .replace("看看都有哪些", " ")
        .replace("可以分为哪些类别", " ")
        .replace("都有哪些", " ")
        .replace("我这所有记录", " ")
        .replace("我这全部记录", " ")
        .replace("我这所有", " ")
        .replace("是什么时候", " ")
        .replace("什么时间", " ")
        .replace("什么时候", " ")
        .replace("是什么", " ")
        .replace("记了什么", " ")
        .replace("写了什么", " ")
        .replace("说过什么", " ")
        .replace("记了", " ")
        .replace("写了", " ")
        .replace("说过", " ")

    return Regex("[\\p{IsHan}A-Za-z0-9]{2,}")
        .findAll(stripped)
        .map { token ->
            token.value.trim()
                .removePrefix("关于")
                .removePrefix("有关")
                .removePrefix("相关")
                .removePrefix("这些")
                .removePrefix("那些")
                .removePrefix("所有")
                .removePrefix("全部")
                .removeSuffix("记录")
                .removeSuffix("笔记")
                .removeSuffix("内容")
                .removeSuffix("问题")
                .trim()
        }
        .filter { term ->
            term.isNotBlank() &&
                term !in reviewChatEntityStopWords &&
                !isGenericReviewChatEntityTerm(term) &&
                !term.matches(Regex("\\d+")) &&
                term.any { it.isLetterOrDigit() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        }
        .distinct()
        .toList()
}

private fun isGenericReviewChatEntityTerm(term: String): Boolean {
    val normalized = term.trim().lowercase()
    if (normalized.isBlank()) return true
    if (normalized in reviewChatGenericEntityTerms) return true
    if (normalized.length <= 2 && normalized in setOf("我这", "都有", "可以", "这些", "那些", "这个", "那个")) {
        return true
    }
    if (normalized.endsWith("记录") && normalized.length <= 4) return true
    if (normalized.endsWith("内容") && normalized.length <= 4) return true
    if (normalized.endsWith("信息") && normalized.length <= 4) return true
    return false
}

internal fun buildReviewChatCorpusSelection(
    question: String,
    mode: ReviewChatQuestionMode,
    notes: List<NoteEntity>,
    entityTerms: List<String>,
): ReviewChatCorpusSelection {
    val scopedNotes = filterNotesForRequestedScope(question, notes).sortedBy(NoteEntity::createdAt)
    if (mode == ReviewChatQuestionMode.EXTERNAL || scopedNotes.isEmpty() || entityTerms.isEmpty()) {
        return ReviewChatCorpusSelection(
            scopedNotes = scopedNotes,
            queryNotes = scopedNotes,
            entityTerms = entityTerms,
        )
    }

    val scored = scopedNotes.map { note ->
        note to scoreEntityTermMatch(note, entityTerms)
    }.filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<NoteEntity, Int>> { it.second }
                .thenBy { it.first.createdAt }
        )
        .map { it.first }

    val shouldPreferScopedDateHits =
        hasRequestedTimeScope(question) &&
            (mode == ReviewChatQuestionMode.FULL_RECORD || wantsReviewChatLinks(question))

    val queryNotes = when {
        scored.isEmpty() && shouldPreferScopedDateHits -> scopedNotes
        scored.isEmpty() &&
            mode in setOf(
                ReviewChatQuestionMode.COLLECTION_OVERVIEW,
                ReviewChatQuestionMode.RECORD_LOOKUP,
                ReviewChatQuestionMode.FULL_RECORD,
                ReviewChatQuestionMode.TIMELINE_ANCHOR,
            ) -> emptyList()
        scored.isNotEmpty() -> scored
        else -> scopedNotes
    }

    return ReviewChatCorpusSelection(
        scopedNotes = scopedNotes,
        queryNotes = queryNotes,
        entityTerms = entityTerms,
    )
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

internal fun buildRawNoteEvidence(
    mode: ReviewChatQuestionMode,
    intent: ReviewChatIntent,
    question: String,
    notes: List<NoteEntity>,
): List<ReviewChatEvidenceItem> {
    val scopedNotes = filterNotesForRequestedScope(question, notes)
    if (mode == ReviewChatQuestionMode.EXTERNAL) return emptyList()
    if (mode == ReviewChatQuestionMode.COLLECTION_OVERVIEW) {
        if (!wantsReviewChatListExamples(question)) return emptyList()
        val recentQuestion = listOf("最近", "这段时间", "近期").any(question::contains)
        return if (recentQuestion) {
            scopedNotes
                .sortedByDescending(NoteEntity::createdAt)
                .take(6)
                .map(NoteEntity::toReviewChatEvidence)
        } else {
            buildSupplementalRawSnippets(
                intent = intent,
                question = question,
                ranked = scopedNotes
                    .sortedBy { it.createdAt }
                    .map { RankedReviewChatNote(note = it, score = 0) },
                limit = 6,
            ).map(NoteEntity::toReviewChatEvidence)
        }
    }
    if (
        mode == ReviewChatQuestionMode.RECORD_LOOKUP &&
        wantsReviewChatCategories(question) &&
        extractReviewChatEntityTerms(question).isEmpty()
    ) {
        return buildSupplementalRawSnippets(
            intent = ReviewChatIntent.SYNTHESIZE,
            question = question,
            ranked = scopedNotes
                .sortedBy { it.createdAt }
                .map { RankedReviewChatNote(note = it, score = 0) },
            limit = 10,
        ).map(NoteEntity::toReviewChatEvidence)
    }
    if (mode == ReviewChatQuestionMode.RECORD_LOOKUP && hasRequestedTimeScope(question)) {
        return scopedNotes
            .sortedBy(NoteEntity::createdAt)
            .take(12)
            .map(NoteEntity::toReviewChatEvidence)
    }
    val limit = when (intent) {
        ReviewChatIntent.SYNTHESIZE -> 4
        ReviewChatIntent.DISCUSS -> 5
        ReviewChatIntent.RECALL -> 6
    }
    val ranked = ReviewChatRetriever.rank(
        query = ReviewChatQueryParser.parse(question).copy(intent = intent),
        notes = scopedNotes,
    )

    val directMatches = ranked.filter { it.score > 0 }.take(limit)
    if (directMatches.size >= limit) {
        return directMatches.map { it.note.toReviewChatEvidence() }
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
        .map(NoteEntity::toReviewChatEvidence)
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
        .sortedBy { it.note.createdAt }
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

private fun NoteEntity.toReviewChatEvidence(
    summaryMaxChars: Int = 120,
): ReviewChatEvidenceItem {
    val date = createdLocalDate().format(reviewChatDateFormatter)
    val compactContent = content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(summaryMaxChars)
    return ReviewChatEvidenceItem(
        noteId = id,
        dateLabel = date,
        title = topic.ifBlank { "未命名记录" },
        summary = compactContent,
    )
}

internal fun buildHistoryAnchors(
    question: String,
    notes: List<NoteEntity>,
): List<ReviewChatTimelineAnchor> {
    if (notes.isEmpty()) return emptyList()
    val wantsHistoryAnchors = reviewChatHistoryHints.any { question.contains(it) }
    if (!wantsHistoryAnchors) return emptyList()

    val sorted = notes.sortedBy(NoteEntity::createdAt)
    val earliest = sorted.first()
    val latest = sorted.last()
    return buildList {
        add(
            ReviewChatTimelineAnchor(
                label = "最早记录",
                item = earliest.toReviewChatEvidence(summaryMaxChars = 100),
            )
        )
        if (latest.id != earliest.id) {
            add(
                ReviewChatTimelineAnchor(
                    label = "最近记录",
                    item = latest.toReviewChatEvidence(summaryMaxChars = 100),
                )
            )
        }
    }
}

internal fun buildCollectionOverview(
    question: String,
    notes: List<NoteEntity>,
): ReviewChatCollectionOverview {
    val scopedNotes = filterNotesForRequestedScope(question, notes)
    val scopeLabel = requestedScopeLabel(question) ?: "全部历史"
    val today = LocalDate.now(ZoneId.systemDefault())
    if (scopedNotes.isEmpty()) {
        return ReviewChatCollectionOverview(
            scopeLabel = scopeLabel,
            totalCount = 0,
        )
    }

    val sorted = scopedNotes.sortedBy(NoteEntity::createdAt)
    val earliest = sorted.first().createdLocalDate().format(reviewChatDateFormatter)
    val latest = sorted.last().createdLocalDate().format(reviewChatDateFormatter)
    val last7DaysCount = notes.count { it.createdLocalDate() >= today.minusDays(7) }
    val last30DaysCount = notes.count { it.createdLocalDate() >= today.minusDays(30) }

    return ReviewChatCollectionOverview(
        scopeLabel = scopeLabel,
        totalCount = scopedNotes.size,
        earliestDateLabel = earliest,
        latestDateLabel = latest,
        last7DaysCount = last7DaysCount.takeIf {
            scopeLabel == "全部历史" && reviewChatCountHints.any(question::contains)
        },
        last30DaysCount = last30DaysCount.takeIf {
            scopeLabel == "全部历史" && reviewChatCountHints.any(question::contains)
        },
    )
}

internal fun NoteEntity.createdLocalDate(): LocalDate =
    Instant.ofEpochMilli(createdAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

internal fun scoreQuestionMatch(
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

internal fun scoreEntityTermMatch(
    note: NoteEntity,
    entityTerms: List<String>,
): Int {
    if (entityTerms.isEmpty()) return 0

    val title = note.topic.lowercase()
    val content = note.content.lowercase()
    val folder = note.folderKey.orEmpty().lowercase()
    val tags = note.tags.map(String::lowercase)

    var score = 0
    entityTerms.forEach { term ->
        val normalized = term.lowercase()
        val negatedInContent = listOf(
            "${normalized}无关",
            "和${normalized}无关",
            "与${normalized}无关",
            "${normalized}没关系",
            "和${normalized}没关系",
            "与${normalized}没关系",
        ).any(content::contains)
        if (negatedInContent) return@forEach
        when {
            title.contains(normalized) -> score += 12
            tags.any { it.contains(normalized) } -> score += 10
            folder.contains(normalized) -> score += 8
            content.contains(normalized) -> score += 5
        }
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
    private val planQueryWithCloud: (suspend (String) -> AiChatResult)? = null,
    private val runCloud: suspend (String) -> AiChatResult,
    private val runOnDevice: suspend (ReviewChatOnDeviceRequest) -> AiChatResult,
    private val streamOnDevice: (suspend (ReviewChatOnDeviceRequest) -> Flow<String>)? = null,
) {
    suspend fun answer(request: ReviewChatTurnRequest): ReviewChatTurnResult {
        val prepared = prepareReviewChatContext(request)

        val cloudPrompt = ReviewChatPromptFactory.cloud(prepared.packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(prepared.packet)
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
                            prompt = onDevicePrompt.userMessage,
                            systemInstruction = onDevicePrompt.systemInstruction,
                            extraContext = onDevicePrompt.extraContext,
                            resetConversation = request.priorMessages.isEmpty(),
                        )
                    ) else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "端侧未就绪")
                    }
                }
                ReviewChatProvider.LOCAL_MEMORY -> error("Review chat no longer routes through LOCAL_MEMORY")
            }
            if (result is AiChatResult.Success) {
                val fallbackOccurred = index > 0
                val normalizedAnswer = result.content.trim()
                return ReviewChatTurnResult(
                    answer = normalizedAnswer,
                    structuredAnswer = parseReviewChatStructuredAnswer(normalizedAnswer),
                    provider = provider,
                    fallbackOccurred = fallbackOccurred,
                    providerLine = buildReviewChatProviderLine(provider, fallbackOccurred),
                    sessionSummary = "${request.question.take(40)}｜${normalizedAnswer.take(80)}",
                    titleSuggestion = request.question.take(18),
                    referencedNoteId = prepared.referencedNotes.singleOrNull()?.noteId,
                    referencedNotes = prepared.referencedNotes,
                )
            }
            if (result is AiChatResult.Failure) {
                lastFailure = result
            }
        }
        error(lastFailure?.message ?: "No provider returned a usable review chat answer")
    }

    fun answerStream(request: ReviewChatTurnRequest): Flow<ReviewChatTurnEvent> = flow {
        val prepared = prepareReviewChatContext(request)

        val cloudPrompt = ReviewChatPromptFactory.cloud(prepared.packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(prepared.packet)
        val attempts = when (resolveExecutionMode()) {
            AiExecutionMode.AUTOMATIC -> listOf(ReviewChatProvider.CLOUD, ReviewChatProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(ReviewChatProvider.CLOUD)
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(ReviewChatProvider.ON_DEVICE)
        }

        var lastFailure: AiChatResult.Failure? = null
        attempts.forEachIndexed { index, provider ->
            when (provider) {
                ReviewChatProvider.CLOUD -> {
                    val result = if (isCloudConfigured()) {
                        runCloud(cloudPrompt)
                    } else {
                        AiChatResult.Failure(AiFailureReason.CONFIG, "云侧未配置")
                    }
                    if (result is AiChatResult.Success) {
                        val normalizedAnswer = result.content.trim()
                        emit(
                            ReviewChatTurnEvent.Complete(
                                ReviewChatTurnResult(
                                    answer = normalizedAnswer,
                                    structuredAnswer = parseReviewChatStructuredAnswer(normalizedAnswer),
                                    provider = provider,
                                    fallbackOccurred = index > 0,
                                    providerLine = buildReviewChatProviderLine(provider, fallbackOccurred = index > 0),
                                    sessionSummary = "${request.question.take(40)}｜${normalizedAnswer.take(80)}",
                                    titleSuggestion = request.question.take(18),
                                    referencedNoteId = prepared.directRawNoteDetails.singleOrNull()?.noteId,
                                    referencedNotes = prepared.referencedNotes,
                                )
                            )
                        )
                        return@flow
                    }
                    if (result is AiChatResult.Failure) {
                        lastFailure = result
                    }
                }

                ReviewChatProvider.ON_DEVICE -> {
                    if (!isOnDeviceReady()) {
                        lastFailure = AiChatResult.Failure(AiFailureReason.CONFIG, "端侧未就绪")
                        return@forEachIndexed
                    }

                    val providerLine = buildReviewChatProviderLine(provider, fallbackOccurred = index > 0)
                    if (streamOnDevice != null) {
                        val buffer = StringBuilder()
                        runCatching {
                            streamOnDevice.invoke(
                                ReviewChatOnDeviceRequest(
                                    sessionId = request.sessionId,
                                    prompt = onDevicePrompt.userMessage,
                                    systemInstruction = onDevicePrompt.systemInstruction,
                                    extraContext = onDevicePrompt.extraContext,
                                    resetConversation = request.priorMessages.isEmpty(),
                                )
                            ).collect { chunk ->
                                if (chunk.isBlank()) return@collect
                                buffer.append(chunk)
                                emit(
                                    ReviewChatTurnEvent.Partial(
                                        content = buffer.toString(),
                                        provider = provider,
                                        providerLine = providerLine,
                                    )
                                )
                            }
                        }.onSuccess {
                            val content = buffer.toString().trim()
                            if (isUsableReviewChatAnswer(content)) {
                                emit(
                                    ReviewChatTurnEvent.Complete(
                                        ReviewChatTurnResult(
                                            answer = content,
                                            structuredAnswer = parseReviewChatStructuredAnswer(content),
                                            provider = provider,
                                            fallbackOccurred = index > 0,
                                            providerLine = providerLine,
                                            sessionSummary = "${request.question.take(40)}｜${content.take(80)}",
                                            titleSuggestion = request.question.take(18),
                                            referencedNoteId = prepared.referencedNotes.singleOrNull()?.noteId,
                                            referencedNotes = prepared.referencedNotes,
                                        )
                                    )
                                )
                                return@flow
                            }
                            lastFailure = AiChatResult.Failure(
                                reason = AiFailureReason.OTHER,
                                message = "本地模型没有返回可用回答",
                            )
                        }.onFailure { error ->
                            lastFailure = AiChatResult.Failure(
                                reason = AiFailureReason.OTHER,
                                message = error.message ?: "本地模型推理失败：请稍后再试",
                            )
                        }
                    } else {
                        val result = runOnDevice(
                            ReviewChatOnDeviceRequest(
                                sessionId = request.sessionId,
                                prompt = onDevicePrompt.userMessage,
                                systemInstruction = onDevicePrompt.systemInstruction,
                                extraContext = onDevicePrompt.extraContext,
                                resetConversation = request.priorMessages.isEmpty(),
                            )
                        )
                        if (result is AiChatResult.Success) {
                            val normalizedAnswer = result.content.trim()
                            emit(
                                ReviewChatTurnEvent.Complete(
                                    ReviewChatTurnResult(
                                        answer = normalizedAnswer,
                                        structuredAnswer = parseReviewChatStructuredAnswer(normalizedAnswer),
                                        provider = provider,
                                        fallbackOccurred = index > 0,
                                        providerLine = providerLine,
                                        sessionSummary = "${request.question.take(40)}｜${normalizedAnswer.take(80)}",
                                        titleSuggestion = request.question.take(18),
                                        referencedNoteId = prepared.referencedNotes.singleOrNull()?.noteId,
                                        referencedNotes = prepared.referencedNotes,
                                    )
                                )
                            )
                            return@flow
                        }
                        if (result is AiChatResult.Failure) {
                            lastFailure = result
                        }
                    }
                }

                ReviewChatProvider.LOCAL_MEMORY -> error("Review chat no longer routes through LOCAL_MEMORY")
            }
        }

        error(lastFailure?.message ?: "No provider returned a usable review chat answer")
    }

    private suspend fun prepareReviewChatContext(
        request: ReviewChatTurnRequest,
    ): PreparedReviewChatContext {
        val parsedQuery = resolveParsedQuery(request.question)
        val notes = loadNotes()
        val corpusContext = ReviewChatCorpusQueryEngine.build(parsedQuery, notes)
        val weeklyReview = loadWeeklyReview()
        val maintenanceSnapshot = loadMaintenanceSnapshot()
        val wikiSnapshot = loadWikiSnapshot()
        val packet = buildReviewChatContextPacket(
            question = request.question,
            intent = parsedQuery.intent,
            notes = notes,
            weeklyReview = weeklyReview,
            maintenanceSnapshot = maintenanceSnapshot,
            wikiSnapshot = wikiSnapshot,
            sessionSummary = request.priorMessages
                .takeLast(2)
                .joinToString("\n") { it.content.take(120) },
            priorMessages = request.priorMessages,
            rawNoteDetails = corpusContext.rawNoteDetails,
            parsedQueryOverride = parsedQuery,
            corpusContextOverride = corpusContext,
        )
        return PreparedReviewChatContext(
            intent = parsedQuery.intent,
            packet = packet,
            directRawNoteDetails = corpusContext.rawNoteDetails,
            referencedNotes = corpusContext.referencedNotes,
        )
    }

    private suspend fun resolveParsedQuery(question: String): ReviewChatParsedQuery {
        val fallbackQuery = ReviewChatQueryParser.parse(question)
        val canUseCloudPlanning =
            planQueryWithCloud != null &&
                resolveExecutionMode() != AiExecutionMode.ON_DEVICE_ONLY &&
                isCloudConfigured()
        if (!canUseCloudPlanning) return fallbackQuery

        val planResult = planQueryWithCloud.invoke(
            ReviewChatModelQueryPlanner.buildPlanningPrompt(question)
        )
        if (planResult !is AiChatResult.Success) return fallbackQuery

        val plannedQuery = ReviewChatModelQueryPlanner.parse(planResult.content) ?: return fallbackQuery
        return ReviewChatQueryParser.parse(question, plannedQuery)
    }
}

private data class PreparedReviewChatContext(
    val intent: ReviewChatIntent,
    val packet: ReviewChatContextPacket,
    val directRawNoteDetails: List<ReviewChatRawNoteDetail>,
    val referencedNotes: List<ReviewChatReferencedNote>,
)

private fun isUsableReviewChatAnswer(content: String): Boolean {
    val normalized = content
        .replace(Regex("[\\p{Punct}\\p{P}\\s]+"), "")
        .trim()
    return normalized.length >= 4
}

internal fun buildDirectRawNoteDetails(
    question: String,
    mode: ReviewChatQuestionMode,
    notes: List<NoteEntity>,
): List<ReviewChatRawNoteDetail> {
    if (mode != ReviewChatQuestionMode.FULL_RECORD) return emptyList()
    val wantsFullRecord = listOf("完整", "全文", "原文", "全部内容").any(question::contains)
    if (!hasRequestedTimeScope(question) && !wantsFullRecord) return emptyList()

    val filtered = if (hasRequestedTimeScope(question)) {
        filterNotesForRequestedScope(question, notes).sortedBy(NoteEntity::createdAt)
    } else {
        ReviewChatRetriever.rank(
            query = ReviewChatQueryParser.parse(question),
            notes = notes,
        ).filter { it.score > 0 }
            .map { it.note }
            .take(2)
    }

    return filtered.map { note ->
        ReviewChatRawNoteDetail(
            noteId = note.id,
            title = note.topic.ifBlank { "未命名记录" },
            dateLabel = note.createdLocalDate().format(reviewChatDateFormatter),
            fullContent = note.content.trim(),
        )
    }
}

internal fun buildReferencedNotes(
    question: String,
    intent: ReviewChatIntent,
    notes: List<NoteEntity>,
    directRawNoteDetails: List<ReviewChatRawNoteDetail>,
): List<ReviewChatReferencedNote> {
    if (!wantsReviewChatLinks(question)) return emptyList()
    val parsedQuery = ReviewChatQueryParser.parse(question).copy(intent = intent)
    if (parsedQuery.isExternalQuestion) return emptyList()
    if (parsedQuery.wantsCount) return emptyList()
    if (directRawNoteDetails.isNotEmpty()) {
        return directRawNoteDetails.map { detail ->
            ReviewChatReferencedNote(
                noteId = detail.noteId,
                title = detail.title,
                dateLabel = detail.dateLabel,
            )
        }
    }

    if (hasRequestedTimeScope(question)) {
        return filterNotesForRequestedScope(question, notes).asSequence()
            .sortedBy(NoteEntity::createdAt)
            .take(3)
            .map { it.toReferencedNote() }
            .toList()
    }

    val wantsEarliest = listOf("第一条", "第一次", "最早", "最初", "一开始").any(question::contains)
    if (wantsEarliest) {
        return notes.sortedBy(NoteEntity::createdAt)
            .take(3)
            .map(NoteEntity::toReferencedNote)
    }

    val ranked = ReviewChatRetriever.rank(parsedQuery, notes)

    val directMatches = ranked.filter { it.score > 0 }.take(3).map(RankedReviewChatNote::note)
    val selected = when {
        directMatches.isNotEmpty() -> directMatches
        parsedQuery.mode == ReviewChatQuestionMode.TIMELINE_ANCHOR ||
            reviewChatHistoryHints.any { question.contains(it) } ->
            buildSupplementalRawSnippets(
                intent = intent,
                question = question,
                ranked = ranked,
                limit = 3,
            )
        else -> emptyList()
    }
    return selected.distinctBy(NoteEntity::id).map(NoteEntity::toReferencedNote)
}

internal fun wantsReviewChatLinks(question: String): Boolean =
    reviewChatLinkHints.any(question::contains)

internal fun wantsReviewChatCategories(question: String): Boolean =
    listOf("类别", "分类", "哪几类", "哪些类别", "归类").any(question::contains)

private fun NoteEntity.toReferencedNote(): ReviewChatReferencedNote =
    ReviewChatReferencedNote(
        noteId = id,
        title = topic.ifBlank { "未命名记录" },
        dateLabel = createdLocalDate().format(reviewChatDateFormatter),
    )

internal fun requestedDateForReviewChat(question: String): LocalDate? {
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

internal fun requestedMonthForReviewChat(question: String): YearMonth? {
    if (requestedDateForReviewChat(question) != null || requestedDateRangeForReviewChat(question) != null) return null
    val today = LocalDate.now(ZoneId.systemDefault())
    val match = Regex("(\\d{1,2})\\s*月(?:份)?").find(question) ?: return null
    val month = match.groupValues[1].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return YearMonth.of(today.year, month)
}

internal fun requestedDateRangeForReviewChat(question: String): ReviewChatTimeScope.Range? {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when {
        "本周末" in question || "这个周末" in question || question.trim() == "周末" -> {
            val saturday = when (today.dayOfWeek.value) {
                6 -> today
                7 -> today.minusDays(1)
                else -> today.minusDays((today.dayOfWeek.value + 1).toLong())
            }
            ReviewChatTimeScope.Range(
                start = saturday,
                endInclusive = saturday.plusDays(1),
                label = "本周末",
            )
        }
        "上周末" in question -> {
            val thisWeekSaturday = when (today.dayOfWeek.value) {
                6 -> today
                7 -> today.minusDays(1)
                else -> today.minusDays((today.dayOfWeek.value + 1).toLong())
            }
            val lastSaturday = thisWeekSaturday.minusWeeks(1)
            ReviewChatTimeScope.Range(
                start = lastSaturday,
                endInclusive = lastSaturday.plusDays(1),
                label = "上周末",
            )
        }
        else -> null
    }
}

internal fun hasRequestedTimeScope(question: String): Boolean =
    requestedDateForReviewChat(question) != null ||
        requestedMonthForReviewChat(question) != null ||
        requestedDateRangeForReviewChat(question) != null

internal fun filterNotesForRequestedScope(
    question: String,
    notes: List<NoteEntity>,
): List<NoteEntity> {
    val requestedDate = requestedDateForReviewChat(question)
    if (requestedDate != null) {
        return notes.filter { it.createdLocalDate() == requestedDate }
    }
    val requestedMonth = requestedMonthForReviewChat(question)
    if (requestedMonth != null) {
        return notes.filter { YearMonth.from(it.createdLocalDate()) == requestedMonth }
    }
    val requestedRange = requestedDateRangeForReviewChat(question)
    if (requestedRange != null) {
        return notes.filter { created ->
            val date = created.createdLocalDate()
            date >= requestedRange.start && date <= requestedRange.endInclusive
        }
    }
    return notes
}

internal fun requestedScopeLabel(question: String): String? =
    requestedDateForReviewChat(question)?.format(reviewChatDateFormatter)
        ?: requestedMonthForReviewChat(question)?.let { "${it.monthValue}月" }
        ?: requestedDateRangeForReviewChat(question)?.label
