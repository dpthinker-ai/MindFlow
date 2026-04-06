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
    val stageLabel: String = "",
    val horizonLabel: String = "",
    val summary: String = "",
    val whyNow: String = "",
    val nextStep: String = "",
    val threadKey: String? = null,
    val focusNoteId: Long? = null,
    val noteId: Long? = null,
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
                ?: "先把今天最值得推进的一件事抓住。",
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
            appendLine("请像主编一样，只选一个今天值得押注的方向。不要列候选，不要泛泛鼓励。")
            appendLine("这是今天的首页主卡，需要像编辑部给出的今日判断，一天内默认保持稳定。")
            if (mainlineCandidates.isNotEmpty()) {
                appendLine("可选押注：")
                mainlineCandidates.forEachIndexed { index, candidate ->
                    appendLine("${index + 1}. ${candidate.title} · ${candidate.stageLabel.ifBlank { "无阶段" }} · ${candidate.horizonLabel.ifBlank { "无周期" }}")
                    candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("  当前判断：$it") }
                    candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("  为什么现在：$it") }
                    candidate.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("  最小动作：$it") }
                }
            }
            selectedMainlineCandidate?.let { candidate ->
                appendLine("这次要写的押注对象：${candidate.title}")
                candidate.stageLabel.takeIf { it.isNotBlank() }?.let { appendLine("阶段：$it") }
                candidate.horizonLabel.takeIf { it.isNotBlank() }?.let { appendLine("时间尺度：$it") }
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("已知判断：$it") }
                candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("已知为什么现在：$it") }
                candidate.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("已知最小动作：$it") }
            }
            if (mainlineNonce > 0 && mainlineCandidates.size > 1) {
                appendLine("用户刚点了“换一个”，这次必须换到不同的真实候选方向，而不是改写同一条内容。")
            } else if (mainlineNonce > 0) {
                appendLine("用户刚点了“换一个”，候选不多时请至少换一个角度，不要只是同义改写。")
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
        }

        val settledContextSummary = buildString {
            appendLine("请像知识编辑一样，只挑一个现在最值得保留下来的判断。不要写计划，不要写鼓励。")
            when (settledFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种会改变优先级、带清楚可信基础的判断更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：避免保守的趋势总结、空泛的进步描述和不影响决策的判断。")
                null -> Unit
            }
            settledDirection?.let { direction ->
                appendLine("方向：${direction.thread.title}")
                appendLine("沉淀候选：${direction.wikiValidatedPoint.ifBlank { direction.wikiVerifiedPoint.ifBlank { direction.wikiConclusionLine.ifBlank { direction.assetSummary } } }}")
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
        }

        val gapContextSummary = buildString {
            appendLine("请像创新编辑一样，只找一个现在最值得试的新连接。不要平均分配，不要列清单。")
            appendLine("新连接的第一职责是把两个旧点重新接上，而不是给维护建议。")
            when (gapFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种跨方向连接、经验迁移和反常识组合更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：不要给维护 chore、不要给显而易见的缺口，要更像两个旧点突然被连成了一个新方向。")
                null -> Unit
            }
            breakthroughDirection?.let { direction ->
                appendLine("方向：${direction.thread.title}")
                appendLine("突破口候选：${direction.wikiOpenQuestion.ifBlank { direction.wikiMaintenanceLine.ifBlank { direction.blocker } }}")
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
            if (primary.explorationPrompts.isNotEmpty()) {
                appendLine("探索提示：")
                primary.explorationPrompts.take(2).forEach { appendLine(it) }
            }
            if (fusionState.lines.isNotEmpty()) {
                appendLine("潜在组合：")
                fusionState.lines.take(2).forEach { appendLine(it) }
            }
            val reusableAssets = directions.followedDirections
                .mapNotNull { summary ->
                    summary.assetSummary.takeIf { it.isNotBlank() }?.let { "${summary.thread.title}：$it" }
                }
                .take(3)
            if (reusableAssets.isNotEmpty()) {
                appendLine("可复用旧积累：")
                reusableAssets.forEach { appendLine(it) }
            }
            val reusableMethods = directions.followedDirections
                .mapNotNull { summary ->
                    summary.wikiKnowledgeObjectLine.takeIf { it.contains("方法") || it.contains("实验") }
                        ?.let { "${summary.thread.title}：$it" }
                }
                .take(2)
            if (reusableMethods.isNotEmpty()) {
                appendLine("可迁移方法：")
                reusableMethods.forEach { appendLine(it) }
            }
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
        val candidates = mutableListOf<MainlineBetCandidate>()
        directions.forEach { direction ->
            candidates += MainlineBetCandidate(
                key = "thread:${direction.thread.key}",
                title = direction.thread.title,
                stageLabel = direction.stage.label,
                horizonLabel = direction.dominantHorizon.label,
                summary = direction.summary,
                whyNow = direction.whyNow,
                nextStep = direction.nextStep,
                threadKey = direction.thread.key,
                focusNoteId = direction.focusNoteId,
                noteId = direction.focusNoteId,
            )
        }
        primary.continueNote?.let { note ->
            val exists = directions.any { summary -> summary.focusNoteId == note.id }
            if (!exists) {
                candidates += MainlineBetCandidate(
                    key = "note:${note.id}",
                    title = note.topic.ifBlank { "正在推进的记录" },
                    stageLabel = note.status.label,
                    horizonLabel = note.horizon.label,
                    summary = primary.nextActionText.ifBlank { note.content.take(72).trim() },
                    whyNow = "",
                    nextStep = primary.nextActionText,
                    noteId = note.id,
                )
            }
        }
        primary.staleNote?.let { note ->
            val exists = candidates.any { candidate -> candidate.noteId == note.id }
            if (!exists) {
                candidates += MainlineBetCandidate(
                    key = "stale:${note.id}",
                    title = note.topic.ifBlank { "值得重新接上的记录" },
                    stageLabel = note.status.label,
                    horizonLabel = note.horizon.label,
                    summary = primary.staleBridge,
                    whyNow = primary.staleBridge,
                    nextStep = primary.staleNextStep,
                    noteId = note.id,
                )
            }
        }
        return candidates.distinctBy { it.key }
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
