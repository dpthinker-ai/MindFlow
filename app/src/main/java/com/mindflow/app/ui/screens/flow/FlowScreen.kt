package com.mindflow.app.ui.screens.flow

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
import androidx.compose.runtime.remember
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
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.FlowFocus
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter

@Composable
fun FlowRoute(
    noteRepository: NoteRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    dailyBriefPlanner: DailyBriefPlanner,
    nextActionPlanner: NextActionPlanner,
    weeklyReviewPlanner: WeeklyReviewPlanner,
    fusionSuggestionPlanner: FusionSuggestionPlanner,
    staleReconnectPlanner: StaleReconnectPlanner,
    threadExecutionPlanner: ThreadExecutionPlanner,
    externalResearchPlanner: ExternalResearchPlanner,
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
            staleReconnectPlanner = staleReconnectPlanner,
            threadExecutionPlanner = threadExecutionPlanner,
            externalResearchPlanner = externalResearchPlanner,
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
    val subtitle = remember(focus) {
        when (focus) {
            FlowFocus.TODAY -> "从提醒回来，先把今天最值得推进的一步接上。"
            FlowFocus.RECONNECT -> "从提醒回来，先把那条该重新接上的想法接回来。"
            FlowFocus.REVIEW -> "从提醒回来，先看这周最值得留下来的判断。"
            FlowFocus.DIRECTION -> "从提醒回来，先看这条更长的方向。"
            null -> "先推进今天最重要的一步，再看正在形成的方向。"
        }
    }

    LaunchedEffect(focus) {
        when (focus) {
            FlowFocus.TODAY,
            FlowFocus.RECONNECT,
            null -> listState.scrollToItem(0)
            FlowFocus.REVIEW,
            FlowFocus.DIRECTION -> listState.scrollToItem(2)
        }
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
                    PanelCard {
                        SectionHeader(
                            title = "今日聚焦",
                            headline = if (uiState.todayCount > 0) "今天已记 ${uiState.todayCount} 条" else "今天最值得先看这三件事",
                        )
                        TodayNoteCard(
                            title = "先推进",
                            note = uiState.continueNote,
                            emptyText = "先从一条真正想做成的事开始，不必面面俱到。",
                            accent = noteStatusAccent(uiState.continueNote?.status ?: NoteStatus.IN_PROGRESS),
                            nextActionText = uiState.nextActionText,
                            nextActionSource = uiState.nextActionSource,
                            highlighted = highlightToday,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenNote = onOpenNote,
                        )
                        if (uiState.staleNote != null) {
                            GentleReconnectCard(
                                note = uiState.staleNote,
                                reason = uiState.staleReason,
                                bridge = uiState.staleBridge,
                                nextStep = uiState.staleNextStep,
                                source = uiState.staleSource,
                                highlighted = highlightReconnect,
                                onOpenNote = onOpenNote,
                            )
                        }
                        ExplorationPromptCard(
                            prompts = uiState.explorationPrompts,
                            source = uiState.explorationSource,
                        )
                    }
                }

                item {
                    DirectionCard(
                        weeklyItems = uiState.weeklyReviewItems,
                        weeklySource = uiState.weeklyReviewSource,
                        weeklyStatsLine = uiState.weeklyReviewStatsLine,
                        followedDirections = uiState.followedDirections,
                        threads = uiState.themeThreads,
                        suggestions = uiState.fusionSuggestions,
                        fusionSource = uiState.fusionSource,
                        highlightReview = highlightReview,
                        highlightConnection = highlightDirection,
                        onOpenThread = onOpenThread,
                        onOpenNote = onOpenNote,
                        onCreateCapture = onCreateCapture,
                    )
                }
            }
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
            title = "方向判断",
            headline = when {
                followedDirections.isNotEmpty() -> "持续经营 ${followedDirections.size} 条方向"
                threads.isNotEmpty() -> "${threads.size} 条主题"
                suggestions.isNotEmpty() -> "有新的建议"
                else -> null
            },
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
            if (threads.isNotEmpty()) {
                Text(
                    text = if (followedDirections.isNotEmpty()) "值得串起来的线索" else "主题线程",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                threads.forEach { thread ->
                    ThreadRow(
                        thread = thread,
                        showFocus = false,
                        onOpenThread = onOpenThread,
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Text(
                    text = if (source == DailyBriefSource.AI) "AI 融合建议" else "融合建议",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (source == DailyBriefSource.AI) MaterialTheme.colorScheme.primary else TextSoft,
                )
                suggestions.forEach { suggestion ->
                    Text(
                        text = "• $suggestion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
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
            Text(
                text = summary.thread.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            Text(
                text = "${summary.stage.label} · ${summary.dominantHorizon.label} · ${summary.thread.noteCount} 条",
                style = MaterialTheme.typography.labelSmall,
                color = AccentBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.rhythmLine.isNotBlank()) {
                Text(
                    text = summary.rhythmLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.thread.focusLine.isNotBlank()) {
                Text(
                    text = summary.thread.focusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            summary.assetSummary
                .takeIf { it.isNotBlank() }
                ?.let { asset ->
                    Text(
                        text = "已沉淀：$asset",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            if (summary.summary.isNotBlank() || summary.nextStep.isNotBlank() || summary.validationStep.isNotBlank()) {
                Surface(
                    color = AccentBlue.copy(alpha = 0.08f),
                    shape = CardShape,
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = if (summary.source == DailyBriefSource.AI) "AI 洞察" else "当前判断",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        summary.summary
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
                                Text(
                                    text = "为什么现在：$reason",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        summary.nextStep
                            .takeIf { it.isNotBlank() }
                            ?.let { action ->
                                Text(
                                    text = "先做：$action",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMain,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        summary.validationStep
                            .takeIf { it.isNotBlank() }
                            ?.let { validation ->
                                Text(
                                    text = "先验证：$validation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMain,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }
            }
            if (
                summary.outsideAngle.isNotBlank() ||
                summary.opportunityGap.isNotBlank() ||
                summary.contrarianQuestion.isNotBlank() ||
                summary.externalHypothesis.isNotBlank()
            ) {
                Surface(
                    color = WhiteGlass.copy(alpha = 0.78f),
                    shape = CardShape,
                    border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.8f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = if (summary.source == DailyBriefSource.AI) "AI 外部视角" else "外部视角",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                        summary.outsideAngle.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        summary.opportunityGap.takeIf { it.isNotBlank() }?.let {
                            Text(text = "机会缺口：$it", style = MaterialTheme.typography.bodySmall, color = TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        (summary.externalHypothesis.takeIf { it.isNotBlank() }
                            ?: summary.contrarianQuestion.takeIf { it.isNotBlank() })
                            ?.let {
                            Text(text = "外部假设：$it", style = MaterialTheme.typography.bodySmall, color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            nextStep.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前最小动作：$it")
            }
            outsideAngle.takeIf { it.isNotBlank() }?.let {
                appendLine("- AI 外部视角：$it")
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
    )
}

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
                Text(
                    text = "AI 洞察",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
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
                    Surface(
                        color = accent.copy(alpha = 0.08f),
                        shape = CardShape,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (nextActionSource == DailyBriefSource.AI) "下一步 · AI 洞察" else "下一步",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                maxLines = 1,
                            )
                            Text(
                                text = nextActionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMain,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                Text(
                    text = "AI 洞察",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
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
                Text(
                    text = "AI 提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = CardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "先做这一步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = nextStep,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                        )
                    }
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
