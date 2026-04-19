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
import com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
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
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
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
    val localMaintainerSnapshot: LocalKnowledgeMaintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
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

private data class FolderSlice(
    val folderKey: String,
    val anchorLabel: String,
    val line: String,
    val support: String,
)

private data class KnowledgeSlice(
    val id: String,
    val type: KnowledgeLayerSearchType,
    val title: String,
    val line: String,
    val support: String,
    val anchorLabel: String,
    val bucketKey: String,
    val threadKey: String?,
    val noteId: Long?,
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
    private val localKnowledgeMaintenancePlanner: LocalKnowledgeMaintenancePlanner,
    private val localKnowledgeBrainPlanner: LocalKnowledgeBrainPlanner,
) : ViewModel() {
    private data class DirectionState(
        val followedDirections: List<FollowedDirectionSummary> = emptyList(),
        val themeThreads: List<ThemeThread> = emptyList(),
        val knowledgeItems: List<KnowledgeLayerSearchItem> = emptyList(),
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
        val preferMaintainerSnapshot: Boolean,
    )

    private val directionState = MutableStateFlow(DirectionState())
    private val localMaintainerSnapshotState = MutableStateFlow(LocalKnowledgeMaintenanceSnapshot())
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
        localMaintainerSnapshotState,
        knowledgeCompressionState,
        mainlineCandidateState,
        settledFeedbackState,
        gapFeedbackState,
    ) { values ->
        val primary = values[0] as FlowPrimaryInputs
        val weeklyReviewState = values[1] as WeeklyReviewState
        val fusionState = values[2] as FusionSuggestionState
        val directions = values[3] as DirectionState
        val localSnapshot = values[4] as LocalKnowledgeMaintenanceSnapshot
        val compression = values[5] as FlowKnowledgeCompressionState
        val mainlineCandidate = values[6] as MainlineBetCandidate?
        val settledFeedback = values[7] as FlowCardFeedback?
        val gapFeedback = values[8] as FlowCardFeedback?
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
            localMaintainerSnapshot = localSnapshot,
            knowledgeCompression = compression,
            settledFeedback = settledFeedback,
            gapFeedback = gapFeedback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlowUiState())

    init {
        viewModelScope.launch {
            localKnowledgeMaintenancePlanner.snapshot.collect { snapshot ->
                localMaintainerSnapshotState.value = snapshot
            }
        }
        viewModelScope.launch {
            combine(
                noteRepository.observeAllNotes(),
                threadPreferencesRepository.settings,
                directionWikiCoordinator.snapshot,
            ) { notes, prefs, wikiSnapshot -> Triple(notes, prefs, wikiSnapshot) }
                .collectLatest { (allNotes, threadPreferences, wikiSnapshot) ->
                    val activeNotes = allNotes.filter { !it.isArchived }
                    val analyzedThreads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes, limit = 12)
                    val candidateThreadKeys = (threadPreferences.followedThreadKeys + analyzedThreads.map { it.key })
                        .distinct()
                    val activeDirections = candidateThreadKeys
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
                            compareByDescending<FollowedDirectionSummary> { it.thread.key in threadPreferences.followedThreadKeys }
                                .thenByDescending { it.thread.noteCount }
                                .thenBy { it.thread.title },
                        )
                        .take(10)
                    directionState.value = DirectionState(
                        followedDirections = activeDirections,
                        themeThreads = analyzedThreads.filterNot { candidate ->
                            activeDirections.any { it.thread.key == candidate.key }
                        },
                        knowledgeItems = wikiSnapshot.knowledgeItems,
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
                localMaintainerSnapshotState,
                mainlineRefreshNonce,
                gapRefreshNonce,
                settledFeedbackState,
                gapFeedbackState,
            ) { values ->
                val primary = values[0] as FlowPrimaryInputs
                val weekly = values[1] as WeeklyReviewState
                val fusion = values[2] as FusionSuggestionState
                val directions = values[3] as DirectionState
                val maintainerSnapshot = values[4] as LocalKnowledgeMaintenanceSnapshot
                val mainlineNonce = values[5] as Int
                val gapNonce = values[6] as Int
                val settledFeedback = values[7] as FlowCardFeedback?
                val gapFeedback = values[8] as FlowCardFeedback?
                buildCompressionInput(
                    primary = primary,
                    weeklyReviewState = weekly,
                    fusionState = fusion,
                    directions = directions,
                    maintainerSnapshot = maintainerSnapshot,
                    mainlineNonce = mainlineNonce,
                    gapNonce = gapNonce,
                    settledFeedback = settledFeedback,
                    gapFeedback = gapFeedback,
                )
            }.collectLatest { input ->
                mainlineCandidateState.value = input.selectedMainlineCandidate
                val previous = knowledgeCompressionState.value
                val next = if (input.preferMaintainerSnapshot) {
                    input.fallback
                } else {
                    flowKnowledgeCompressionPlanner.summarize(
                        mainlineKey = input.mainlineKey,
                        settledKey = input.settledKey,
                        gapKey = input.gapKey,
                        mainlineContextSummary = input.mainlineContextSummary,
                        settledContextSummary = input.settledContextSummary,
                        gapContextSummary = input.gapContextSummary,
                        fallback = input.fallback,
                    )
                }
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
        maintainerSnapshot: LocalKnowledgeMaintenanceSnapshot,
        mainlineNonce: Int,
        gapNonce: Int,
        settledFeedback: FlowCardFeedback?,
        gapFeedback: FlowCardFeedback?,
    ): FlowCompressionInput {
        val primaryDirection = directions.followedDirections.firstOrNull()
        val anchoredCurrentJudgement = maintainerSnapshot.currentJudgement.takeIf {
            it.threadKey.isNotBlank() || it.noteId != null
        }
        val mainlineCandidates = buildMainlineCandidates(
            primary = primary,
            directions = directions.followedDirections,
        )
        val selectedMainlineCandidate = mainlineCandidates
            .getOrNull(mainlineNonce.floorMod(mainlineCandidates.size))
        val selectedMainlineFolderKey = selectedMainlineCandidate?.bucketKey?.folderKeyFromBucket()
        val directionFolderKeys = directions.followedDirections.associate { direction ->
            direction.thread.key to directionFolderKey(direction, primary.activeNotes)
        }
        val noteFolderKeys = primary.activeNotes.associate { note ->
            note.id to (MindFolderCatalog.normalizedKey(note.folderKey) ?: "uncategorized")
        }
        val knowledgeSlices = buildKnowledgeSlices(
            items = directions.knowledgeItems,
            noteFolderKeys = noteFolderKeys,
            threadFolderKeys = directionFolderKeys,
        )
        val settledKnowledge = selectKnowledgeSlice(
            slices = knowledgeSlices,
            excludedIds = setOfNotNull(selectedMainlineCandidate?.noteId?.let { "note:$it" }),
            preferredDifferentBucketFrom = setOfNotNull(selectedMainlineFolderKey),
            predicate = {
                it.type == KnowledgeLayerSearchType.CONCLUSION ||
                    it.type == KnowledgeLayerSearchType.EVIDENCE ||
                    it.type == KnowledgeLayerSearchType.METHOD ||
                    it.type == KnowledgeLayerSearchType.EXPERIMENT
            },
        )
        val gapKnowledge = selectKnowledgeSlice(
            slices = knowledgeSlices,
            excludedIds = setOfNotNull(settledKnowledge?.id),
            preferredDifferentBucketFrom = setOfNotNull(
                selectedMainlineFolderKey,
                settledKnowledge?.bucketKey?.folderKeyFromBucket(),
            ),
            predicate = {
                it.type == KnowledgeLayerSearchType.QUESTION ||
                    it.type == KnowledgeLayerSearchType.CONCEPT ||
                    it.type == KnowledgeLayerSearchType.EXPERIMENT ||
                    it.type == KnowledgeLayerSearchType.METHOD
            },
        )
        val settledDirection = selectDirectionSummary(
            directions = directions.followedDirections,
            directionFolderKeys = directionFolderKeys,
            excludedThreadKeys = setOfNotNull(selectedMainlineCandidate?.threadKey),
            preferredDifferentFolderFrom = setOfNotNull(selectedMainlineFolderKey),
            predicate = {
                it.wikiValidatedPoint.isNotBlank() ||
                    it.wikiVerifiedPoint.isNotBlank() ||
                    it.wikiConclusionLine.isNotBlank() ||
                    it.assetSummary.isNotBlank()
            },
        ) ?: primaryDirection
        val breakthroughDirection = selectDirectionSummary(
            directions = directions.followedDirections,
            directionFolderKeys = directionFolderKeys,
            excludedThreadKeys = setOfNotNull(selectedMainlineCandidate?.threadKey, settledDirection?.thread?.key),
            preferredDifferentFolderFrom = setOfNotNull(
                selectedMainlineFolderKey,
                settledDirection?.thread?.key?.let(directionFolderKeys::get),
            ),
            predicate = {
                it.wikiOpenQuestion.isNotBlank() ||
                    it.contrarianQuestion.isNotBlank() ||
                    it.externalHypothesis.isNotBlank() ||
                    it.opportunityGap.isNotBlank() ||
                    it.blocker.isNotBlank()
            },
        ) ?: primaryDirection
        val crossFolderSlices = buildFolderSlices(
            notes = primary.activeNotes,
            excludedFolderKeys = setOfNotNull(
                selectedMainlineFolderKey,
                settledDirection?.thread?.key?.let(directionFolderKeys::get),
            ),
        )
        val recurringLine = selectedMainlineCandidate?.summary
            ?.takeIf { it.isNotBlank() }
            ?: anchoredCurrentJudgement?.line?.takeIf { it.isNotBlank() }
            ?: primary.nextActionText.takeIf { it.isNotBlank() }
            ?: primary.staleBridge.takeIf { it.isNotBlank() }
            ?: primary.staleNextStep.takeIf { it.isNotBlank() }
            ?: "先让几条记录开始互相呼应，这里才会长出真正值得继续养的暗线。"
        val recurringWhyNow = selectedMainlineCandidate?.whyNow
            ?.takeIf { it.isNotBlank() }
            ?: anchoredCurrentJudgement?.support?.takeIf { it.isNotBlank() }
            ?: settledKnowledge?.support?.takeIf { it.isNotBlank() }
            ?: primary.staleBridge.takeIf { it.isNotBlank() }
            ?: weeklyReviewState.items.firstOrNull()?.text.orEmpty()
        val durableSettledLine = settledKnowledge?.line
            .takeIf { !it.isNullOrBlank() }
            ?: settledDirection?.wikiValidatedPoint?.takeIf { it.isNotBlank() }
            ?: settledDirection?.wikiVerifiedPoint?.takeIf { it.isNotBlank() }
            ?: settledDirection?.wikiConclusionLine?.takeIf { it.isNotBlank() }
            ?: settledDirection?.assetSummary?.takeIf { it.isNotBlank() }
            ?: crossFolderSlices.firstOrNull()?.line
        val durableSettledSupport = settledKnowledge?.support
            .takeIf { !it.isNullOrBlank() }
            ?: settledDirection?.wikiGroundingLine?.takeIf { it.isNotBlank() }
            ?: settledDirection?.wikiTrustLine?.takeIf { it.isNotBlank() }
            ?: settledDirection?.wikiKnowledgeObjectLine?.takeIf { it.isNotBlank() }
            ?: crossFolderSlices.firstOrNull()?.support
        val collisionLine = maintainerSnapshot.newConnection.line.ifBlank {
            gapKnowledge?.line
                .takeIf { !it.isNullOrBlank() }
                ?: breakthroughDirection?.contrarianQuestion?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.externalHypothesis?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.opportunityGap?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.wikiOpenQuestion?.takeIf { it.isNotBlank() }
                ?: crossFolderSlices.getOrNull(1)?.line
                ?: fusionState.lines.firstOrNull().orEmpty()
        }
        val collisionSupport = maintainerSnapshot.newConnection.support.ifBlank {
            gapKnowledge?.support
                .takeIf { !it.isNullOrBlank() }
                ?: breakthroughDirection?.postValidationAction?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.validationReason?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.outsideAngle?.takeIf { it.isNotBlank() }
                ?: breakthroughDirection?.wikiMaintenanceFocusLine?.takeIf { it.isNotBlank() }
                ?: crossFolderSlices.getOrNull(1)?.support
                ?: fusionState.lines.getOrNull(1).orEmpty()
        }

        val fallback = FlowKnowledgeCompressionState(
            mainline = recurringLine,
            whyNow = recurringWhyNow,
            mainlineSource = DailyBriefSource.RULE,
            settledLine = durableSettledLine
                ?: maintainerSnapshot.recentAbsorption.line.takeIf { it.isNotBlank() }
                ?: weeklyReviewState.items.firstOrNull { it.label == "主线" }?.text.orEmpty(),
            settledSupport = durableSettledSupport
                ?: maintainerSnapshot.recentAbsorption.support.takeIf { it.isNotBlank() }
                ?: weeklyReviewState.statsLine,
            settledSource = DailyBriefSource.RULE,
            gapLine = collisionLine,
            gapSupport = collisionSupport,
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
            append(':')
            append(directions.knowledgeItems.joinToString("|") {
                listOf(
                    it.id,
                    it.type.name,
                    it.title,
                    it.summary,
                    it.supportLine,
                    it.threadKey,
                    it.noteId?.toString().orEmpty(),
                ).joinToString("~")
            })
        }
        val todayKey = LocalDate.now().toString()
        val mainlineKey = "$todayKey:mainline:$mainlineNonce"
        val settledKey = "$signature:settled"
        val gapKey = "$signature:gap:$gapNonce"

        val mainlineContextSummary = buildString {
            appendLine("请像想法孵化器背后的本地知识维护员一样，只指出用户其实一直在反复想什么。")
            appendLine("这张卡不是今日判断，也不是任务提示。它必须像一条已经反复出现、正在成形的暗线。")
            appendLine("不要因为最近最活跃的是同一个项目或应用，就默认继续围着它改写；如果其他文件夹或主题里也有真实势能，优先选更能拉开视野的那条线。")
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前最有复现信号的对象：${candidate.title}")
                appendLine("主要来自：${candidate.anchorLabel}")
                candidate.stageLabel.takeIf { it.isNotBlank() }?.let { appendLine("阶段：$it") }
                candidate.horizonLabel.takeIf { it.isNotBlank() }?.let { appendLine("时间尺度：$it") }
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("这条暗线当前像什么：$it") }
                candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("为什么现在值得继续养：$it") }
                candidate.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("继续养它的下一口：$it") }
            }
            if (mainlineNonce > 0 && mainlineCandidates.size > 1) {
                appendLine("用户刚点了“换一条线”，这次必须切到不同项目、文件夹或方向的真实暗线，而不是改写同一主题。")
            } else if (mainlineNonce > 0) {
                appendLine("用户刚点了“换一条线”，候选不多时请至少换一个角度，不要只是同义改写。")
            }
            if (crossFolderSlices.isNotEmpty()) {
                appendLine("除当前对象外，其他文件夹里还有这些真实线索：")
                crossFolderSlices.take(3).forEach { slice ->
                    appendLine("${slice.anchorLabel}：${slice.line}")
                }
            }
            if (knowledgeSlices.isNotEmpty()) {
                appendLine("本地知识层里最近还有这些对象：")
                knowledgeSlices.take(4).forEach { slice ->
                    appendLine("${slice.anchorLabel}：${slice.line}")
                }
            }
            directions.followedDirections.firstOrNull { it.thread.key == selectedMainlineCandidate?.threadKey }?.let { direction ->
                direction.wikiContinuityLine.takeIf { it.isNotBlank() }?.let { appendLine("连续性：$it") }
                direction.wikiTrajectoryLine.takeIf { it.isNotBlank() }?.let { appendLine("正在往哪长：$it") }
                direction.lastProgressLine.takeIf { it.isNotBlank() }?.let { appendLine("最近动作：$it") }
            }
            primary.nextActionText.takeIf { it.isNotBlank() }?.let { appendLine("最近手上动作：$it") }
            primary.staleBridge.takeIf { it.isNotBlank() }?.let { appendLine("旧线重连理由：$it") }
            primary.staleNextStep.takeIf { it.isNotBlank() }?.let { appendLine("旧线重连动作：$it") }
            weeklyReviewState.items.firstOrNull { it.label == "推进" }
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine("本周反复推进：$it") }
            appendLine("目标：输出一条反复回来的暗线，以及为什么它现在值得继续养。")
        }

        val settledContextSummary = buildString {
            appendLine("请像 wiki 维护员一样，只挑一个已经开始长成可复用资产的结果、方法或结论。不要写计划，不要写鼓励。")
            appendLine("不要把那条暗线换一种说法重复出来。这张卡必须更像已经能留下来的资产。")
            appendLine("不要默认复述当前最活跃项目里的结果；如果其他主题里已经长出更通用、更可迁移的资产，优先选那个。")
            when (settledFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种会改变优先级、而且带清楚可信基础的资产更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：避免保守的趋势总结、空泛进步描述和还不能复用的判断。")
                null -> Unit
            }
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前反复出现的暗线：${candidate.title}")
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("暗线主句：$it") }
                candidate.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("暗线理由：$it") }
            }
            settledKnowledge?.let { item ->
                appendLine("知识对象：${item.anchorLabel}")
                appendLine("资产候选：${item.line}")
                item.support.takeIf { it.isNotBlank() }?.let { appendLine("可信基础：$it") }
            }
            val corroboratingKnowledge = knowledgeSlices
                .filter { it.id != settledKnowledge?.id }
                .mapNotNull { slice ->
                    slice.line.takeIf { it.isNotBlank() }?.let { "${slice.anchorLabel}：$it" }
                }
                .take(2)
            if (corroboratingKnowledge.isNotEmpty()) {
                appendLine("旁证积累：")
                corroboratingKnowledge.forEach { appendLine(it) }
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
            if (crossFolderSlices.isNotEmpty()) {
                appendLine("如果其他项目或文件夹里已经有更成熟的积累，优先从不同文件夹里挑真正长成的资产，不要继续围着同一主题改写。")
                crossFolderSlices.take(3).forEach { slice ->
                    appendLine("${slice.anchorLabel}：${slice.line}｜${slice.support}")
                }
            }
            appendLine("目标：输出一条已经开始可复用的资产，而不是趋势总结。")
        }

        val gapContextSummary = buildString {
            appendLine("请像想法孵化器背后的 maintainer 一样，只找一个现在最值得撞一下的连接。不要平均分配，不要列清单。")
            appendLine("这张卡的职责不是制造口号，而是指出哪两个点碰一下最可能长出新东西，以及为什么现在值得试。")
            appendLine("不要重复那条暗线，也不要把已经长成的资产换个词说一遍。")
            appendLine("如果最近最热的是同一个项目，先向外找不同文件夹、不同主题或不同知识对象之间的连接，不要继续在同一条线内部打转。")
            when (gapFeedback) {
                FlowCardFeedback.HELPFUL -> appendLine("最近反馈：这种真正指出碰撞对象和试一下理由的结果更有用。")
                FlowCardFeedback.FLAT -> appendLine("最近反馈：不要给维护口号、不要给泛泛灵感，要明确指出哪两点碰一下。")
                null -> Unit
            }
            selectedMainlineCandidate?.let { candidate ->
                appendLine("当前暗线：${candidate.title}")
                candidate.summary.takeIf { it.isNotBlank() }?.let { appendLine("暗线主句：$it") }
            }
            settledDirection?.let { direction ->
                direction.wikiValidatedPoint
                    .ifBlank { direction.wikiVerifiedPoint }
                    .ifBlank { direction.wikiConclusionLine }
                    .takeIf { it.isNotBlank() }
                    ?.let { appendLine("已经长成的资产：$it") }
            }
            gapKnowledge?.let { item ->
                appendLine("碰撞对象：${item.anchorLabel}")
                appendLine("碰撞候选：${item.line}")
                item.support.takeIf { it.isNotBlank() }?.let { appendLine("为什么值得试：$it") }
            }
            val crossKnowledgeAssets = knowledgeSlices
                .filter { it.id != gapKnowledge?.id }
                .filter { it.threadKey != selectedMainlineCandidate?.threadKey }
                .mapNotNull { slice ->
                    slice.line.takeIf { it.isNotBlank() }?.let { "${slice.anchorLabel}：$it" }
                }
                .distinct()
                .take(3)
            if (crossKnowledgeAssets.isNotEmpty()) {
                appendLine("其他项目/知识对象可借来的碰撞材料：")
                crossKnowledgeAssets.forEach { appendLine(it) }
            }
            if (crossFolderSlices.isNotEmpty()) {
                appendLine("如果有其他项目或文件夹的材料，优先把当前暗线和不同文件夹的旧积累连起来，不要继续围着同一方向打转。")
                crossFolderSlices.take(3).forEach { slice ->
                    appendLine("${slice.anchorLabel}：${slice.line}")
                }
            }
            if (primary.explorationPrompts.isNotEmpty()) {
                appendLine("待吸收材料：")
                primary.explorationPrompts.take(2).forEach { appendLine(it) }
            }
            if (fusionState.lines.isNotEmpty()) {
                appendLine("潜在碰撞：")
                fusionState.lines.take(2).forEach { appendLine(it) }
            }
            appendLine("目标：输出一次最值得试的碰撞，以及为什么现在值得撞。")
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
            preferMaintainerSnapshot = maintainerSnapshot.hasContent && mainlineNonce == 0 && gapNonce == 0,
        )
    }

    fun refreshMainline() {
        mainlineRefreshNonce.value = mainlineRefreshNonce.value + 1
    }

    fun refreshGap() {
        gapRefreshNonce.value = gapRefreshNonce.value + 1
    }

    fun refreshLocalKnowledgeBrain() {
        viewModelScope.launch {
            localKnowledgeBrainPlanner.rebuildAll()
        }
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
                    summary = buildThreadCandidateSummary(direction, threadNotes, threadFocus),
                    whyNow = buildThreadCandidateWhyNow(direction, threadNotes, threadFocus),
                    nextStep = buildThreadCandidateNextStep(direction, threadFocus),
                    bucketKey = threadFolderKey?.let { "folder:$it" } ?: "direction:${direction.thread.key}",
                    threadKey = direction.thread.key,
                    focusNoteId = direction.focusNoteId,
                    noteId = threadFocus?.id ?: direction.focusNoteId,
                ),
                score = 76 +
                    direction.thread.noteCount +
                    if (direction.wikiContinuityLine.isNotBlank() || direction.wikiTrajectoryLine.isNotBlank()) 6 else 0 +
                    if (direction.lastProgressLine.isNotBlank()) 2 else 0,
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
                        title = note.topic.ifBlank { "$folderName 里最值得继续养的一条" },
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
                        title = note.topic.ifBlank { "正在长出来的一条想法" },
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
                        title = note.topic.ifBlank { "值得重新接回来的旧线" },
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
            localKnowledgeMaintenancePlanner: LocalKnowledgeMaintenancePlanner,
            localKnowledgeBrainPlanner: LocalKnowledgeBrainPlanner,
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
                    localKnowledgeMaintenancePlanner = localKnowledgeMaintenancePlanner,
                    localKnowledgeBrainPlanner = localKnowledgeBrainPlanner,
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

private fun selectDirectionSummary(
    directions: List<FollowedDirectionSummary>,
    directionFolderKeys: Map<String, String?>,
    excludedThreadKeys: Set<String>,
    preferredDifferentFolderFrom: Set<String>,
    predicate: (FollowedDirectionSummary) -> Boolean,
): FollowedDirectionSummary? {
    val filtered = directions.filter { predicate(it) && it.thread.key !in excludedThreadKeys }
    return filtered.firstOrNull { summary ->
        val folderKey = directionFolderKeys[summary.thread.key]
        folderKey != null && folderKey !in preferredDifferentFolderFrom
    } ?: filtered.firstOrNull()
}

private fun directionFolderKey(
    direction: FollowedDirectionSummary,
    notes: List<NoteEntity>,
): String? = dominantFolderKey(NoteConnectionAnalyzer.notesForThread(direction.thread.key, notes))

private fun buildFolderSlices(
    notes: List<NoteEntity>,
    excludedFolderKeys: Set<String>,
): List<FolderSlice> = notes
    .filter { !it.isArchived }
    .groupBy { MindFolderCatalog.normalizedKey(it.folderKey) ?: "uncategorized" }
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<String, List<NoteEntity>>> { (_, grouped) ->
            grouped.count { it.status == NoteStatus.IN_PROGRESS }
        }.thenByDescending { (_, grouped) ->
            grouped.maxOfOrNull { it.updatedAt } ?: 0L
        },
    )
    .mapNotNull { (folderKey, grouped) ->
        if (folderKey in excludedFolderKeys) return@mapNotNull null
        val note = grouped.sortedWith(
            compareByDescending<NoteEntity> { it.status == NoteStatus.IN_PROGRESS }
                .thenByDescending { it.horizon.priority }
                .thenByDescending { it.updatedAt },
        ).firstOrNull() ?: return@mapNotNull null
        val folderName = MindFolderCatalog.fromKey(folderKey)?.name ?: "其他"
        FolderSlice(
            folderKey = folderKey,
            anchorLabel = "$folderName · ${note.topic.ifBlank { "一条记录" }}",
            line = buildNoteCandidateSummary(note),
            support = buildNoteCandidateWhyNow(note),
        )
    }
    .take(4)

private fun buildKnowledgeSlices(
    items: List<KnowledgeLayerSearchItem>,
    noteFolderKeys: Map<Long, String>,
    threadFolderKeys: Map<String, String?>,
): List<KnowledgeSlice> = items
    .sortedWith(
        compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
            .thenByDescending { it.updatedAt },
    )
    .mapNotNull { item ->
        val line = item.summary.takeIf { it.isNotBlank() } ?: item.supportLine.takeIf { it.isNotBlank() }
        line?.let {
            val folderKey = item.noteId?.let(noteFolderKeys::get)
                ?: item.threadKey.takeIf { it.isNotBlank() }?.let(threadFolderKeys::get)
                ?: "knowledge:${item.type.name.lowercase()}"
            KnowledgeSlice(
                id = item.id,
                type = item.type,
                title = item.title,
                line = it,
                support = item.supportLine,
                anchorLabel = buildKnowledgeAnchorLabel(item, folderKey),
                bucketKey = if (folderKey.startsWith("knowledge:")) folderKey else "folder:$folderKey",
                threadKey = item.threadKey.takeIf { it.isNotBlank() },
                noteId = item.noteId,
            )
        }
    }
    .distinctBy { it.id }
    .take(12)

private fun buildKnowledgeAnchorLabel(
    item: KnowledgeLayerSearchItem,
    folderKey: String,
): String {
    val folderName = if (folderKey.startsWith("knowledge:")) {
        null
    } else {
        MindFolderCatalog.fromKey(folderKey)?.name
    }
    return buildString {
        folderName?.let {
            append(it)
            append(" · ")
        }
        append(item.type.label)
        append(" · ")
        append(item.title)
    }
}

private fun selectKnowledgeSlice(
    slices: List<KnowledgeSlice>,
    excludedIds: Set<String>,
    preferredDifferentBucketFrom: Set<String>,
    predicate: (KnowledgeSlice) -> Boolean,
): KnowledgeSlice? {
    val filtered = slices.filter { predicate(it) && it.id !in excludedIds }
    return filtered.firstOrNull { slice -> slice.bucketKey !in preferredDifferentBucketFrom }
        ?: filtered.firstOrNull()
}

private fun String.folderKeyFromBucket(): String? =
    takeIf { startsWith("folder:") }?.removePrefix("folder:")

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
        NoteStatus.IN_PROGRESS -> "这条线已经不是一次性念头了，它最近一直在回到你手里。"
        NoteStatus.IDEA -> when (note.horizon) {
            NoteHorizon.SHORT -> "这颗短期火花还很脆，现在不接一下，很容易直接沉下去。"
            NoteHorizon.MEDIUM -> "这条中期想法已经攒到可以长成一条线，不该继续散在零碎记录里。"
            NoteHorizon.LONG -> "这条长期问题已经露出轮廓，说明它不是偶然冒出来的一次念头。"
        }
        NoteStatus.DONE -> "这里已经留下一个结果，值得沿着它继续外扩，而不是从头再起一条线。"
    }

private fun buildNoteCandidateWhyNow(note: NoteEntity): String =
    when {
        note.status == NoteStatus.IN_PROGRESS -> "它已经在动了，顺手再养一口最容易让这条线继续长。"
        note.horizon == NoteHorizon.SHORT -> "这种近处火花最怕掉地上，现在不接就很容易直接忘掉。"
        note.folderName() == "项目" -> "项目类想法最怕只停在脑内，现在补一条具体记录更容易长成东西。"
        note.folderName() == "工作" -> "工作类上下文衰减得快，趁约束还清楚，先把它压回一条活线。"
        note.folderName() == "健康" -> "健康类线索依赖连续节奏，现在接住更容易形成真实反馈。"
        else -> "它已经积到值得继续养的程度，再拖下去只会重新散开。"
    }

private fun buildNoteCandidateNextStep(note: NoteEntity): String =
    when (note.status) {
        NoteStatus.IN_PROGRESS -> "先补一句最新变化，再看看它应该和哪条旧线连起来。"
        NoteStatus.IDEA -> when (note.folderName()) {
            "项目" -> "先写下它真正指向的问题或最小原型，不要继续摊大方案。"
            "工作" -> "先把它压成一个值得继续追的问题，而不是只留模糊印象。"
            "健康" -> "先把它改成今天就能观察反馈的一次小实验。"
            else -> "先补一条更具体的记录，让它从火花开始长成一条线。"
        }
        NoteStatus.DONE -> "先补一条结果延展记录，说明这个结果还能往哪继续放大。"
    }

private fun graphTypePriority(type: KnowledgeLayerSearchType): Int =
    when (type) {
        KnowledgeLayerSearchType.CONCLUSION -> 0
        KnowledgeLayerSearchType.EVIDENCE -> 1
        KnowledgeLayerSearchType.METHOD -> 2
        KnowledgeLayerSearchType.EXPERIMENT -> 3
        KnowledgeLayerSearchType.CONCEPT -> 4
        KnowledgeLayerSearchType.QUESTION -> 5
        KnowledgeLayerSearchType.DIRECTION -> 6
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
    focusNote ?: return "这条线已经形成了连续记录，现在继续养最容易长出更清楚的轮廓。"
    val repeatedTag = notes
        .flatMap { it.tags.distinct() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return when (focusNote.status) {
        NoteStatus.IN_PROGRESS -> "这条线已经在动了，顺着「${focusNote.topic.ifBlank { thread.title }}」继续养最省力。"
        NoteStatus.IDEA -> when {
            repeatedTag != null -> "它和你反复记录的「$repeatedTag」是同一条线，现在最适合继续接住它。"
            notes.size >= 3 -> "这条线已经连续出现 ${notes.size} 次，再拖下去只会重新散开。"
            else -> "这条线刚冒出新线索，趁上下文还热，先补一条更具体的记录。"
        }
        NoteStatus.DONE -> "这里已经有做成的结果，可以沿着结果外扩，而不是从头再起一条线。"
    }
}

private fun nextStepForThread(
    thread: ThemeThread,
    focusNote: NoteEntity?,
): String {
    focusNote ?: return "先补一条更具体的记录，把这条线重新接回今天。"
    return when (focusNote.status) {
        NoteStatus.IN_PROGRESS -> "先补一句最新变化，再看看它现在应该和哪条旧线连起来。"
        NoteStatus.IDEA -> when (focusNote.folderName()) {
            "工作" -> "先把它压成一个值得继续追的问题，再补一条更具体的工作记录。"
            "项目" -> "先写出最小原型或最小验证，不要继续只停在脑内。"
            "健康" -> "先把它改成今天能观察反馈的一次小实验。"
            else -> "先给「${focusNote.topic.ifBlank { thread.title }}」补一条更具体的记录。"
        }
        NoteStatus.DONE -> "先补一条结果延展记录，说明这个结果还能往哪一步继续放大。"
    }
}

private fun buildThreadCandidateSummary(
    direction: FollowedDirectionSummary,
    notes: List<NoteEntity>,
    focusNote: NoteEntity?,
): String =
    direction.wikiContinuityLine.takeIf { it.isNotBlank() }
        ?: direction.wikiTrajectoryLine.takeIf { it.isNotBlank() }
        ?: direction.summary.takeIf { it.isNotBlank() }
        ?: when {
            notes.size >= 4 -> "这些记录其实在反复咬同一个问题，「${direction.thread.title}」已经开始长成一条线。"
            focusNote != null -> "你最近总会绕回「${focusNote.topic.ifBlank { direction.thread.title }}」，这已经不是一次性念头。"
            else -> "「${direction.thread.title}」正在从零散记录里慢慢长出轮廓。"
        }

private fun buildThreadCandidateWhyNow(
    direction: FollowedDirectionSummary,
    notes: List<NoteEntity>,
    focusNote: NoteEntity?,
): String =
    direction.whyNow.takeIf { it.isNotBlank() }
        ?: direction.wikiSnapshotCadenceLine.takeIf { it.isNotBlank() }
        ?: direction.rhythmLine.takeIf { it.isNotBlank() }
        ?: direction.lastProgressLine.takeIf { it.isNotBlank() }
        ?: whyNowForThread(direction.thread, notes, focusNote)

private fun buildThreadCandidateNextStep(
    direction: FollowedDirectionSummary,
    focusNote: NoteEntity?,
): String =
    direction.nextStep.takeIf { it.isNotBlank() }
        ?: direction.nextCheckInLine.takeIf { it.isNotBlank() }
        ?: nextStepForThread(direction.thread, focusNote)

private fun NoteEntity.folderName(): String? =
    com.mindflow.app.data.model.MindFolderCatalog.fromKey(folderKey)?.name

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
