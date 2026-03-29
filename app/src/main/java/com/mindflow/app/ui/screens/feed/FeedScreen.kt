package com.mindflow.app.ui.screens.feed

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.MetricTile
import com.mindflow.app.ui.components.NeonProgress
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.AccentSuccess
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FeedRoute(
    noteRepository: NoteRepository,
    backgroundFolderOrganizer: BackgroundFolderOrganizer,
    onCreateNote: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenStatusFilter: (NoteStatus?, Boolean) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.factory(
            noteRepository = noteRepository,
            backgroundFolderOrganizer = backgroundFolderOrganizer,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareCardGenerator = remember(context) { NoteShareCardGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingShareNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FeedEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    pendingShareNote?.let { note ->
        ShareStyleDialog(
            onDismiss = { pendingShareNote = null },
            onLight = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.LIGHT) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
            onDark = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.DARK) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
        )
    }

    FeedScreen(
        uiState = uiState,
        onCreateNote = onCreateNote,
        onOpenFolder = onOpenFolder,
        onOpenStatusFilter = onOpenStatusFilter,
        onOpenNote = onOpenNote,
        onArchiveNote = viewModel::archiveNote,
        onDeleteNote = viewModel::deleteNote,
        onClassifyPendingFolders = viewModel::classifyPendingFolders,
        onShareNote = { pendingShareNote = it },
    )
}

@Composable
private fun FeedScreen(
    uiState: FeedUiState,
    onCreateNote: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenStatusFilter: (NoteStatus?, Boolean) -> Unit,
    onOpenNote: (Long) -> Unit,
    onArchiveNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onClassifyPendingFolders: () -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    val doneCount = uiState.doneCount
    val totalCount = uiState.totalCount
    val ideaCount = uiState.ideaCount
    val inProgressCount = uiState.inProgressCount
    val archivedCount = uiState.archivedCount
    val completionProgress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
    val completionPercent = (completionProgress * 100).toInt()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "灵感易逝，及时行动",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "想到就记，记下来就做",
                            headline = "叉手立办",
                        )
                        Text(
                            text = "别等好的时机，先把念头抓住，再把它一点点推进成真正发生的事。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SectionHeader(
                            title = "总览",
                            headline = "$completionPercent% 已实现",
                        )
                        NeonProgress(
                            progress = completionProgress,
                            startColor = AccentSuccess,
                            endColor = AccentSuccess,
                        )
                        GridTwo {
                            MetricTile(
                                label = "想法",
                                value = ideaCount.toString(),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenStatusFilter(NoteStatus.IDEA, false) },
                                accent = noteStatusAccent(NoteStatus.IDEA),
                            )
                            MetricTile(
                                label = "进行中",
                                value = inProgressCount.toString(),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenStatusFilter(NoteStatus.IN_PROGRESS, false) },
                                accent = noteStatusAccent(NoteStatus.IN_PROGRESS),
                            )
                        }
                        GridTwo {
                            MetricTile(
                                label = "已实现",
                                value = doneCount.toString(),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenStatusFilter(NoteStatus.DONE, false) },
                                accent = noteStatusAccent(NoteStatus.DONE),
                            )
                            MetricTile(
                                label = "已归档",
                                value = archivedCount.toString(),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenStatusFilter(null, true) },
                                accent = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        ActionButton(
                            text = "新建记录",
                            onClick = onCreateNote,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.EditNote,
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "记录",
                        headline = "${uiState.notes.size} 条",
                    )
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有记录",
                            description = "先写下一条想法，让列表慢慢长出来。",
                        )
                    }
                } else {
                    items(uiState.notes, key = { it.id }) { note ->
                        SwipeRevealNoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onToggleArchive = { onArchiveNote(note.id) },
                            onShare = { onShareNote(note) },
                            onDelete = { onDeleteNote(note.id) },
                        )
                    }
                }
            }
        }
    }
}
