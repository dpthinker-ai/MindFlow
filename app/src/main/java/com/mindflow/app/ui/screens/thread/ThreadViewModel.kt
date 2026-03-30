package com.mindflow.app.ui.screens.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ThreadUiState(
    val threadKey: String,
    val title: String,
    val notes: List<NoteEntity> = emptyList(),
    val totalCount: Int = 0,
    val ideaCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
    val threadSummary: String = "",
    val threadBlocker: String = "",
    val threadNextStep: String = "",
    val weeklyStatsLine: String = "",
    val weeklyLines: List<String> = emptyList(),
    val researchHighlights: List<String> = emptyList(),
    val researchQueries: List<String> = emptyList(),
    val researchSource: DailyBriefSource = DailyBriefSource.RULE,
    val focusNote: NoteEntity? = null,
    val focusReason: String = "",
    val insightSource: DailyBriefSource = DailyBriefSource.RULE,
    val isRefreshingInsights: Boolean = false,
    val isFollowed: Boolean = false,
) {
    val insightSourceLabel: String
        get() = when {
            isRefreshingInsights -> "生成中..."
            threadSummary.isBlank() && threadNextStep.isBlank() -> ""
            insightSource == DailyBriefSource.AI -> "AI"
            else -> "规则"
        }
}

sealed interface ThreadEvent {
    data class Message(val text: String) : ThreadEvent
}

class ThreadViewModel(
    private val noteRepository: NoteRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val threadPreferencesRepository: ThreadPreferencesRepository,
    private val aiServiceClient: AiServiceClient,
    private val threadKey: String,
) : ViewModel() {
    private data class ThreadInsightState(
        val signature: String = "",
        val summary: String = "",
        val blocker: String = "",
        val nextStep: String = "",
        val researchHighlights: List<String> = emptyList(),
        val researchQueries: List<String> = emptyList(),
        val researchSource: DailyBriefSource = DailyBriefSource.RULE,
        val source: DailyBriefSource = DailyBriefSource.RULE,
        val isLoading: Boolean = false,
    )

    private val _insightState = MutableStateFlow(ThreadInsightState())
    private val _events = MutableSharedFlow<ThreadEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ThreadUiState> = combine(
        noteRepository.observeAllNotes(),
        threadPreferencesRepository.settings.map { it.isFollowed(threadKey) },
        _insightState,
    ) { allNotes, isFollowed, insight ->
        val notes = NoteConnectionAnalyzer.notesForThread(threadKey, allNotes)
        val focusNote = pickFocusNote(notes)
        val weeklyReview = buildThreadWeeklyReview(notes)
        ThreadUiState(
            threadKey = threadKey,
            title = NoteConnectionAnalyzer.titleForThread(threadKey),
            notes = notes,
            totalCount = notes.size,
            ideaCount = notes.count { it.status == NoteStatus.IDEA },
            inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = notes.count { it.status == NoteStatus.DONE },
            threadSummary = insight.summary,
            threadBlocker = insight.blocker,
            threadNextStep = insight.nextStep,
            weeklyStatsLine = weeklyReview.statsLine,
            weeklyLines = weeklyReview.lines,
            researchHighlights = insight.researchHighlights,
            researchQueries = insight.researchQueries,
            researchSource = insight.researchSource,
            focusNote = focusNote,
            focusReason = focusReasonFor(focusNote),
            insightSource = insight.source,
            isRefreshingInsights = insight.isLoading,
            isFollowed = isFollowed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThreadUiState(
            threadKey = threadKey,
            title = NoteConnectionAnalyzer.titleForThread(threadKey),
        ),
    )

    init {
        viewModelScope.launch {
            noteRepository.observeAllNotes().collect { allNotes ->
                refreshInsightsIfNeeded(NoteConnectionAnalyzer.notesForThread(threadKey, allNotes))
            }
        }
    }

    fun archiveNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.setArchived(noteId, archived = true)
            _events.emit(ThreadEvent.Message("已归档"))
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(ThreadEvent.Message("已删除记录"))
        }
    }

    fun toggleFollow() {
        viewModelScope.launch {
            val nowFollowed = threadPreferencesRepository.toggleFollow(threadKey)
            _events.emit(ThreadEvent.Message(if (nowFollowed) "已加入关注方向" else "已取消关注"))
        }
    }

    fun promoteFocusNote() {
        viewModelScope.launch {
            val note = uiState.value.focusNote ?: return@launch
            if (note.status != NoteStatus.IDEA) {
                _events.emit(ThreadEvent.Message("这条记录已经在推进中了"))
                return@launch
            }
            noteRepository.updateNote(
                noteId = note.id,
                content = note.content,
                topic = note.topic,
                folderKey = note.folderKey,
                tags = note.tags,
                status = NoteStatus.IN_PROGRESS,
                isArchived = note.isArchived,
                folderManuallyEdited = note.folderSource == FolderSource.MANUAL,
                topicManuallyEdited = note.topicSource == TopicSource.MANUAL,
                tagsManuallyEdited = note.tagSource == TagSource.MANUAL,
            )
            _events.emit(ThreadEvent.Message("已开始推进这条记录"))
        }
    }

    private suspend fun refreshInsightsIfNeeded(notes: List<NoteEntity>) {
        val signature = buildSignature(notes)
        val current = _insightState.value
        if (notes.isEmpty()) {
            _insightState.value = ThreadInsightState(signature = signature)
            return
        }
        if (current.signature == signature && (current.summary.isNotBlank() || current.nextStep.isNotBlank())) {
            return
        }
        refreshInsights(notes, signature)
    }

    private suspend fun refreshInsights(
        notes: List<NoteEntity>,
        signature: String,
    ) {
        _insightState.value = _insightState.value.copy(isLoading = true, signature = signature)
        val fallback = buildRuleInsights(notes)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        if (settings.aiEnabled && settings.isConfigured && notes.isNotEmpty()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (val result = aiServiceClient.generateThreadWorkspace(settings, buildAiContext(notes))) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        _insightState.value = ThreadInsightState(
                            signature = signature,
                            summary = lines.getOrElse(0) { fallback.summary },
                            blocker = lines.getOrElse(1) { fallback.blocker },
                            nextStep = lines.getOrElse(2) { fallback.nextStep },
                            researchHighlights = fallback.researchHighlights,
                            researchQueries = fallback.researchQueries,
                            researchSource = fallback.researchSource,
                            source = DailyBriefSource.AI,
                            isLoading = false,
                        )
                        refreshResearch(notes, signature, preserveInsight = true)
                        return
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule-based insights.
                }
            }
        }

        _insightState.value = ThreadInsightState(
            signature = signature,
            summary = fallback.summary,
            blocker = fallback.blocker,
            nextStep = fallback.nextStep,
            researchHighlights = fallback.researchHighlights,
            researchQueries = fallback.researchQueries,
            researchSource = fallback.researchSource,
            source = DailyBriefSource.RULE,
            isLoading = false,
        )
        refreshResearch(notes, signature, preserveInsight = true)
    }

    private fun buildAiContext(notes: List<NoteEntity>): String {
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(10)
        val tags = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("、") { "${it.key}(${it.value})" }
        return buildString {
            appendLine("你正在为一个长期方向生成线程工作台。")
            appendLine("线程标题：${NoteConnectionAnalyzer.titleForThread(threadKey)}")
            appendLine("总记录：${notes.size} 条；想法${notes.count { it.status == NoteStatus.IDEA }}条，进行中${notes.count { it.status == NoteStatus.IN_PROGRESS }}条，已实现${notes.count { it.status == NoteStatus.DONE }}条。")
            if (tags.isNotBlank()) {
                appendLine("高频标签：$tags")
            }
            appendLine("最近记录：")
            latestNotes.forEach { note ->
                appendLine("${note.topic.ifBlank { "未命名记录" }}｜${note.status.label}｜${note.content.compactPreview(120)}")
            }
        }
    }

    private suspend fun refreshResearch(
        notes: List<NoteEntity>,
        signature: String,
        preserveInsight: Boolean,
    ) {
        val fallback = buildRuleResearch(notes)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        if (settings.aiEnabled && settings.isConfigured && notes.isNotEmpty()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (val result = aiServiceClient.generateResearchBrief(settings, buildAiResearchContext(notes))) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.size >= 3) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        _insightState.value = _insightState.value.copy(
                            signature = signature,
                            researchHighlights = lines.take(2),
                            researchQueries = lines.drop(2).take(2),
                            researchSource = DailyBriefSource.AI,
                            isLoading = if (preserveInsight) false else _insightState.value.isLoading,
                        )
                        return
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule-based research.
                }
            }
        }

        _insightState.value = _insightState.value.copy(
            signature = signature,
            researchHighlights = fallback.highlights,
            researchQueries = fallback.queries,
            researchSource = DailyBriefSource.RULE,
            isLoading = if (preserveInsight) false else _insightState.value.isLoading,
        )
    }

    private fun buildRuleInsights(notes: List<NoteEntity>): TripleInsight {
        val latest = notes.firstOrNull()
        val inProgress = notes.filter { it.status == NoteStatus.IN_PROGRESS }
        val repeatedTag = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val summary = when {
            inProgress.isNotEmpty() -> "这条方向已经从想法积累进入推进阶段，重点在于把零散记录压成一条连续主线。"
            notes.size >= 3 -> "这条方向已经有了重复出现的线索，适合从记录层升级成一个长期主题。"
            else -> "这条方向还在形成期，先把核心问题写得更具体，别急着摊开太多分支。"
        }
        val blocker = when {
            repeatedTag != null -> "目前最大的卡点，是围绕「$repeatedTag」还没有形成一个被持续验证的问题定义。"
            latest != null -> "目前最大的卡点，是最近这条记录还停在描述层，没有被压成一个明确的验证动作。"
            else -> "目前最大的卡点，是有效记录还不够多，暂时难以稳定出主线。"
        }
        val nextStep = when {
            latest != null -> "下一步先围绕「${latest.topic.ifBlank { "未命名记录" }}」补一条最小可验证动作，再继续往下推进。"
            else -> "下一步先补一条更具体的真实记录，让这个方向有可以继续组织的抓手。"
        }
        return TripleInsight(summary = summary, blocker = blocker, nextStep = nextStep)
    }

    private fun buildAiResearchContext(notes: List<NoteEntity>): String {
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(8)
        val tags = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("、") { "${it.key}(${it.value})" }
        return buildString {
            appendLine("你正在为一个人的长期方向生成外部研究增强。")
            appendLine("方向标题：${NoteConnectionAnalyzer.titleForThread(threadKey)}")
            appendLine("记录数：${notes.size} 条；进行中${notes.count { it.status == NoteStatus.IN_PROGRESS }}条，已实现${notes.count { it.status == NoteStatus.DONE }}条。")
            if (tags.isNotBlank()) {
                appendLine("高频标签：$tags")
            }
            appendLine("最近记录：")
            latestNotes.forEach { note ->
                appendLine("${note.topic.ifBlank { "未命名记录" }}｜${note.content.compactPreview(100)}")
            }
        }
    }

    private fun buildRuleResearch(notes: List<NoteEntity>): ResearchBundle {
        val title = NoteConnectionAnalyzer.titleForThread(threadKey)
        val repeatedTag = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val primaryKeyword = repeatedTag ?: title
        val highlights = buildList {
            add("先找 2 到 3 个做过类似方向的产品、论文或个人实践，确认别人是怎么切入这个问题的。")
            if (repeatedTag != null) {
                add("重点关注「$repeatedTag」在相邻领域里的做法，看看有没有可以借过来的结构或验证路径。")
            } else {
                add("别只搜同类产品，也试着找相邻场景里的成熟方法，通常突破感会从跨域借法里出来。")
            }
        }
        val queries = buildList {
            add("${primaryKeyword} 产品 方案 案例")
            add("${title} workflow design benchmark")
        }.distinct().take(2)
        return ResearchBundle(highlights = highlights.take(2), queries = queries)
    }

    private fun buildThreadWeeklyReview(notes: List<NoteEntity>): ThreadWeeklyReview {
        val weeklyNotes = notes.currentWeekNotes()
        if (weeklyNotes.isEmpty()) {
            return ThreadWeeklyReview(
                statsLine = "这周还没有新的推进",
                lines = listOf("先补一条真实记录，让这个方向重新开始流动起来。"),
            )
        }
        val latest = weeklyNotes.maxByOrNull { it.updatedAt }
        val repeatedTag = weeklyNotes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val lines = buildList {
            if (repeatedTag != null) {
                add("这周主线主要围绕「$repeatedTag」，可以继续把它压成更明确的问题定义。")
            } else {
                add("这周这个方向还在继续聚焦，别再往外摊更多分支。")
            }
            latest?.let {
                add("下周先接着推进「${it.topic.ifBlank { "未命名记录" }}」，别让最近这条又沉下去。")
            }
        }.take(2)
        return ThreadWeeklyReview(
            statsLine = buildList {
                add("本周 ${weeklyNotes.size} 条")
                val progressCount = weeklyNotes.count { it.status == NoteStatus.IN_PROGRESS }
                val doneCount = weeklyNotes.count { it.status == NoteStatus.DONE }
                if (progressCount > 0) add("推进 ${progressCount} 条")
                if (doneCount > 0) add("完成 ${doneCount} 条")
            }.joinToString(" · "),
            lines = lines,
        )
    }

    private fun parseAiLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .removePrefix("3.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(3)
            .toList()

    private fun buildSignature(notes: List<NoteEntity>): String =
        "${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private fun pickFocusNote(notes: List<NoteEntity>): NoteEntity? =
        notes
            .filter { it.status == NoteStatus.IN_PROGRESS }
            .maxByOrNull { it.updatedAt }
            ?: notes
                .filter { it.status == NoteStatus.IDEA }
                .maxByOrNull { it.updatedAt }
            ?: notes.maxByOrNull { it.updatedAt }

    private fun focusReasonFor(note: NoteEntity?): String =
        when (note?.status) {
            NoteStatus.IN_PROGRESS -> "这条记录最接近真实推进，可以继续沿着它把方向压实。"
            NoteStatus.IDEA -> "这条记录最新、最值得先压成动作，别让它只停在想法层。"
            NoteStatus.DONE -> "这条记录已经做成了，可以把它当作下一轮延展的起点。"
            null -> ""
        }

    private fun List<NoteEntity>.currentWeekNotes(): List<NoteEntity> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val weekStart = today.with(DayOfWeek.MONDAY)
        return filter { note ->
            val noteDate = Instant.ofEpochMilli(note.updatedAt).atZone(zoneId).toLocalDate()
            !noteDate.isBefore(weekStart)
        }
    }

    private fun String.compactPreview(maxLength: Int): String =
        replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            aiSettingsRepository: AiSettingsRepository,
            threadPreferencesRepository: ThreadPreferencesRepository,
            aiServiceClient: AiServiceClient,
            threadKey: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThreadViewModel(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    aiServiceClient = aiServiceClient,
                    threadKey = threadKey,
                )
            }
        }
    }

    private data class TripleInsight(
        val summary: String,
        val blocker: String,
        val nextStep: String,
        val researchHighlights: List<String> = emptyList(),
        val researchQueries: List<String> = emptyList(),
        val researchSource: DailyBriefSource = DailyBriefSource.RULE,
    )

    private data class ResearchBundle(
        val highlights: List<String>,
        val queries: List<String>,
    )

    private data class ThreadWeeklyReview(
        val statsLine: String,
        val lines: List<String>,
    )
}
