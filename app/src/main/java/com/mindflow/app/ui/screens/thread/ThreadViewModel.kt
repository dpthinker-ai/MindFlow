package com.mindflow.app.ui.screens.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.DirectionStageHistoryAnalyzer
import com.mindflow.app.data.connect.DirectionStageHistoryEntry
import com.mindflow.app.data.connect.DirectionAsset
import com.mindflow.app.data.connect.DirectionAssetAnalyzer
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ResearchEvidenceAnalyzer
import com.mindflow.app.data.connect.ResearchEvidenceSummary
import com.mindflow.app.data.connect.ResearchCluster
import com.mindflow.app.data.connect.ThreadResearchAnalyzer
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek

data class ThreadUiState(
    val threadKey: String,
    val title: String,
    val notes: List<NoteEntity> = emptyList(),
    val researchNotes: List<NoteEntity> = emptyList(),
    val researchClusters: List<ResearchCluster> = emptyList(),
    val totalCount: Int = 0,
    val ideaCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
    val threadSummary: String = "",
    val threadBlocker: String = "",
    val threadNextStep: String = "",
    val stage: DirectionStage = DirectionStage.FORMING,
    val stageReason: String = "",
    val rhythmLine: String = "",
    val dominantHorizon: NoteHorizon = NoteHorizon.MEDIUM,
    val stageHistory: List<DirectionStageHistoryEntry> = emptyList(),
    val directionAssets: List<DirectionAsset> = emptyList(),
    val wikiAssetSummary: String = "",
    val wikiGroundingLine: String = "",
    val wikiSignalPoints: List<String> = emptyList(),
    val wikiHypothesisPoints: List<String> = emptyList(),
    val wikiVerifiedPoints: List<String> = emptyList(),
    val wikiValidatedPoints: List<String> = emptyList(),
    val wikiOpenQuestions: List<String> = emptyList(),
    val wikiContinuityLine: String = "",
    val wikiTrajectoryLine: String = "",
    val wikiStageHistorySummary: String = "",
    val wikiUpdatedAt: Long = 0L,
    val weeklyStatsLine: String = "",
    val weeklyLines: List<String> = emptyList(),
    val researchOutsideAngle: String = "",
    val researchGap: String = "",
    val researchContrarianQuestion: String = "",
    val researchExternalHypothesis: String = "",
    val researchQueries: List<String> = emptyList(),
    val researchSource: DailyBriefSource = DailyBriefSource.RULE,
    val researchEvidence: ResearchEvidenceSummary = ResearchEvidenceSummary(),
    val focusNote: NoteEntity? = null,
    val focusReason: String = "",
    val executionWhyNow: String = "",
    val validationStep: String = "",
    val validationReason: String = "",
    val postValidationAction: String = "",
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
    private val threadPreferencesRepository: ThreadPreferencesRepository,
    private val threadExecutionPlanner: ThreadExecutionPlanner,
    private val externalResearchPlanner: ExternalResearchPlanner,
    private val directionWikiCoordinator: DirectionWikiCoordinator,
    private val threadKey: String,
) : ViewModel() {
    private data class ThreadInsightState(
        val signature: String = "",
        val summary: String = "",
        val blocker: String = "",
        val nextStep: String = "",
        val stage: DirectionStage = DirectionStage.FORMING,
        val stageReason: String = "",
        val rhythmLine: String = "",
        val dominantHorizon: NoteHorizon = NoteHorizon.MEDIUM,
        val executionWhyNow: String = "",
        val validationStep: String = "",
        val validationReason: String = "",
        val postValidationAction: String = "",
        val researchOutsideAngle: String = "",
        val researchGap: String = "",
        val researchContrarianQuestion: String = "",
        val researchExternalHypothesis: String = "",
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
        directionWikiCoordinator.snapshot,
    ) { allNotes, isFollowed, insight, wikiSnapshot ->
        val notes = NoteConnectionAnalyzer.notesForThread(threadKey, allNotes)
        val researchNotes = notes.filter(ThreadResearchAnalyzer::isResearchMemoryNote).take(3)
        val researchClusters = ThreadResearchAnalyzer.buildResearchClusters(
            notes = researchNotes,
            threadTitle = NoteConnectionAnalyzer.titleForThread(threadKey),
        )
        val stageHistory = DirectionStageHistoryAnalyzer.build(notes)
        val directionAssets = DirectionAssetAnalyzer.build(notes)
        val focusNote = pickFocusNote(notes)
        val weeklyReview = buildThreadWeeklyReview(notes)
        val wikiDirection = wikiSnapshot.directions[threadKey]
        ThreadUiState(
            threadKey = threadKey,
            title = NoteConnectionAnalyzer.titleForThread(threadKey),
            notes = notes,
            researchNotes = researchNotes,
            researchClusters = researchClusters,
            totalCount = notes.size,
            ideaCount = notes.count { it.status == NoteStatus.IDEA },
            inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = notes.count { it.status == NoteStatus.DONE },
            threadSummary = insight.summary,
            threadBlocker = insight.blocker,
            threadNextStep = insight.nextStep,
            stage = insight.stage,
            stageReason = insight.stageReason,
            rhythmLine = insight.rhythmLine,
            dominantHorizon = insight.dominantHorizon,
            stageHistory = stageHistory,
            directionAssets = directionAssets,
            wikiAssetSummary = wikiDirection?.assetSummary.orEmpty(),
            wikiGroundingLine = wikiDirection?.groundingLine.orEmpty(),
            wikiSignalPoints = wikiDirection?.signalPoints.orEmpty(),
            wikiHypothesisPoints = wikiDirection?.hypothesisPoints.orEmpty(),
            wikiVerifiedPoints = wikiDirection?.verifiedPoints.orEmpty(),
            wikiValidatedPoints = wikiDirection?.validatedPoints.orEmpty(),
            wikiOpenQuestions = wikiDirection?.openQuestions.orEmpty(),
            wikiContinuityLine = wikiDirection?.continuityLine.orEmpty(),
            wikiTrajectoryLine = wikiDirection?.trajectoryLine.orEmpty(),
            wikiStageHistorySummary = wikiDirection?.stageHistorySummary.orEmpty(),
            wikiUpdatedAt = wikiDirection?.updatedAt ?: 0L,
            weeklyStatsLine = weeklyReview.statsLine,
            weeklyLines = weeklyReview.lines,
            researchOutsideAngle = insight.researchOutsideAngle,
            researchGap = insight.researchGap,
            researchContrarianQuestion = insight.researchContrarianQuestion,
            researchExternalHypothesis = insight.researchExternalHypothesis,
            researchQueries = insight.researchQueries,
            researchSource = insight.researchSource,
            researchEvidence = ResearchEvidenceAnalyzer.summarize(researchNotes),
            focusNote = focusNote,
            focusReason = focusReasonFor(focusNote),
            executionWhyNow = insight.executionWhyNow,
            validationStep = insight.validationStep,
            validationReason = insight.validationReason,
            postValidationAction = insight.postValidationAction,
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
                horizon = note.horizon,
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
        if (current.signature == signature && (current.summary.isNotBlank() || current.researchOutsideAngle.isNotBlank())) {
            return
        }
        _insightState.value = _insightState.value.copy(isLoading = true, signature = signature)
        val execution = threadExecutionPlanner.summarize(threadKey, notes)
        val research = externalResearchPlanner.summarize(threadKey, notes)
        _insightState.value = ThreadInsightState(
            signature = signature,
            summary = execution.summary,
            blocker = execution.blocker,
            nextStep = execution.nextStep,
            stage = execution.stage,
            stageReason = execution.stageReason,
            rhythmLine = execution.rhythmLine,
            dominantHorizon = execution.dominantHorizon,
            executionWhyNow = execution.whyNow,
            validationStep = execution.validationStep,
            validationReason = execution.validationReason,
            postValidationAction = execution.postValidationAction,
            researchOutsideAngle = research.outsideAngle,
            researchGap = research.opportunityGap,
            researchContrarianQuestion = research.contrarianQuestion,
            researchExternalHypothesis = research.externalHypothesis,
            researchQueries = research.queries,
            researchSource = research.source,
            source = execution.source,
            isLoading = false,
        )
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

    private fun buildSignature(notes: List<NoteEntity>): String =
        "${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private fun pickFocusNote(notes: List<NoteEntity>): NoteEntity? =
        notes
            .filter { it.status == NoteStatus.IN_PROGRESS }
            .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
            .firstOrNull()
            ?: notes
                .filter { it.status == NoteStatus.IDEA }
                .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
                .firstOrNull()
            ?: notes.sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt }).firstOrNull()

    private fun focusReasonFor(note: NoteEntity?): String =
        when (note?.status) {
            NoteStatus.IN_PROGRESS -> "这条${note.horizon.label}记录最接近真实推进，可以继续沿着它把方向压实。"
            NoteStatus.IDEA -> "这条${note.horizon.label}记录最新、最值得先压成动作，别让它只停在想法层。"
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

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            threadPreferencesRepository: ThreadPreferencesRepository,
            threadExecutionPlanner: ThreadExecutionPlanner,
            externalResearchPlanner: ExternalResearchPlanner,
            directionWikiCoordinator: DirectionWikiCoordinator,
            threadKey: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThreadViewModel(
                    noteRepository = noteRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                    threadKey = threadKey,
                )
            }
        }
    }

    private data class ThreadWeeklyReview(
        val statsLine: String,
        val lines: List<String>,
    )
}
