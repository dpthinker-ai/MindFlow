package com.mindflow.app.ui.screens.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionState
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.InsightLine
import com.mindflow.app.ui.components.InsightTone
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.PanelShape
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.FlowFocus
import com.mindflow.app.ui.navigation.KnowledgeMaintenanceSeedContext
import com.mindflow.app.ui.navigation.buildKnowledgeMaintenanceCaptureSeed
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter
import kotlinx.coroutines.delay

@Composable
fun FlowRoute(
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
    initialFocus: FlowFocus? = null,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val viewModel: FlowViewModel = viewModel(
        factory = FlowViewModel.factory(
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
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FlowScreen(
        uiState = uiState,
        focus = initialFocus,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
        onCreateCapture = onCreateCapture,
    )
}

@Composable
private fun FlowScreen(
    uiState: FlowUiState,
    focus: FlowFocus?,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val listState = rememberLazyListState()
    val highlightToday = focus == FlowFocus.TODAY
    val highlightReconnect = focus == FlowFocus.RECONNECT
    val highlightReview = focus == FlowFocus.REVIEW
    val highlightDirection = focus == FlowFocus.DIRECTION
    val primaryDirection = remember(uiState.followedDirections) {
        uiState.followedDirections.firstOrNull()
    }
    val settledDirection = remember(uiState.followedDirections) {
        uiState.followedDirections.firstOrNull {
            it.wikiValidatedPoint.isNotBlank() ||
                it.wikiVerifiedPoint.isNotBlank() ||
                it.wikiConclusionLine.isNotBlank() ||
                it.assetSummary.isNotBlank()
        } ?: uiState.followedDirections.firstOrNull()
    }
    val breakthroughDirection = remember(uiState.followedDirections) {
        uiState.followedDirections.firstOrNull {
            it.wikiOpenQuestion.isNotBlank() ||
                it.wikiMaintenanceLine.isNotBlank() ||
                it.wikiMaintenanceFocusLine.isNotBlank() ||
                it.blocker.isNotBlank()
        } ?: uiState.followedDirections.firstOrNull()
    }
    val subtitle = remember(focus) {
        when (focus) {
            FlowFocus.TODAY -> "先押今天最值得推进的一件事。"
            FlowFocus.RECONNECT -> "先把最值得重新接回来的那条线接上。"
            FlowFocus.REVIEW -> "先看最近真正成立的一个判断。"
            FlowFocus.DIRECTION -> "先看最可能打开新东西的那个切口。"
            null -> "先押一件事，再守住一个已成立判断。"
        }
    }
    var showMainline by remember { mutableStateOf(false) }
    var showSettled by remember { mutableStateOf(false) }
    var showGap by remember { mutableStateOf(false) }

    LaunchedEffect(focus) {
        when (focus) {
            FlowFocus.TODAY,
            FlowFocus.RECONNECT,
            null -> listState.scrollToItem(1)
            FlowFocus.REVIEW,
            FlowFocus.DIRECTION -> listState.scrollToItem(4)
        }
    }
    LaunchedEffect(
        uiState.knowledgeCompression.mainline,
        uiState.knowledgeCompression.settledLine,
        uiState.knowledgeCompression.gapLine,
    ) {
        showMainline = false
        showSettled = false
        showGap = false
        delay(40)
        showMainline = true
        delay(70)
        showSettled = true
        delay(70)
        showGap = true
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MindFlow",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showMainline,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                        exit = fadeOut(),
                        label = "flowMainlineReveal",
                    ) {
                        MainlineFocusCard(
                            note = uiState.continueNote,
                            direction = primaryDirection,
                            nextActionText = uiState.nextActionText,
                            compression = uiState.knowledgeCompression,
                            reconnectNote = uiState.staleNote,
                            reconnectBridge = uiState.staleBridge,
                            reconnectStep = uiState.staleNextStep,
                            highlighted = highlightToday || highlightReconnect,
                            onOpenThread = onOpenThread,
                            onOpenNote = onOpenNote,
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showSettled,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                        exit = fadeOut(),
                        label = "flowSettledReveal",
                    ) {
                        SettledKnowledgeCard(
                            direction = settledDirection,
                            compression = uiState.knowledgeCompression,
                            highlighted = highlightReview,
                            onOpenThread = onOpenThread,
                            onOpenNote = onOpenNote,
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showGap,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                        exit = fadeOut(),
                        label = "flowGapReveal",
                    ) {
                        BreakthroughGapCard(
                            direction = breakthroughDirection,
                            explorationPrompts = uiState.explorationPrompts,
                            compression = uiState.knowledgeCompression,
                            highlighted = highlightDirection || highlightReconnect,
                            onOpenThread = onOpenThread,
                            onCreateCapture = onCreateCapture,
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun MainlineFocusCard(
    note: NoteEntity?,
    direction: FollowedDirectionSummary?,
    nextActionText: String,
    compression: FlowKnowledgeCompressionState,
    reconnectNote: NoteEntity?,
    reconnectBridge: String,
    reconnectStep: String,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val accent = noteStatusAccent(note?.status ?: NoteStatus.IN_PROGRESS)
    val title = direction?.thread?.title
        ?: note?.topic?.takeIf { it.isNotBlank() }
        ?: "今天先押这一件"
    val mainLine = direction?.summary
        ?.takeIf { it.isNotBlank() }
        ?: nextActionText.takeIf { it.isNotBlank() }
        ?: reconnectStep.takeIf { it.isNotBlank() }
        ?: "先从一条真正想做成的事开始，不必面面俱到。"
    val whyNowLine = direction?.whyNow
        ?.takeIf { it.isNotBlank() }
        ?: reconnectBridge.takeIf { it.isNotBlank() }
    val resolvedMainLine = compression.mainline.ifBlank { mainLine }
    val resolvedWhyNow = compression.whyNow.ifBlank { whyNowLine.orEmpty() }
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.45f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "今日押注",
                headline = if (note != null || direction != null) "今天先推这一件" else "先把一条真正值得做成的事接上",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                direction?.let {
                    InsightChip(text = it.stage.label, tone = InsightTone.Primary)
                    InsightChip(text = it.dominantHorizon.label, tone = InsightTone.Neutral)
                } ?: note?.let {
                    InsightChip(text = it.status.label, tone = InsightTone.Primary)
                    InsightChip(text = it.horizon.label, tone = InsightTone.Neutral)
                }
            }
            title.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = resolvedMainLine,
                label = "flowMainlineText",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (resolvedWhyNow.isNotBlank() || compression.mainlineSource != DailyBriefSource.AI) {
                InsightBlock(
                    sourceLabel = if (compression.mainlineSource == DailyBriefSource.AI) null else "本地判断",
                    tone = InsightTone.Primary,
                ) {
                    AnimatedContent(
                        targetState = resolvedWhyNow,
                        label = "flowMainlineWhyNow",
                    ) { text ->
                        if (text.isNotBlank()) {
                            InsightLine(label = "为什么现在", text = text, maxLines = 2)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "继续推进",
                    onClick = {
                        direction?.focusNoteId?.let(onOpenNote)
                            ?: note?.id?.let(onOpenNote)
                            ?: direction?.thread?.key?.let(onOpenThread)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (direction != null) {
                    GhostActionButton(
                        text = "看方向",
                        onClick = { onOpenThread(direction.thread.key) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (reconnectNote != null) {
                    GhostActionButton(
                        text = "重新接上",
                        onClick = { onOpenNote(reconnectNote.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettledKnowledgeCard(
    direction: FollowedDirectionSummary?,
    compression: FlowKnowledgeCompressionState,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val settledLine = direction?.wikiValidatedPoint
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiVerifiedPoint?.takeIf { it.isNotBlank() }
        ?: direction?.wikiConclusionLine?.takeIf { it.isNotBlank() }
        ?: direction?.assetSummary?.takeIf { it.isNotBlank() }
    val trustChip = when {
        direction?.wikiValidatedPoint?.isNotBlank() == true -> "已验证"
        direction?.wikiVerifiedPoint?.isNotBlank() == true -> "已查证"
        direction?.wikiConclusionLine?.isNotBlank() == true -> "当前结论"
        else -> "正在沉淀"
    }
    val supportLine = direction?.wikiGroundingLine
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiTrustLine?.takeIf { it.isNotBlank() }
        ?: direction?.wikiKnowledgeObjectLine?.takeIf { it.isNotBlank() }
    val resolvedSettledLine = compression.settledLine.ifBlank { settledLine.orEmpty() }
    val resolvedSupportLine = compression.settledSupport.ifBlank { supportLine.orEmpty() }
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) AccentBlue.copy(alpha = 0.38f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "已成立",
                headline = if (resolvedSettledLine.isNotBlank()) "最近最值得信的一点" else "开始长出能被依赖的判断",
            )
            if (direction == null || resolvedSettledLine.isBlank()) {
                Text(
                    text = "再沿着同一条方向多推几步，这里会开始留下真正能拿来用的判断，而不是一堆散记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            } else {
                InsightChip(text = trustChip, tone = InsightTone.Primary)
                AnimatedContent(
                    targetState = resolvedSettledLine,
                    label = "flowSettledLine",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextMain,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                resolvedSupportLine.takeIf { it.isNotBlank() }?.let {
                    InsightBlock(
                        sourceLabel = if (compression.settledSource == DailyBriefSource.AI) null else "本地判断",
                        tone = InsightTone.Neutral,
                    ) {
                        AnimatedContent(
                            targetState = it,
                            label = "flowSettledSupport",
                        ) { text ->
                            InsightLine(label = "为什么可信", text = text, maxLines = 2)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "看这条判断",
                        onClick = {
                            direction.assetNoteId?.let(onOpenNote) ?: onOpenThread(direction.thread.key)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GhostActionButton(
                        text = "看方向",
                        onClick = { onOpenThread(direction.thread.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakthroughGapCard(
    direction: FollowedDirectionSummary?,
    explorationPrompts: List<String>,
    compression: FlowKnowledgeCompressionState,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val gapLine = direction?.wikiOpenQuestion
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiMaintenanceLine?.takeIf { it.isNotBlank() }
        ?: direction?.wikiMaintenanceFocusLine?.takeIf { it.isNotBlank() }
        ?: direction?.blocker?.takeIf { it.isNotBlank() }
        ?: explorationPrompts.firstOrNull().orEmpty()
    val supportLine = direction?.wikiMaintenanceFocusLine
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiMaintenanceTargetLine?.takeIf { it.isNotBlank() }
        ?: direction?.wikiMaintenanceSourceLine?.takeIf { it.isNotBlank() }
        ?: direction?.validationStep?.takeIf { it.isNotBlank() }
    val resolvedGapLine = compression.gapLine.ifBlank { gapLine }
    val resolvedGapSupport = compression.gapSupport.ifBlank { supportLine.orEmpty() }
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "新机会",
                headline = if (resolvedGapLine.isNotBlank()) "最可能打开新东西的一刀" else "先找到最值得试的新连接",
            )
            if (resolvedGapLine.isBlank()) {
                Text(
                    text = "再多沿着同一条方向推一点，这里会开始指出最值得试的新连接或切口。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            } else {
                AnimatedContent(
                    targetState = resolvedGapLine,
                    label = "flowGapLine",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextMain,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                resolvedGapSupport.takeIf { it.isNotBlank() }?.let {
                    InsightBlock(
                        sourceLabel = if (compression.gapSource == DailyBriefSource.AI) null else "本地判断",
                        tone = InsightTone.Neutral,
                    ) {
                        AnimatedContent(
                            targetState = it,
                            label = "flowGapSupport",
                        ) { text ->
                            InsightLine(
                                label = "为什么值得试",
                                text = text,
                                emphasize = true,
                                maxLines = 2,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (direction != null && direction.wikiMaintenanceLine.isNotBlank()) {
                        ActionButton(
                            text = "记一条",
                            onClick = { onCreateCapture(direction.toMaintenanceCaptureSeed()) },
                            modifier = Modifier.weight(1f),
                        )
                    } else if (direction != null && direction.validationStep.isNotBlank()) {
                        ActionButton(
                            text = "记一条",
                            onClick = { onCreateCapture(direction.toResearchCaptureSeed()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (direction != null) {
                        GhostActionButton(
                            text = "看方向",
                            onClick = { onOpenThread(direction.thread.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeTrailCard(
    weeklyItems: List<WeeklyReviewItem>,
    weeklySource: DailyBriefSource,
    weeklyStatsLine: String,
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    onOpenThread: (String) -> Unit,
) {
    val weeklyLine = weeklyItems.firstOrNull()?.text.orEmpty()
    val connectionLine = suggestions.firstOrNull().orEmpty()
    if (
        weeklyLine.isBlank() &&
        connectionLine.isBlank() &&
        followedDirections.isEmpty() &&
        threads.isEmpty()
    ) {
        return
    }
    PanelCard {
        SectionHeader(
            title = "知识脉络",
            headline = "需要时再看更长的线，不打断今天主线",
        )
        if (weeklyLine.isNotBlank() || connectionLine.isNotBlank()) {
            InsightBlock(
                sourceLabel = when {
                    weeklySource == DailyBriefSource.AI || fusionSource == DailyBriefSource.AI -> "AI 判断"
                    else -> "最近脉络"
                },
                tone = InsightTone.Neutral,
            ) {
                weeklyLine.takeIf { it.isNotBlank() }?.let {
                    InsightLine(label = "本周判断", text = it, maxLines = 2)
                }
                connectionLine.takeIf { it.isNotBlank() }?.let {
                    InsightLine(label = "值得串起来", text = it, maxLines = 2)
                }
            }
        }
        weeklyStatsLine.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
        followedDirections.take(2).forEach { summary ->
            ThreadRow(
                thread = summary.thread,
                showFocus = true,
                onOpenThread = onOpenThread,
            )
        }
        if (followedDirections.isEmpty()) {
            threads.take(2).forEach { thread ->
                ThreadRow(
                    thread = thread,
                    showFocus = true,
                    onOpenThread = onOpenThread,
                )
            }
        }
        val remainingCount = (followedDirections.size + threads.size - 2).coerceAtLeast(0)
        if (remainingCount > 0) {
            Text(
                text = "还有 $remainingCount 条方向或线索可以继续看",
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun DirectionCard(
    weeklyItems: List<WeeklyReviewItem>,
    weeklySource: DailyBriefSource,
    weeklyStatsLine: String,
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    highlightReview: Boolean,
    highlightConnection: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    PanelCard {
        SectionHeader(
            title = "知识沉淀",
            headline = when {
                followedDirections.isNotEmpty() -> "先看最近长出来的判断，再决定往哪推"
                suggestions.isNotEmpty() -> "先看最值得继续追的一个点"
                threads.isNotEmpty() -> "先把分散的线索收成一条可经营的主线"
                else -> null
            },
        )
        KnowledgePayoffCard(
            followedDirections = followedDirections,
            suggestions = suggestions,
            source = fusionSource,
        )
        ConnectionCard(
            followedDirections = followedDirections,
            threads = threads,
            suggestions = suggestions,
            source = fusionSource,
            highlighted = highlightConnection,
            onOpenThread = onOpenThread,
            onOpenNote = onOpenNote,
            onCreateCapture = onCreateCapture,
        )
        WeeklyReviewCard(
            items = weeklyItems,
            source = weeklySource,
            statsLine = weeklyStatsLine,
            highlighted = highlightReview,
        )
    }
}

@Composable
private fun KnowledgePayoffCard(
    followedDirections: List<FollowedDirectionSummary>,
    suggestions: List<String>,
    source: DailyBriefSource,
) {
    val recentLine = followedDirections.firstNonBlankValue {
        it.wikiConclusionLine.ifBlank { it.assetSummary }
    }
    val trustedLine = followedDirections.firstNonBlankValue {
        it.wikiValidatedPoint.ifBlank { it.wikiVerifiedPoint }
    }
    val gapLine = followedDirections.firstNonBlankValue {
        it.wikiOpenQuestion
            .ifBlank { it.wikiMaintenanceLine }
            .ifBlank { it.blocker }
    }
    val ideaLine = suggestions.firstOrNull().orEmpty()

    if (
        recentLine.isBlank() &&
        trustedLine.isBlank() &&
        gapLine.isBlank() &&
        ideaLine.isBlank()
    ) {
        return
    }

    InsightBlock(
        sourceLabel = if (source == DailyBriefSource.AI) "AI 沉淀" else "最近沉淀",
        tone = InsightTone.Primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        recentLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "已经沉淀", text = it, emphasize = true, maxLines = 2)
        }
        trustedLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "最可靠的一点", text = it, maxLines = 2)
        }
        gapLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "还待补", text = it, maxLines = 2)
        }
        ideaLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "值得延展", text = it, maxLines = 2)
        }
    }
}

@Composable
private fun ConnectionCard(
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    source: DailyBriefSource,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (followedDirections.isNotEmpty()) {
                Text(
                    text = "正在经营的方向",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                followedDirections.forEach { summary ->
                    FollowedDirectionRow(
                        summary = summary,
                        onOpenThread = onOpenThread,
                        onOpenNote = onOpenNote,
                        onCreateCapture = onCreateCapture,
                    )
                }
            }
            if (threads.isNotEmpty() || suggestions.isNotEmpty()) {
                InsightBlock(
                    sourceLabel = if (source == DailyBriefSource.AI) "AI 串联" else "串联线索",
                    tone = InsightTone.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    threads.firstOrNull()?.let { thread ->
                        InsightLine(
                            label = "值得串起来",
                            text = "${thread.title} · ${thread.summary}",
                            maxLines = 2,
                        )
                    }
                    suggestions.firstOrNull()?.let { suggestion ->
                        InsightLine(
                            label = "值得延展",
                            text = suggestion,
                            maxLines = 2,
                        )
                    }
                }
                if (threads.size > 1) {
                    Text(
                        text = "还有 ${threads.size - 1} 条线索可以继续串起来",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                } else if (suggestions.size > 1) {
                    Text(
                        text = "还有 ${suggestions.size - 1} 条延展想法",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                }
            }
            if (followedDirections.isEmpty() && threads.isEmpty() && suggestions.isEmpty()) {
                Text(
                    text = "记录再多一点，这里就会帮你把反复出现的方向串起来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun FollowedDirectionRow(
    summary: FollowedDirectionSummary,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable { onOpenThread(summary.thread.key) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val trustChip = when {
                summary.wikiValidatedPoint.isNotBlank() -> "已验证"
                summary.wikiVerifiedPoint.isNotBlank() -> "已查证"
                summary.wikiHypothesisPoint.isNotBlank() -> "待验证"
                else -> "方向中"
            }
            val recentKnowledge = summary.wikiConclusionLine
                .ifBlank { summary.assetSummary }
                .ifBlank { summary.summary }
            val trustedResult = summary.wikiValidatedPoint
                .ifBlank { summary.wikiVerifiedPoint }
                .ifBlank { summary.wikiTrustLine }
            val gapLine = summary.wikiOpenQuestion
                .ifBlank { summary.wikiMaintenanceLine }
                .ifBlank { summary.blocker }
            val nextLine = summary.validationStep
                .ifBlank { summary.nextStep }
                .ifBlank { summary.wikiNextShiftLine }
            Text(
                text = summary.thread.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InsightChip(text = summary.stage.label, tone = InsightTone.Primary)
                InsightChip(text = trustChip, tone = InsightTone.Neutral)
                InsightChip(text = summary.dominantHorizon.label, tone = InsightTone.Neutral)
            }
            summary.rhythmLine.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.summary.isNotBlank() || summary.nextStep.isNotBlank() || summary.validationStep.isNotBlank()) {
                InsightBlock(
                    sourceLabel = if (summary.source == DailyBriefSource.AI) "AI 洞察" else "当前判断",
                    tone = InsightTone.Primary,
                ) {
                    recentKnowledge
                        .takeIf { it.isNotBlank() }
                        ?.let { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMain,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    summary.whyNow
                        .takeIf { it.isNotBlank() }
                        ?.let { reason ->
                            InsightLine(label = "为什么现在", text = reason, maxLines = 2)
                        }
                    nextLine
                        .takeIf { it.isNotBlank() }
                        ?.let { action ->
                            InsightLine(label = "下一步", text = action, emphasize = true, maxLines = 2)
                        }
                }
            }
            if (trustedResult.isNotBlank() || gapLine.isNotBlank()) {
                InsightBlock(
                    sourceLabel = "知识层",
                    tone = InsightTone.Neutral,
                ) {
                    trustedResult
                        .takeIf { it.isNotBlank() }
                        ?.let { trust ->
                            InsightLine(label = trustChip, text = trust, maxLines = 2)
                        }
                    gapLine
                        .takeIf { it.isNotBlank() }
                        ?.let { gap ->
                            InsightLine(label = "当前缺口", text = gap, maxLines = 2)
                        }
                    summary.whyNow
                        .takeIf { it.isNotBlank() }
                        ?.let { reason ->
                            InsightLine(label = "为什么现在", text = reason, maxLines = 2)
                        }
                    summary.lastProgressLine
                        .takeIf { it.isNotBlank() }
                        ?.let { progress ->
                            InsightLine(label = "最近推进", text = progress, maxLines = 2)
                        }
                }
            }
            if (
                summary.outsideAngle.isNotBlank() ||
                summary.opportunityGap.isNotBlank() ||
                summary.contrarianQuestion.isNotBlank() ||
                summary.externalHypothesis.isNotBlank()
            ) {
                InsightBlock(
                    sourceLabel = if (summary.source == DailyBriefSource.AI) "AI 外部线索" else "外部线索",
                    tone = InsightTone.Neutral,
                ) {
                    summary.outsideAngle.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    (summary.externalHypothesis.takeIf { it.isNotBlank() }
                        ?: summary.contrarianQuestion.takeIf { it.isNotBlank() })
                        ?.let {
                            InsightLine(label = "外部假设", text = it, maxLines = 2)
                        }
                    summary.opportunityGap.takeIf { it.isNotBlank() }?.let {
                        InsightLine(label = "机会缺口", text = it, maxLines = 2)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "继续推进",
                    onClick = {
                        summary.focusNoteId?.let(onOpenNote) ?: onOpenThread(summary.thread.key)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (summary.validationStep.isNotBlank()) {
                    GhostActionButton(
                        text = "记验证",
                        onClick = { onCreateCapture(summary.toResearchCaptureSeed()) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (summary.wikiMaintenanceLine.isNotBlank()) {
                    GhostActionButton(
                        text = "补材料",
                        onClick = { onCreateCapture(summary.toMaintenanceCaptureSeed()) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (summary.assetNoteId != null) {
                    GhostActionButton(
                        text = "看沉淀",
                        onClick = { onOpenNote(summary.assetNoteId) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GhostActionButton(
                        text = "看方向",
                        onClick = { onOpenThread(summary.thread.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun FollowedDirectionSummary.toResearchCaptureSeed(): CaptureSeed {
    val cleanTitle = thread.title.removePrefix("#").trim()
    val initialFolderKey = thread.key
        .takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.ifBlank { null }
    val initialTags = thread.key
        .takeIf { it.startsWith("tag:") }
        ?.removePrefix("tag:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()
    return CaptureSeed(
        initialTopic = "$cleanTitle · 验证动作",
        initialContent = buildString {
            appendLine("围绕「$cleanTitle」补一条验证记录：")
            appendLine("- 当前阶段：${stage.label} · ${dominantHorizon.label}")
            appendLine("- 为什么现在接：$whyNow")
            lastProgressLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 最近推进：$it")
            }
            nextStep.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前最小动作：$it")
            }
            nextCheckInLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 下次检查：$it")
            }
            outsideAngle.takeIf { it.isNotBlank() }?.let {
                appendLine("- AI 外部线索：$it")
            }
            opportunityGap.takeIf { it.isNotBlank() }?.let {
                appendLine("- 机会缺口：$it")
            }
            contrarianQuestion.takeIf { it.isNotBlank() }?.let {
                appendLine("- 反问：$it")
            }
            appendLine("- 先验证：$validationStep")
            validationReason.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么现在做：$it")
            }
            postValidationAction.takeIf { it.isNotBlank() }?.let {
                appendLine("- 如果成立，下一步：$it")
            }
            appendLine("- 我准备怎么验证：")
            appendLine("- 看什么结果算成立：")
        },
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
        initialKnowledgeTrust = KnowledgeTrust.HYPOTHESIS,
    )
}

private fun FollowedDirectionSummary.toMaintenanceCaptureSeed(): CaptureSeed =
    buildKnowledgeMaintenanceCaptureSeed(
        KnowledgeMaintenanceSeedContext(
            title = thread.title,
            threadKey = thread.key,
            stageLabel = stage.label,
            horizonLabel = dominantHorizon.label,
            whyNow = whyNow,
            nextStep = nextStep,
            validationStep = validationStep,
            conclusionLine = wikiConclusionLine,
            nextShiftLine = wikiNextShiftLine,
            groundingLine = wikiGroundingLine,
            maintenanceLine = wikiMaintenanceLine,
            maintenanceTargetLine = wikiMaintenanceTargetLine,
            maintenanceSourceLine = wikiMaintenanceSourceLine,
            maintenanceDimensionLine = wikiMaintenanceDimensionLine,
            maintenanceFocusLine = wikiMaintenanceFocusLine,
        ),
    )

private fun <T> Iterable<T>.firstNonBlankValue(
    selector: (T) -> String,
): String = asSequence()
    .map(selector)
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

@Composable
private fun ThreadRow(
    thread: ThemeThread,
    showFocus: Boolean,
    onOpenThread: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable { onOpenThread(thread.key) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${thread.title} · ${thread.noteCount} 条",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            if (showFocus && thread.focusLine.isNotBlank()) {
                Text(
                    text = thread.focusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = thread.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeeklyReviewCard(
    items: List<WeeklyReviewItem>,
    source: DailyBriefSource,
    statsLine: String,
    highlighted: Boolean,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "本周判断",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
                maxLines = 1,
            )
            if (statsLine.isNotBlank()) {
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                    maxLines = 1,
                )
            }
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 洞察")
            }
            if (items.isEmpty()) {
                Text(
                    text = "这一周的线索还不够多，再记几条更具体的内容会更有价值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayNoteCard(
    title: String,
    note: NoteEntity?,
    emptyText: String,
    accent: Color,
    nextActionText: String,
    nextActionSource: DailyBriefSource,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.45f) else BorderSoft,
        ),
        modifier = modifier.then(
            if (note != null) {
                Modifier.clickable { onOpenNote(note.id) }
            } else {
                Modifier
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
            )
            if (note == null) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                Text(
                    text = note.topic.ifBlank { "未命名记录" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nextActionText.isNotBlank()) {
                    InsightBlock(
                        sourceLabel = if (nextActionSource == DailyBriefSource.AI) "下一步 · AI 洞察" else "下一步",
                        tone = InsightTone.Primary,
                    ) {
                        Text(
                            text = nextActionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = note.content.asTodayPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${note.horizon.label} · ${note.status.label} · ${TimeFormatter.compact(note.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExplorationPromptCard(
    prompts: List<String>,
    source: DailyBriefSource,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今天值得想",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
                maxLines = 1,
            )
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 洞察")
            }
            if (prompts.isEmpty()) {
                Text(
                    text = "继续记录更具体的想法，AI 会从中提炼出更有启发性的探索方向。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                prompts.take(2).forEach { prompt ->
                    Text(
                        text = "• $prompt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
                    )
                }
            }
        }
    }
}

@Composable
private fun GentleReconnectCard(
    note: NoteEntity,
    reason: String,
    bridge: String,
    nextStep: String,
    source: DailyBriefSource,
    highlighted: Boolean,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
        modifier = Modifier.clickable { onOpenNote(note.id) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "重新接上",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
            )
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 提醒")
            }
            Text(
                text = note.topic.ifBlank { "未命名记录" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${note.horizon.label} · ${note.status.label}",
                style = MaterialTheme.typography.labelSmall,
                color = if (source == DailyBriefSource.AI) MaterialTheme.colorScheme.primary else TextSoft,
            )
            if (reason.isNotBlank()) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
            if (bridge.isNotBlank()) {
                Text(
                    text = bridge,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
            if (nextStep.isNotBlank()) {
                InsightBlock(
                    sourceLabel = "先做这一步",
                    tone = InsightTone.Primary,
                ) {
                    Text(
                        text = nextStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                    )
                }
            }
            Text(
                text = TimeFormatter.compact(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
    }
}


private fun String.asTodayPreview(): String =
    replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
