package com.mindflow.app.ui.screens.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.action.NextActionState
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.brief.DailyBriefState
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.FusionSuggestionState
import com.mindflow.app.data.connect.DirectionAssetAnalyzer
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.followup.StaleReconnectState
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.review.items
import com.mindflow.app.data.review.statsLine
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FlowUiState(
    val todayCount: Int = 0,
    val continueNote: NoteEntity? = null,
    val nextActionText: String = "",
    val nextActionSource: DailyBriefSource = DailyBriefSource.RULE,
    val staleNote: NoteEntity? = null,
    val staleReason: String = "",
    val staleBridge: String = "",
    val staleNextStep: String = "",
    val staleSource: DailyBriefSource = DailyBriefSource.RULE,
    val explorationPrompts: List<String> = emptyList(),
    val explorationSource: DailyBriefSource = DailyBriefSource.RULE,
    val weeklyReviewItems: List<WeeklyReviewItem> = emptyList(),
    val weeklyReviewSource: DailyBriefSource = DailyBriefSource.RULE,
    val weeklyReviewStatsLine: String = "",
    val followedDirections: List<FollowedDirectionSummary> = emptyList(),
    val themeThreads: List<ThemeThread> = emptyList(),
    val fusionSuggestions: List<String> = emptyList(),
    val fusionSource: DailyBriefSource = DailyBriefSource.RULE,
)

data class FollowedDirectionSummary(
    val thread: ThemeThread,
    val focusNoteId: Long? = null,
    val summary: String = "",
    val blocker: String = "",
    val stage: DirectionStage = DirectionStage.FORMING,
    val stageReason: String = "",
    val rhythmLine: String = "",
    val dominantHorizon: NoteHorizon = NoteHorizon.MEDIUM,
    val whyNow: String = "",
    val nextStep: String = "",
    val validationStep: String = "",
    val validationReason: String = "",
    val postValidationAction: String = "",
    val outsideAngle: String = "",
    val opportunityGap: String = "",
    val contrarianQuestion: String = "",
    val externalHypothesis: String = "",
    val researchQueries: List<String> = emptyList(),
    val assetLabel: String = "",
    val assetSummary: String = "",
    val assetNoteId: Long? = null,
    val wikiGroundingLine: String = "",
    val wikiVerifiedPoint: String = "",
    val wikiValidatedPoint: String = "",
    val wikiHypothesisPoint: String = "",
    val wikiOpenQuestion: String = "",
    val wikiContinuityLine: String = "",
    val wikiTrajectoryLine: String = "",
    val wikiStageHistorySummary: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowViewModel(
    noteRepository: NoteRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    private val dailyBriefPlanner: DailyBriefPlanner,
    private val nextActionPlanner: NextActionPlanner,
    private val weeklyReviewPlanner: WeeklyReviewPlanner,
    private val fusionSuggestionPlanner: FusionSuggestionPlanner,
    private val staleReconnectPlanner: StaleReconnectPlanner,
    private val threadExecutionPlanner: ThreadExecutionPlanner,
    private val externalResearchPlanner: ExternalResearchPlanner,
    private val directionWikiCoordinator: DirectionWikiCoordinator,
) : ViewModel() {
    private data class DirectionState(
        val followedDirections: List<FollowedDirectionSummary> = emptyList(),
        val themeThreads: List<ThemeThread> = emptyList(),
    )

    private data class FlowPrimaryInputs(
        val todayCount: Int,
        val continueNote: NoteEntity?,
        val nextActionText: String,
        val nextActionSource: DailyBriefSource,
        val staleNote: NoteEntity?,
        val staleReason: String,
        val staleBridge: String,
        val staleNextStep: String,
        val staleSource: DailyBriefSource,
        val explorationPrompts: List<String>,
        val explorationSource: DailyBriefSource,
    )

    private val directionState = MutableStateFlow(DirectionState())

    private val primaryInputs = combine(
        noteRepository.observeAllNotes(),
        dailyBriefPlanner.state,
        nextActionPlanner.state,
        staleReconnectPlanner.state,
    ) { allNotes: List<NoteEntity>,
        briefState: DailyBriefState,
        nextActionState: NextActionState,
        reconnectState: StaleReconnectState ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val activeNotes = allNotes.filter { !it.isArchived }
        val continueNote = pickContinueNote(activeNotes)
        val staleNote = pickStaleNote(activeNotes, continueNote?.id)
        val hasReconnectMatch = staleNote != null &&
            reconnectState.noteId == staleNote.id &&
            reconnectState.noteUpdatedAt == staleNote.updatedAt &&
            reconnectState.continueNoteId == continueNote?.id
        val nextActionText = if (
            continueNote != null &&
            nextActionState.noteId == continueNote.id &&
            nextActionState.noteUpdatedAt == continueNote.updatedAt
        ) {
            nextActionState.text
        } else {
            ""
        }
        FlowPrimaryInputs(
            todayCount = activeNotes.count { it.createdAt.toLocalDate(zoneId) == today },
            continueNote = continueNote,
            nextActionText = nextActionText,
            nextActionSource = nextActionState.source,
            staleNote = staleNote,
            staleReason = staleReasonFor(staleNote),
            staleBridge = if (hasReconnectMatch) reconnectState.bridge else staleBridgeFallback(staleNote, continueNote, activeNotes),
            staleNextStep = if (hasReconnectMatch) reconnectState.nextStep else staleNextStepFallback(staleNote),
            staleSource = if (hasReconnectMatch) reconnectState.source else DailyBriefSource.RULE,
            explorationPrompts = briefState.lines,
            explorationSource = briefState.source,
        )
    }

    val uiState: StateFlow<FlowUiState> = combine(
        primaryInputs,
        weeklyReviewPlanner.state,
        fusionSuggestionPlanner.state,
        directionState,
    ) { primary,
        weeklyReviewState: WeeklyReviewState,
        fusionState: FusionSuggestionState,
        directions: DirectionState ->
        FlowUiState(
            todayCount = primary.todayCount,
            continueNote = primary.continueNote,
            nextActionText = primary.nextActionText,
            nextActionSource = primary.nextActionSource,
            staleNote = primary.staleNote,
            staleReason = primary.staleReason,
            staleBridge = primary.staleBridge,
            staleNextStep = primary.staleNextStep,
            staleSource = primary.staleSource,
            explorationPrompts = primary.explorationPrompts,
            explorationSource = primary.explorationSource,
            weeklyReviewItems = weeklyReviewState.items,
            weeklyReviewSource = weeklyReviewState.source,
            weeklyReviewStatsLine = weeklyReviewState.statsLine,
            followedDirections = directions.followedDirections,
            themeThreads = directions.themeThreads,
            fusionSuggestions = fusionState.lines,
            fusionSource = fusionState.source,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlowUiState())

    init {
        viewModelScope.launch {
            combine(
                noteRepository.observeAllNotes(),
                threadPreferencesRepository.settings,
                directionWikiCoordinator.snapshot,
            ) { notes, prefs, wikiSnapshot -> Triple(notes, prefs, wikiSnapshot) }
                .collectLatest { (allNotes, threadPreferences, wikiSnapshot) ->
                    val activeNotes = allNotes.filter { !it.isArchived }
                    val analyzedThreads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes, limit = 6)
                    val followedDirections = threadPreferences.followedThreadKeys
                        .mapNotNull { threadKey ->
                            val threadNotes = NoteConnectionAnalyzer.notesForThread(threadKey, activeNotes)
                            if (threadNotes.isEmpty()) {
                                null
                            } else {
                                val thread = NoteConnectionAnalyzer.threadFromKey(threadKey, activeNotes)
                                val execution = threadExecutionPlanner.summarize(threadKey, threadNotes)
                                val research = externalResearchPlanner.summarize(threadKey, threadNotes)
                                val asset = DirectionAssetAnalyzer.build(threadNotes).firstOrNull()
                                val wikiAsset = wikiSnapshot.directions[threadKey]
                                FollowedDirectionSummary(
                                    thread = thread,
                                    focusNoteId = execution.focusNoteId,
                                    summary = execution.summary,
                                    blocker = execution.blocker,
                                    stage = execution.stage,
                                    stageReason = execution.stageReason,
                                    rhythmLine = execution.rhythmLine,
                                    dominantHorizon = execution.dominantHorizon,
                                    whyNow = execution.whyNow,
                                    nextStep = execution.nextStep,
                                    validationStep = execution.validationStep,
                                    validationReason = execution.validationReason,
                                    postValidationAction = execution.postValidationAction,
                                    outsideAngle = research.outsideAngle,
                                    opportunityGap = research.opportunityGap,
                                    contrarianQuestion = research.contrarianQuestion,
                                    externalHypothesis = research.externalHypothesis,
                                    researchQueries = research.queries,
                                    assetLabel = if (wikiAsset?.assetSummary?.isNotBlank() == true) "Direction Wiki" else asset?.type?.label.orEmpty(),
                                    assetSummary = wikiAsset?.assetSummary?.takeIf { it.isNotBlank() } ?: asset?.summary.orEmpty(),
                                    assetNoteId = asset?.noteId,
                                    wikiGroundingLine = wikiAsset?.groundingLine.orEmpty(),
                                    wikiVerifiedPoint = wikiAsset?.verifiedPoints?.firstOrNull().orEmpty(),
                                    wikiValidatedPoint = wikiAsset?.validatedPoints?.firstOrNull().orEmpty(),
                                    wikiHypothesisPoint = wikiAsset?.hypothesisPoints?.firstOrNull().orEmpty(),
                                    wikiOpenQuestion = wikiAsset?.openQuestions?.firstOrNull().orEmpty(),
                                    wikiContinuityLine = wikiAsset?.continuityLine.orEmpty(),
                                    wikiTrajectoryLine = wikiAsset?.trajectoryLine.orEmpty(),
                                    wikiStageHistorySummary = wikiAsset?.stageHistorySummary.orEmpty(),
                                    source = if (execution.source == DailyBriefSource.AI || research.source == DailyBriefSource.AI) {
                                        DailyBriefSource.AI
                                    } else {
                                        DailyBriefSource.RULE
                                    },
                                )
                            }
                        }
                        .sortedWith(
                            compareByDescending<FollowedDirectionSummary> { it.thread.noteCount }
                                .thenBy { it.thread.title },
                        )
                    directionState.value = DirectionState(
                        followedDirections = followedDirections,
                        themeThreads = analyzedThreads.filterNot { candidate ->
                            followedDirections.any { it.thread.key == candidate.key }
                        },
                    )
                }
        }
        viewModelScope.launch {
            noteRepository.observeAllNotes().collect { notes ->
                val activeNotes = notes.filter { !it.isArchived }
                dailyBriefPlanner.refreshIfNeeded(notes)
                nextActionPlanner.refreshIfNeeded(pickContinueNote(activeNotes))
                weeklyReviewPlanner.refreshIfNeeded(notes)
                fusionSuggestionPlanner.refreshIfNeeded(notes)
                val continueNote = pickContinueNote(activeNotes)
                staleReconnectPlanner.refreshIfNeeded(
                    staleNote = pickStaleNote(activeNotes, continueNote?.id),
                    continueNote = continueNote,
                    notes = activeNotes,
                )
            }
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            threadPreferencesRepository: ThreadPreferencesRepository,
            dailyBriefPlanner: DailyBriefPlanner,
            nextActionPlanner: NextActionPlanner,
            weeklyReviewPlanner: WeeklyReviewPlanner,
            fusionSuggestionPlanner: FusionSuggestionPlanner,
            staleReconnectPlanner: StaleReconnectPlanner,
            threadExecutionPlanner: ThreadExecutionPlanner,
            externalResearchPlanner: ExternalResearchPlanner,
            directionWikiCoordinator: DirectionWikiCoordinator,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FlowViewModel(
                    noteRepository = noteRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    dailyBriefPlanner = dailyBriefPlanner,
                    nextActionPlanner = nextActionPlanner,
                    weeklyReviewPlanner = weeklyReviewPlanner,
                    fusionSuggestionPlanner = fusionSuggestionPlanner,
                    staleReconnectPlanner = staleReconnectPlanner,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                )
            }
        }
    }
}

private fun pickContinueNote(notes: List<NoteEntity>): NoteEntity? =
    notes
        .filter { it.status == NoteStatus.IN_PROGRESS }
        .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
        .firstOrNull()
        ?: notes
            .filter { it.status == NoteStatus.IDEA }
            .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
            .firstOrNull()

private fun pickStaleNote(
    notes: List<NoteEntity>,
    excludeNoteId: Long?,
): NoteEntity? {
    val threshold = System.currentTimeMillis() - 12L * 24 * 60 * 60 * 1_000
    return notes
        .filter { it.id != excludeNoteId }
        .filter { it.status != NoteStatus.DONE }
        .filter { it.updatedAt < threshold }
        .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenBy { it.updatedAt })
        .firstOrNull()
}

private fun staleReasonFor(note: NoteEntity?): String =
    when (note?.status) {
        NoteStatus.IN_PROGRESS -> "这条${note.horizon.label}方向已经晾了一阵，现在最适合重新接上一小步。"
        NoteStatus.IDEA -> "这条${note.horizon.label}想法沉下去有点久了，值得重新补一条更具体的记录。"
        else -> ""
    }

private fun staleBridgeFallback(
    note: NoteEntity?,
    continueNote: NoteEntity?,
    notes: List<NoteEntity>,
): String {
    note ?: return ""
    val sharedTag = continueNote
        ?.tags
        ?.firstOrNull { candidate -> candidate.isNotBlank() && candidate in note.tags }
    if (sharedTag != null) {
        return "它和你正在推进的内容都围绕「$sharedTag」，适合顺手接回同一条主线。"
    }

    val noteFolder = note.folderKey
    val continueFolder = continueNote?.folderKey
    if (!noteFolder.isNullOrBlank() && noteFolder == continueFolder) {
        val folderName = note.folderName()
        return if (folderName != null) {
            "它和你最近在推的内容都在「$folderName」里，说明这不是一次性念头。"
        } else {
            "它和你最近推进的内容在同一类问题里，值得重新接上。"
        }
    }

    val repeatedTag = note.tags
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                notes.count { active -> !active.isArchived && candidate in active.tags } >= 2
        }
    if (repeatedTag != null) {
        return "它属于「#$repeatedTag」这条持续出现的方向，可以重新补一笔，把线索接回来。"
    }

    return note.folderName()?.let { folderName ->
        "它还落在「$folderName」这类问题里，说明这个方向仍然值得保留。"
    }.orEmpty()
}

private fun staleNextStepFallback(note: NoteEntity?): String =
    when {
        note == null -> ""
        note.horizon == NoteHorizon.SHORT -> "先把这周内最小的一步落下来，再决定后面要不要放大。"
        note.horizon == NoteHorizon.LONG -> "先把长期目标压成一个月内要验证的最小问题。"
        note.status == NoteStatus.IN_PROGRESS -> "先补一句最新进展，再把它往前拱一小步。"
        note.folderName() == "工作" -> "先把它压成一个最想验证的问题，再补一条更具体的记录。"
        note.folderName() == "项目" -> "先写下一个最小可验证版本，别直接摊开完整方案。"
        note.folderName() == "健康" -> "先把它变成今天就能执行的一次小动作，再看后续反馈。"
        else -> "先补一条更具体的记录，把这个想法重新压回到可推进的状态。"
    }

private fun whyNowForThread(
    thread: ThemeThread,
    notes: List<NoteEntity>,
    focusNote: NoteEntity?,
): String {
    focusNote ?: return "这条方向已经形成了连续记录，值得先接着整理成一条主线。"
    val repeatedTag = notes
        .flatMap { it.tags.distinct() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return when (focusNote.status) {
        NoteStatus.IN_PROGRESS -> "这条方向已经在推进里，顺着「${focusNote.topic.ifBlank { thread.title }}」继续最省力。"
        NoteStatus.IDEA -> when {
            repeatedTag != null -> "它和你反复记录的「$repeatedTag」是同一条线，现在最适合压成动作。"
            notes.size >= 3 -> "这条方向已经连续出现 ${notes.size} 次，再拖下去只会重新散开。"
            else -> "这条方向刚冒出新线索，趁上下文还热，先把它压成一小步。"
        }
        NoteStatus.DONE -> "这里已经有做成的结果，可以沿着结果外扩，而不是从头再起一条线。"
    }
}

private fun nextStepForThread(
    thread: ThemeThread,
    focusNote: NoteEntity?,
): String {
    focusNote ?: return "先补一条更具体的记录，把这个方向重新压回到可推进状态。"
    return when (focusNote.status) {
        NoteStatus.IN_PROGRESS -> "先补一句最新进展，再写一个今天能验证的小动作。"
        NoteStatus.IDEA -> when (focusNote.folderName()) {
            "工作" -> "先把它压成一个待验证的问题，再补一条更具体的工作记录。"
            "项目" -> "先写出最小可验证版本，再看要不要继续扩。"
            "健康" -> "先把它改成今天能执行的一次小实验，再观察反馈。"
            else -> "先把「${focusNote.topic.ifBlank { thread.title }}」压成一个今天能动手的小动作。"
        }
        NoteStatus.DONE -> "先补一条结果延展记录，说明这个结果还能往哪一步继续放大。"
    }
}

private fun NoteEntity.folderName(): String? =
    com.mindflow.app.data.model.MindFolderCatalog.fromKey(folderKey)?.name

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
