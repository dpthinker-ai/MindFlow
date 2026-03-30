package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.navigation.FlowFocus
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
    initialFocus: FlowFocus? = null,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
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
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FlowScreen(
        uiState = uiState,
        focus = initialFocus,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
    )
}

@Composable
private fun FlowScreen(
    uiState: FlowUiState,
    focus: FlowFocus?,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val highlightToday = focus == FlowFocus.TODAY
    val highlightReconnect = focus == FlowFocus.RECONNECT
    val highlightReview = focus == FlowFocus.REVIEW
    val highlightDirection = focus == FlowFocus.DIRECTION
    val subtitle = remember(focus) {
        when (focus) {
            FlowFocus.TODAY -> "从提醒回来，先把今天最值得做的一步接上。"
            FlowFocus.RECONNECT -> "从提醒回来，先把那条该重新接上的想法接回来。"
            FlowFocus.REVIEW -> "从提醒回来，先看这周最值得留下来的判断。"
            FlowFocus.DIRECTION -> "从提醒回来，先看这条更长的方向。"
            null -> "先推进一件最值得做的事，再看一个更长的方向。"
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
                            title = "今天",
                            headline = if (uiState.todayCount > 0) "今天已记 ${uiState.todayCount} 条" else "今天还没落笔",
                        )
                        TodayNoteCard(
                            title = "积极推进",
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
                        followedThreads = uiState.followedThreads,
                        threads = uiState.themeThreads,
                        suggestions = uiState.fusionSuggestions,
                        fusionSource = uiState.fusionSource,
                        highlightReview = highlightReview,
                        highlightConnection = highlightDirection,
                        onOpenThread = onOpenThread,
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
    followedThreads: List<ThemeThread>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    highlightReview: Boolean,
    highlightConnection: Boolean,
    onOpenThread: (String) -> Unit,
) {
    PanelCard {
        SectionHeader(
            title = "方向",
            headline = when {
                followedThreads.isNotEmpty() -> "已关注 ${followedThreads.size} 条"
                threads.isNotEmpty() -> "${threads.size} 条主题"
                suggestions.isNotEmpty() -> "有新的建议"
                else -> null
            },
        )
        WeeklyReviewCard(
            items = weeklyItems,
            source = weeklySource,
            statsLine = weeklyStatsLine,
            highlighted = highlightReview,
        )
        ConnectionCard(
            followedThreads = followedThreads,
            threads = threads,
            suggestions = suggestions,
            source = fusionSource,
            highlighted = highlightConnection,
            onOpenThread = onOpenThread,
        )
    }
}

@Composable
private fun ConnectionCard(
    followedThreads: List<ThemeThread>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    source: DailyBriefSource,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
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
            Text(
                text = "连接",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
            )
            if (followedThreads.isNotEmpty()) {
                Text(
                    text = "关注方向",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                followedThreads.forEach { thread ->
                    ThreadRow(thread = thread, onOpenThread = onOpenThread)
                }
            }
            if (threads.isNotEmpty()) {
                Text(
                    text = if (followedThreads.isNotEmpty()) "更多线索" else "主题线程",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                threads.forEach { thread ->
                    ThreadRow(thread = thread, onOpenThread = onOpenThread)
                }
            }
            if (suggestions.isNotEmpty()) {
                Text(
                    text = if (source == DailyBriefSource.AI) "融合建议 · AI" else "融合建议",
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
            if (threads.isEmpty() && suggestions.isEmpty()) {
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
private fun ThreadRow(
    thread: ThemeThread,
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
                text = "本周回看",
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
                    text = "AI Review",
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
                                text = if (nextActionSource == DailyBriefSource.AI) "下一步动作 · AI" else "下一步动作",
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
                    text = "${note.status.label} · ${TimeFormatter.compact(note.updatedAt)}",
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
                text = "探索方向",
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
                text = if (source == DailyBriefSource.AI) "重新接上 · AI" else "重新接上",
                style = MaterialTheme.typography.labelLarge,
                color = if (source == DailyBriefSource.AI) MaterialTheme.colorScheme.primary else TextSoft,
            )
            Text(
                text = note.topic.ifBlank { "未命名记录" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
