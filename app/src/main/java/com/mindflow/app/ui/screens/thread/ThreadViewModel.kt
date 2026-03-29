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
import java.time.LocalDate

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
                            source = DailyBriefSource.AI,
                            isLoading = false,
                        )
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
            source = DailyBriefSource.RULE,
            isLoading = false,
        )
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
    )
}
