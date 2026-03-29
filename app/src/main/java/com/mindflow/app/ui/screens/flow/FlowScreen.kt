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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter

@Composable
fun FlowRoute(
    noteRepository: NoteRepository,
    dailyBriefPlanner: DailyBriefPlanner,
    nextActionPlanner: NextActionPlanner,
    weeklyReviewPlanner: WeeklyReviewPlanner,
    fusionSuggestionPlanner: FusionSuggestionPlanner,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: FlowViewModel = viewModel(
        factory = FlowViewModel.factory(
            noteRepository = noteRepository,
            dailyBriefPlanner = dailyBriefPlanner,
            nextActionPlanner = nextActionPlanner,
            weeklyReviewPlanner = weeklyReviewPlanner,
            fusionSuggestionPlanner = fusionSuggestionPlanner,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FlowScreen(
        uiState = uiState,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
    )
}

@Composable
private fun FlowScreen(
    uiState: FlowUiState,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
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
                            text = "先推进一件最值得做的事，再看一个更长的方向。",
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
                            modifier = Modifier.fillMaxWidth(),
                            onOpenNote = onOpenNote,
                        )
                        ExplorationPromptCard(
                            prompts = uiState.explorationPrompts,
                            source = uiState.explorationSource,
                        )
                    }
                }

                item {
                    DirectionCard(
                        weeklyLines = uiState.weeklyReviewLines,
                        weeklySource = uiState.weeklyReviewSource,
                        threads = uiState.themeThreads,
                        suggestions = uiState.fusionSuggestions,
                        fusionSource = uiState.fusionSource,
                        onOpenThread = onOpenThread,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionCard(
    weeklyLines: List<String>,
    weeklySource: DailyBriefSource,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    onOpenThread: (String) -> Unit,
) {
    PanelCard {
        SectionHeader(
            title = "方向",
            headline = when {
                threads.isNotEmpty() -> "${threads.size} 条主题"
                suggestions.isNotEmpty() -> "有新的建议"
                else -> null
            },
        )
        WeeklyReviewCard(
            lines = weeklyLines,
            source = weeklySource,
        )
        ConnectionCard(
            threads = threads,
            suggestions = suggestions,
            source = fusionSource,
            onOpenThread = onOpenThread,
        )
    }
}

@Composable
private fun ConnectionCard(
    threads: List<ThemeThread>,
    suggestions: List<String>,
    source: DailyBriefSource,
    onOpenThread: (String) -> Unit,
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
                text = "连接",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
            )
            if (threads.isNotEmpty()) {
                Text(
                    text = "主题线程",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                threads.forEach { thread ->
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
private fun WeeklyReviewCard(
    lines: List<String>,
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "本周回看",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
                maxLines = 1,
            )
            if (source == DailyBriefSource.AI) {
                Text(
                    text = "AI Review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (lines.isEmpty()) {
                Text(
                    text = "这一周的线索还不够多，再记几条更具体的内容会更有价值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                lines.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
                    )
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
    modifier: Modifier = Modifier,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
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


private fun String.asTodayPreview(): String =
    replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
