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
import com.mindflow.app.data.flow.FlowKnowledgeCompressionPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionState
import com.mindflow.app.data.followup.StaleReconnectState
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
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
    val mainlineCandidate: MainlineBetCandidate? = null,
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
    val knowledgeCompression: FlowKnowledgeCompressionState = FlowKnowledgeCompressionState(),
    val settledFeedback: FlowCardFeedback? = null,
    val gapFeedback: FlowCardFeedback? = null,
)

data class MainlineBetCandidate(
    val key: String,
    val title: String,
    val anchorLabel: String = title,
    val stageLabel: String = "",
    val horizonLabel: String = "",
    val summary: String = "",
    val whyNow: String = "",
    val nextStep: String = "",
    val bucketKey: String = key,
    val threadKey: String? = null,
    val focusNoteId: Long? = null,
    val noteId: Long? = null,
)

private data class MainlineCandidateSeed(
    val candidate: MainlineBetCandidate,
    val score: Int,
)

enum class FlowCardFeedback {
    HELPFUL,
    FLAT,
}

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
    val lastProgressLine: String = "",
    val nextStep: String = "",
    val nextCheckInLine: String = "",
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
    val wikiConclusionLine: String = "",
    val wikiNextShiftLine: String = "",
    val wikiGroundingLine: String = "",
    val wikiTrustLine: String = "",
    val wikiVerifiedPoint: String = "",
    val wikiValidatedPoint: String = "",
    val wikiHypothesisPoint: String = "",
    val wikiOpenQuestion: String = "",
    val wikiContinuityLine: String = "",
    val wikiTrajectoryLine: String = "",
    val wikiStageHistorySummary: String = "",
    val wikiSnapshotStageLine: String = "",
    val wikiSnapshotCadenceLine: String = "",
    val wikiKnowledgeObjectLine: String = "",
    val wikiHealthLine: String = "",
    val wikiMaintenanceLine: String = "",
    val wikiMaintenanceTargetLine: String = "",
    val wikiMaintenanceSourceLine: String = "",
    val wikiMaintenanceDimensionLine: String = "",
    val wikiMaintenanceFocusLine: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowViewModel(
    noteRepository: NoteRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    private val dailyBriefPlanner: DailyBriefPlanner,
    private val nextActionPlanner: NextActionPlanner,
    private val weeklyReviewPlanner: WeeklyReviewPlanner,
    private val fusionSuggestionPlanner: FusionSuggestionPlanner,
    private val flowKnowledgeCompressionPlanner: FlowKnowledgeCompressionPlanner,
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
        val activeNotes: List<NoteEntity>,
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

    private data class FlowCompressionInput(
        val signature: String,
        val mainlineKey: String,
        val settledKey: String,
        val gapKey: String,
        val selectedMainlineCandidate: MainlineBetCandidate?,
        val mainlineContextSummary: String,
        val settledContextSummary: String,
        val gapContextSummary: String,
        val fallback: FlowKnowledgeCompressionState,
    )

    private val directionState = MutableStateFlow(DirectionState())
    private val knowledgeCompressionState = MutableStateFlow(FlowKnowledgeCompressionState())
    private val mainlineCandidateState = MutableStateFlow<MainlineBetCandidate?>(null)
    private val mainlineRefreshNonce = MutableStateFlow(0)
    private val gapRefreshNonce = MutableStateFlow(0)
    private val settledFeedbackState = MutableStateFlow<FlowCardFeedback?>(null)
    private val gapFeedbackState = MutableStateFlow<FlowCardFeedback?>(null)

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
            activeNotes = activeNotes,
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
        knowledgeCompressionState,
        mainlineCandidateState,
        settledFeedbackState,
        gapFeedbackState,
    ) { values ->
        val primary = values[0] as FlowPrimaryInputs
        val weeklyReviewState = values[1] as WeeklyReviewState
        val fusionState = values[2] as FusionSuggestionState
        val directions = values[3] as DirectionState
        val compression = values[4] as FlowKnowledgeCompressionState
        val mainlineCandidate = values[5] as MainlineBetCandidate?
        val settledFeedback = values[6] as FlowCardFeedback?
        val gapFeedback = values[7] as FlowCardFeedback?
        FlowUiState(
            todayCount = primary.todayCount,
            continueNote = primary.continueNote,
            mainlineCandidate = mainlineCandidate,
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
            knowledgeCompression = compression,
            settledFeedback = settledFeedback,
            gapFeedback = gapFeedback,
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
                                    lastProgressLine = execution.lastProgressLine,
                                    nextStep = execution.nextStep,
                                    nextCheckInLine = execution.nextCheckInLine,
                                    validationStep = execution.validationStep,
                                    validationReason = execution.validationReason,
                                    postValidationAction = execution.postValidationAction,
                                    outsideAngle = research.outsideAngle,
                                    opportunityGap = research.opportunityGap,
                                    contrarianQuestion = research.contrarianQuestion,
                                    externalHypothesis = research.externalHypothesis,
                                    researchQueries = research.queries,
                                    assetLabel = if (wikiAsset?.assetSummary?.isNotBlank() == true) "知识层" else asset?.type?.label.orEmpty(),
                                    assetSummary = wikiAsset?.assetSummary?.takeIf { it.isNotBlank() } ?: asset?.summary.orEmpty(),
                                    assetNoteId = asset?.noteId,
                                    wikiConclusionLine = wikiAsset?.conclusionLine.orEmpty(),
                                    wikiNextShiftLine = wikiAsset?.nextShiftLine.orEmpty(),
                                    wikiGroundingLine = wikiAsset?.groundingLine.orEmpty(),
                                    wikiTrustLine = wikiAsset?.trustLine.orEmpty(),
                                    wikiVerifiedPoint = wikiAsset?.verifiedPoints?.firstOrNull().orEmpty(),
                                    wikiValidatedPoint = wikiAsset?.validatedPoints?.firstOrNull().orEmpty(),
                                    wikiHypothesisPoint = wikiAsset?.hypothesisPoints?.firstOrNull().orEmpty(),
                                    wikiOpenQuestion = wikiAsset?.openQuestions?.firstOrNull().orEmpty(),
                                    wikiContinuityLine = wikiAsset?.continuityLine.orEmpty(),
                                    wikiTrajectoryLine = wikiAsset?.trajectoryLine.orEmpty(),
                                    wikiStageHistorySummary = wikiAsset?.stageHistorySummary.orEmpty(),
                                    wikiSnapshotStageLine = wikiAsset?.snapshotStageLine.orEmpty(),
                                    wikiSnapshotCadenceLine = wikiAsset?.snapshotCadenceLine.orEmpty(),
                                    wikiKnowledgeObjectLine = wikiAsset?.knowledgeObjectLine.orEmpty(),
                                    wikiHealthLine = wikiAsset?.healthLine.orEmpty(),
                                    wikiMaintenanceLine = wikiAsset?.maintenanceLine.orEmpty(),
                                    wikiMaintenanceTargetLine = wikiAsset?.maintenanceTargetLine.orEmpty(),
                                    wikiMaintenanceSourceLine = wikiAsset?.maintenanceSourceLine.orEmpty(),
                                    wikiMaintenanceDimensionLine = wikiAsset?.maintenanceDimensionLine.orEmpty(),
                                    wikiMaintenanceFocusLine = wikiAsset?.maintenanceFocusLine.orEmpty(),
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
        viewModelScope.launch {
            combine(
                primaryInputs,
                weeklyReviewPlanner.state,
                fusionSuggestionPlanner.state,
                directionState,
                mainlineRefreshNonce,
                gapRefreshNonce,
                settledFeedbackState,
                gapFeedbackState,
            ) { values ->
                val primary = values[0] as FlowPrimaryInputs
                val weekly = values[1] as WeeklyReviewState
                val fusion = values[2] as FusionSuggestionState
                val directions = values[3] as DirectionState
                val mainlineNonce = values[4] as Int
                val gapNonce = values[5] as Int
                val settledFeedback = values[6] as FlowCardFeedback?
                val gapFeedback = values[7] as FlowCardFeedback?
                buildCompressionInput(
                    primary = primary,
                    weeklyReviewState = weekly,
                    fusionState = fusion,
                    directions = directions,
                    mainlineNonce = mainlineNonce,
                    gapNonce = gapNonce,
                    settledFeedback = settledFeedback,
                    gapFeedback = gapFeedback,
                )
            }.collectLatest { input ->
                mainlineCandidateState.value = input.selectedMainlineCandidate
                val previous = knowledgeCompressionState.value
                val next = flowKnowledgeCompressionPlanner.summarize(
                    mainlineKey = input.mainlineKey,
                    settledKey = input.settledKey,
                    gapKey = input.gapKey,
                    mainlineContextSummary = input.mainlineContextSummary,
                    settledContextSummary = input.settledContextSummary,
                    gapContextSummary = input.gapContextSummary,
                    fallback = input.fallback,
                )
                knowledgeCompressionState.value = next
                if (previous.settledLine != next.settledLine) {
                    settledFeedbackState.value = null
                }
                if (previous.gapLine != next.gapLine) {
                    gapFeedbackState.value = null
                }
            }
        }
    }

    private fun buildCompressionInput(
        primary: FlowPrimaryInputs,
        weeklyReviewState: WeeklyReviewState,
        fusionState: FusionSuggestionState,
        directions: DirectionState,
        mainlineNonce: Int,
        gapNonce: Int,
        settledFeedback: FlowCardFeedback?,
        gapFeedback: FlowCardFeedback?,
    ): FlowCompressionInput {
        val primaryDirection = directions.followedDirections.firstOrNull()
        val mainlineCandidates = buildMainlineCandidates(
            primary = primary,
            directions = directions.followedDirections,
        )
        val selectedMainlineCandidate = mainlineCandidates
            .getOrNull(mainlineNonce.floorMod(mainlineCandidates.size))
        val settledDirection = directions.followedDirections.firstOrNull {
            it.wikiValidatedPoint.isNotBlank() ||
                it.wikiVerifiedPoint.isNotBlank() ||
                it.wikiConclusionLine.isNotBlank() ||
                it.assetSummary.isNotBlank()
        } ?: primaryDirection
        val breakthroughDirection = directions.followedDirections.firstOrNull {
            it.wikiOpenQuestion.isNotBlank() ||
                it.contrarianQuestion.isNotBlank() ||
                it.externalHypothesis.isNotBlank() ||
                it.opportunityGap.isNotBlank() ||
                it.blocker.isNotBlank()
        } ?: primaryDirection

        val fallback = FlowKnowledgeCompressionState(
            mainline = selectedMainlineCandidate?.summary
                ?.takeIf { it.isNotBlank() }
                ?: primary.nextActionText.takeIf { it.isNotBlank() }
                ?: primary.staleNextStep.takeIf { it.isNotBlank() }
                ?: "先把今天最新进来的材料压成一个当前综合判断。",
            whyNow = selectedMainlineCandidate?.whyNow
                ?.takeIf { it.isNotBlank() }
                ?: primary.staleBridge.takeIf { it.isNotBlank() }
                ?: weeklyReviewState.items.firstOrNull()?.text.orEmpty(),
            mainlineSource = DailyBriefSource.RULE,
            settledLine = settledDirection?.wikiValidatedPoint
                ?.takeIf { it.isNotBlank() }
                ?: settledDirection?.wikiVerifiedPoint?.takeIf { it.isNotBlank() }
                ?: settledDirection?.wikiConclusionLine?.takeIf { it.isNotBlank() }
                ?: settledDirection?.assetSummary?.takeIf { it.isNotBlank() }
                ?: weeklyReviewState.items.firstOrNull { it.label == "主线" }?.text.orEmpty(),
            settledSupport = settledDirection?.wikiGroundingLine
                ?.takeIf { it.isNotBlank() }
                ?: settledDirection?.wikiTrustLine?.takeIf { it.isNotBlank() }
                ?: settledDirection?.wikiKnowledgeObjectLine?.takeIf { it.isNotBlank() }
                ?: weeklyReviewState.statsLine,
            settledSource = DailyBriefSource.RULE,
            gapLine = breakthroughDirection?.contrarianQuestion
                ?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.externalHypothesis?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.opportunityGap?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.wikiOpenQuestion?.takeIf { it.isNotBlank() }
                ?: fusionState.lines.firstOrNull().orEmpty(),
            gapSupport = breakthroughDirection?.postValidationAction
                ?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.validationReason?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.outsideAngle?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.wikiMaintenanceFocusLine?.takeIf { it.isNotBlank() }
                ?: fusionState.lines.getOrNull(1).orEmpty(),
            gapSource = DailyBriefSource.RULE,
        )

        val signature = buildString {
            append(primary.continueNote?.id ?: -1L)
            append(':')
            append(primary.continueNote?.updatedAt ?: 0L)
            append(':')
            append(primary.staleNote?.id ?: -1L)
            append(':')
            append(primary.staleNote?.updatedAt ?: 0L)
            append(':')
            append(weeklyReviewState.weekKey)
            append(':')
            append(weeklyReviewState.generatedAt)
            append(':')
            append(fusionState.generatedAt)
            append(':')
            append(mainlineCandidates.joinToString("|") {
                listOf(
                    it.key,
                    it.title,
                    it.stageLabel,
                    it.horizonLabel,
                    it.summary,
                    it.whyNow,
                    it.nextStep,
                ).joinToString("~")
            })
            append(':')
            append(directions.followedDirections.joinToString("|") {
                listOf(
                    it.thread.key,
                    it.thread.noteCount.toString(),
                    it.wikiConclusionLine,
                    it.wikiValidatedPoint,
                    it.wikiVerifiedPoint,
                    it.wikiOpenQuestion,
                    it.summary,
                    it.whyNow,
                    it.nextStep,
                ).joinToString("~")
            })
        }
        val todayKey = LocalDate.now().toString()
        val mainlineKey = "$todayKey:mainline:$mainlineNonce"
        val settledKey = "$signature:settled"
        val gapKey = "$signature:gap:$gapNonce"

        val mainlineContextSummary = buildString {
            appendLine("请像本地知识维护员一样，把今天新进来的材料和已有积累压成一个当前综合判断。不要列候选，不要泛泛鼓励。")
            appendLine("这是 Flow 第一张主卡，需要像 llm-wiki 已经维护出来的当前综合判断，而不是即时摘要。")
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前最值得看的对象：${candidate.title}")
                appendLine("主要来自：${candidate.anchorLabel}")
                candidate.stageLabel.takeIf { it.isNotBlank() }?.let { appendLine("阶段：$it") }
                candidate.horizonLabel.takeIf { it.isNotBlank() }?.let { appendLine("时间尺度：$it") }
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("已知判断：$it") }
                candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("已知为什么现在：$it") }
                candidate.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("已知最小动作：$it") }
            }
            if (mainlineNonce > 0 && mainlineCandidates.size > 1) {
                appendLine("用户刚点了“再压一次”，这次必须切到不同项目、文件夹或方向的真实候选，而不是改写同一主题。")
            } else if (mainlineNonce > 0) {
                appendLine("用户刚点了“再压一次”，候选不多时请至少换一个角度，不要只是同义改写。")
            }
            directions.followedDirections.firstOrNull { it.thread.key == selectedMainlineCandidate?.threadKey }?.let { direction ->
                direction.lastProgressLine.takeIf { it.isNotBlank() }?.let { appendLine("最近推进：$it") }
            }
            primary.nextActionText.takeIf { it.isNotBlank() }?.let { appendLine("系统下一步：$it") }
            primary.staleBridge.takeIf { it.isNotBlank() }?.let { appendLine("重连理由：$it") }
            primary.staleNextStep.takeIf { it.isNotBlank() }?.let { appendLine("重连动作：$it") }
            weeklyReviewState.items.firstOrNull { it.label == "推进" }
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine("本周推进：$it") }
            appendLine("目标：输出一个当前综合判断，而不是行动号召。")
        }

        val settledContextSummary = buildString {
            appendLine("请像 wiki 维护员一样，只挑一个最近真正被吸收进知识层的结果。不要写计划，不要写鼓励。")
            appendLine("不要把当前综合判断换一种说法重新写成最近吸收。最近吸收必须更像已被写入知识层的结果、结论或方法。")
            when (settledFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种会改变优先级、带清楚可信基础的判断更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：避免保守的趋势总结、空泛的进步描述和不影响决策的判断。")
                null -> Unit
            }
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前综合判断对象：${candidate.title}")
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("综合判断主句：$it") }
                candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("综合判断原因：$it") }
            }
            settledDirection?.let { direction ->
                appendLine("方向：${direction.thread.title}")
                appendLine("吸收候选：${direction.wikiValidatedPoint.ifBlank { direction.wikiVerifiedPoint.ifBlank { direction.wikiConclusionLine.ifBlank { direction.assetSummary } } }}")
                direction.wikiValidatedPoint.takeIf { it.isNotBlank() }?.let { appendLine("已验证：$it") }
                direction.wikiVerifiedPoint.takeIf { it.isNotBlank() }?.let { appendLine("已查证：$it") }
                direction.wikiHypothesisPoint.takeIf { it.isNotBlank() }?.let { appendLine("待验证：$it") }
                direction.wikiGroundingLine.takeIf { it.isNotBlank() }?.let { appendLine("证据基础：$it") }
                direction.wikiTrustLine.takeIf { it.isNotBlank() }?.let { appendLine("可信边界：$it") }
                direction.wikiKnowledgeObjectLine.takeIf { it.isNotBlank() }?.let { appendLine("知识对象：$it") }
                direction.wikiSnapshotStageLine.takeIf { it.isNotBlank() }?.let { appendLine("阶段位置：$it") }
                direction.wikiContinuityLine.takeIf { it.isNotBlank() }?.let { appendLine("连续性：$it") }
                direction.wikiTrajectoryLine.takeIf { it.isNotBlank() }?.let { appendLine("走势：$it") }
                direction.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("它会影响的下一步：$it") }
            }
            val corroboratingDirections = directions.followedDirections
                .filter { it.thread.key != settledDirection?.thread?.key }
                .mapNotNull { summary ->
                    summary.wikiValidatedPoint
                        .ifBlank { summary.wikiVerifiedPoint }
                        .ifBlank { summary.wikiConclusionLine }
                        .takeIf { it.isNotBlank() }
                        ?.let { "${summary.thread.title}：$it" }
                }
                .take(2)
            if (corroboratingDirections.isNotEmpty()) {
                appendLine("旁证方向：")
                corroboratingDirections.forEach { appendLine(it) }
            }
            val repeatedHorizons = directions.followedDirections
                .groupingBy { it.dominantHorizon.label }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(2)
                .joinToString("，") { "${it.key}${it.value}条" }
            if (repeatedHorizons.isNotBlank()) {
                appendLine("你最近长期在推：$repeatedHorizons")
            }
            appendLine("目标：输出一条最近刚被写进知识层的结果，而不是趋势总结。")
        }

        val gapContextSummary = buildString {
            appendLine("请像 wiki maintainer 一样，只找一个当前最该厘清的张力。不要平均分配，不要列清单。")
            appendLine("这张卡的职责不是制造灵感，而是指出知识层里最该补的一处张力，以及下一次该摄入什么材料。")
            appendLine("不要重复当前综合判断，也不要把最近吸收换个词说一遍。")
            when (gapFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种真正指出张力和下一次摄入对象的结果更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：不要给维护口号、不要给显而易见的缺口，要明确指出哪一处知识最薄。")
                null -> Unit
            }
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前综合判断：${candidate.title}")
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("综合判断主句：$it") }
            }
            settledDirection?.let { direction ->
                direction.wikiValidatedPoint
                    .ifBlank { direction.wikiVerifiedPoint }
                    .ifBlank { direction.wikiConclusionLine }
                    .takeIf { it.isNotBlank() }
                    ?.let { appendLine("最近吸收：$it") }
            }
            breakthroughDirection?.let { direction ->
                appendLine("方向：${direction.thread.title}")
                appendLine("张力候选：${direction.wikiOpenQuestion.ifBlank { direction.wikiMaintenanceLine.ifBlank { direction.blocker } }}")
                direction.wikiHealthLine.takeIf { it.isNotBlank() }?.let { appendLine("健康状态：$it") }
                direction.wikiMaintenanceFocusLine.takeIf { it.isNotBlank() }?.let { appendLine("优先对象：$it") }
                direction.wikiMaintenanceTargetLine.takeIf { it.isNotBlank() }?.let { appendLine("先维护：$it") }
                direction.wikiMaintenanceSourceLine.takeIf { it.isNotBlank() }?.let { appendLine("先补来源：$it") }
                direction.wikiMaintenanceDimensionLine.takeIf { it.isNotBlank() }?.let { appendLine("最薄弱：$it") }
                direction.outsideAngle.takeIf { it.isNotBlank() }?.let { appendLine("外部角度：$it") }
                direction.opportunityGap.takeIf { it.isNotBlank() }?.let { appendLine("机会缺口：$it") }
                direction.contrarianQuestion.takeIf { it.isNotBlank() }?.let { appendLine("反常识问题：$it") }
                direction.externalHypothesis.takeIf { it.isNotBlank() }?.let { appendLine("外部假设：$it") }
                direction.wikiHypothesisPoint.takeIf { it.isNotBlank() }?.let { appendLine("待验证：$it") }
                direction.postValidationAction.takeIf { it.isNotBlank() }?.let { appendLine("如果成立下一步：$it") }
            }
            val crossDirectionAssets = directions.followedDirections
                .filter { it.thread.key != breakthroughDirection?.thread?.key }
                .filter { it.thread.key != selectedMainlineCandidate?.threadKey }
                .mapNotNull { summary ->
                    val stablePoint = summary.wikiValidatedPoint
                        .ifBlank { summary.wikiVerifiedPoint }
                        .ifBlank { summary.wikiConclusionLine }
                        .ifBlank { summary.assetSummary }
                    val openPoint = summary.wikiOpenQuestion
                        .ifBlank { summary.contrarianQuestion }
                        .ifBlank { summary.opportunityGap }
                    val chosen = stablePoint.ifBlank { openPoint }
                    chosen.takeIf { it.isNotBlank() }?.let { "${summary.thread.title}：$it" }
                }
                .distinct()
                .take(3)
            if (crossDirectionAssets.isNotEmpty()) {
                appendLine("其他方向可借来的积累：")
                crossDirectionAssets.forEach { appendLine(it) }
            }
            if (primary.explorationPrompts.isNotEmpty()) {
                appendLine("待吸收提示：")
                primary.explorationPrompts.take(2).forEach { appendLine(it) }
            }
            if (fusionState.lines.isNotEmpty()) {
                appendLine("潜在张力：")
                fusionState.lines.take(2).forEach { appendLine(it) }
            }
            appendLine("目标：输出当前最该厘清的张力，以及下一次摄入什么材料。")
        }

        return FlowCompressionInput(
            signature = signature,
            mainlineKey = mainlineKey,
            settledKey = settledKey,
            gapKey = gapKey,
            selectedMainlineCandidate = selectedMainlineCandidate,
            mainlineContextSummary = mainlineContextSummary,
            settledContextSummary = settledContextSummary,
            gapContextSummary = gapContextSummary,
            fallback = fallback,
        )
    }

    fun refreshMainline() {
        mainlineRefreshNonce.value = mainlineRefreshNonce.value + 1
    }

    fun refreshGap() {
        gapRefreshNonce.value = gapRefreshNonce.value + 1
    }

    fun markSettledFeedback(helpful: Boolean) {
        settledFeedbackState.value = if (helpful) FlowCardFeedback.HELPFUL else FlowCardFeedback.FLAT
    }

    fun markGapFeedback(helpful: Boolean) {
        gapFeedbackState.value = if (helpful) FlowCardFeedback.HELPFUL else FlowCardFeedback.FLAT
    }

    private fun buildMainlineCandidates(
        primary: FlowPrimaryInputs,
        directions: List<FollowedDirectionSummary>,
    ): List<MainlineBetCandidate> {
        val noteById = primary.activeNotes.associateBy { it.id }
        val candidates = mutableListOf<MainlineCandidateSeed>()
        directions.forEach { direction ->
            val threadFocus = direction.focusNoteId?.let(noteById::get)
            val threadNotes = NoteConnectionAnalyzer.notesForThread(direction.thread.key, primary.activeNotes)
            val threadFolderKey = dominantFolderKey(threadNotes)
            val threadFolderName = threadFolderKey?.let { key -> MindFolderCatalog.fromKey(key)?.name }
            candidates += MainlineCandidateSeed(
                candidate = MainlineBetCandidate(
                    key = "thread:${direction.thread.key}",
                    title = direction.thread.title,
                    anchorLabel = threadFolderName?.let { "$it · ${direction.thread.title}" } ?: direction.thread.title,
                    stageLabel = direction.stage.label,
                    horizonLabel = direction.dominantHorizon.label,
                    summary = direction.summary,
                    whyNow = direction.whyNow,
                    nextStep = direction.nextStep,
                    bucketKey = threadFolderKey?.let { "folder:$it" } ?: "direction:${direction.thread.key}",
                    threadKey = direction.thread.key,
                    focusNoteId = direction.focusNoteId,
                    noteId = threadFocus?.id ?: direction.focusNoteId,
                ),
                score = 100 + direction.thread.noteCount,
            )
        }
        primary.activeNotes
            .filter { it.status != NoteStatus.DONE }
            .groupBy { MindFolderCatalog.normalizedKey(it.folderKey) ?: "uncategorized" }
            .forEach { (folderKey, notes) ->
                val note = notes.sortedWith(
                    compareByDescending<NoteEntity> { it.status == NoteStatus.IN_PROGRESS }
                        .thenByDescending { it.horizon.priority }
                        .thenByDescending { it.updatedAt },
                ).firstOrNull() ?: return@forEach
                val folderName = MindFolderCatalog.fromKey(folderKey)?.name ?: "其他"
                val anchorLabel = "$folderName · ${note.topic.ifBlank { "一条记录" }}"
                val seed = MainlineCandidateSeed(
                    candidate = MainlineBetCandidate(
                        key = "folder-note:${folderKey}:${note.id}",
                        title = note.topic.ifBlank { "$folderName 里最值得推进的一条" },
                        anchorLabel = anchorLabel,
                        stageLabel = note.status.label,
                        horizonLabel = note.horizon.label,
                        summary = buildNoteCandidateSummary(note),
                        whyNow = buildNoteCandidateWhyNow(note),
                        nextStep = buildNoteCandidateNextStep(note),
                        bucketKey = "folder:$folderKey",
                        noteId = note.id,
                    ),
                    score = if (note.status == NoteStatus.IN_PROGRESS) 90 else 72,
                )
                candidates += seed
            }
        primary.continueNote?.let { note ->
            val exists = candidates.any { seed -> seed.candidate.noteId == note.id }
            if (!exists) {
                candidates += MainlineCandidateSeed(
                    candidate = MainlineBetCandidate(
                        key = "note:${note.id}",
                        title = note.topic.ifBlank { "正在推进的记录" },
                        anchorLabel = buildAnchorLabel(note),
                        stageLabel = note.status.label,
                        horizonLabel = note.horizon.label,
                        summary = primary.nextActionText.ifBlank { buildNoteCandidateSummary(note) },
                        whyNow = buildNoteCandidateWhyNow(note),
                        nextStep = primary.nextActionText.ifBlank { buildNoteCandidateNextStep(note) },
                        bucketKey = "folder:${MindFolderCatalog.normalizedKey(note.folderKey) ?: "uncategorized"}",
                        noteId = note.id,
                    ),
                    score = 96,
                )
            }
        }
        primary.staleNote?.let { note ->
            val exists = candidates.any { seed -> seed.candidate.noteId == note.id }
            if (!exists) {
                candidates += MainlineCandidateSeed(
                    candidate = MainlineBetCandidate(
                        key = "stale:${note.id}",
                        title = note.topic.ifBlank { "值得重新接上的记录" },
                        anchorLabel = buildAnchorLabel(note),
                        stageLabel = note.status.label,
                        horizonLabel = note.horizon.label,
                        summary = primary.staleBridge.ifBlank { buildNoteCandidateSummary(note) },
                        whyNow = primary.staleBridge.ifBlank { buildNoteCandidateWhyNow(note) },
                        nextStep = primary.staleNextStep.ifBlank { buildNoteCandidateNextStep(note) },
                        bucketKey = "folder:${MindFolderCatalog.normalizedKey(note.folderKey) ?: "uncategorized"}",
                        noteId = note.id,
                    ),
                    score = 84,
                )
            }
        }
        return diversifyCandidates(candidates)
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            threadPreferencesRepository: ThreadPreferencesRepository,
            dailyBriefPlanner: DailyBriefPlanner,
            nextActionPlanner: NextActionPlanner,
            weeklyReviewPlanner: WeeklyReviewPlanner,
            fusionSuggestionPlanner: FusionSuggestionPlanner,
            flowKnowledgeCompressionPlanner: FlowKnowledgeCompressionPlanner,
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
                    flowKnowledgeCompressionPlanner = flowKnowledgeCompressionPlanner,
                    staleReconnectPlanner = staleReconnectPlanner,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                )
            }
        }
    }
}

private fun Int.floorMod(size: Int): Int =
    if (size <= 0) 0 else ((this % size) + size) % size

private fun diversifyCandidates(
    candidates: List<MainlineCandidateSeed>,
): List<MainlineBetCandidate> {
    val remaining = candidates
        .sortedByDescending { it.score }
        .toMutableList()
    val ordered = mutableListOf<MainlineBetCandidate>()
    var lastBucket: String? = null
    while (remaining.isNotEmpty()) {
        val nextIndex = remaining.indexOfFirst { it.candidate.bucketKey != lastBucket }
            .takeIf { it >= 0 } ?: 0
        val next = remaining.removeAt(nextIndex).candidate
        ordered += next
        lastBucket = next.bucketKey
    }
    return ordered.distinctBy { it.key }
}

private fun dominantFolderKey(notes: List<NoteEntity>): String? =
    notes
        .mapNotNull { note -> MindFolderCatalog.normalizedKey(note.folderKey) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

private fun buildAnchorLabel(note: NoteEntity): String {
    val folderName = note.folderName()
    return if (folderName != null) "$folderName · ${note.topic.ifBlank { "一条记录" }}" else note.topic.ifBlank { "一条记录" }
}

private fun buildNoteCandidateSummary(note: NoteEntity): String =
    when (note.status) {
        NoteStatus.IN_PROGRESS -> "这条线已经在推进里，继续推它最容易长出真实结果。"
        NoteStatus.IDEA -> when (note.horizon) {
            NoteHorizon.SHORT -> "这个短期想法如果一周内不压成动作，很容易重新散掉。"
            NoteHorizon.MEDIUM -> "这条中期想法已经有足够材料，适合现在压成一个明确推进点。"
            NoteHorizon.LONG -> "这条长期线已经露出轮廓，值得先把它推成一个更稳的阶段目标。"
        }
        NoteStatus.DONE -> "这条线已经有结果，可以沿结果继续放大，而不是从头再想。"
    }

private fun buildNoteCandidateWhyNow(note: NoteEntity): String =
    when {
        note.status == NoteStatus.IN_PROGRESS -> "它已经在动，今天接着推最省认知切换成本。"
        note.horizon == NoteHorizon.SHORT -> "它离落地最近，现在推进最容易在短期内看到反馈。"
        note.folderName() == "项目" -> "项目类方向最怕拖成空想，现在补一刀更容易形成可验证版本。"
        note.folderName() == "工作" -> "工作类方向的上下文容易过期，趁还记得关键约束先往前压。"
        note.folderName() == "健康" -> "健康类方向更依赖连续节奏，现在推进更容易形成正反馈。"
        else -> "它已经累积到值得推进的程度，再拖下去只会重新散开。"
    }

private fun buildNoteCandidateNextStep(note: NoteEntity): String =
    when (note.status) {
        NoteStatus.IN_PROGRESS -> "先补一句最新进展，再把它往前拱一小步。"
        NoteStatus.IDEA -> when (note.folderName()) {
            "项目" -> "先写一个最小可验证版本，而不是继续摊大方案。"
            "工作" -> "先把它压成一个最值得验证的问题。"
            "健康" -> "先把它改成今天就能执行的一次小实验。"
            else -> "先把它压成一个今天能动手的小动作。"
        }
        NoteStatus.DONE -> "先补一条结果延展记录，说明这条结果还能往哪继续放大。"
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
