package com.mindflow.app.ui.screens.folder

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.MetricTile
import com.mindflow.app.ui.components.NeonProgress
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.navigation.MindFlowDestinations
import com.mindflow.app.ui.theme.AccentBlue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

internal const val FolderDeleteUsesDeferredSnackbarUndo = false

data class FolderUiState(
    val folderKey: String,
    val folderName: String,
    val accentHex: String,
    val notes: List<NoteEntity> = emptyList(),
    val totalCount: Int = 0,
    val ideaCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
)

sealed interface FolderEvent {
    data class Message(val text: String) : FolderEvent
}

class FolderViewModel(
    private val noteRepository: NoteRepository,
    private val folderKey: String,
) : ViewModel() {
    val uiState: StateFlow<FolderUiState> = noteRepository.observeAllNotes()
        .map { notes ->
            val filtered = notes
                .filter { !it.isArchived }
                .filter { note ->
                    if (folderKey == MindFlowDestinations.UNCATEGORIZED_FOLDER) {
                        note.folderKey == null
                    } else {
                        note.folderKey == folderKey
                    }
                }
            val folder = MindFolderCatalog.fromKey(folderKey)
            val folderName = if (folderKey == MindFlowDestinations.UNCATEGORIZED_FOLDER) "未分类" else (folder?.name ?: "文件夹")
            val accentHex = folder?.colorHex ?: "#64748B"
            FolderUiState(
                folderKey = folderKey,
                folderName = folderName,
                accentHex = accentHex,
                notes = filtered,
                totalCount = filtered.size,
                ideaCount = filtered.count { it.status == NoteStatus.IDEA },
                inProgressCount = filtered.count { it.status == NoteStatus.IN_PROGRESS },
                doneCount = filtered.count { it.status == NoteStatus.DONE },
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FolderUiState(
                folderKey = folderKey,
                folderName = if (folderKey == MindFlowDestinations.UNCATEGORIZED_FOLDER) "未分类" else (MindFolderCatalog.fromKey(folderKey)?.name ?: "文件夹"),
                accentHex = MindFolderCatalog.fromKey(folderKey)?.colorHex ?: "#64748B",
            ),
        )

    private val _events = MutableSharedFlow<FolderEvent>()
    val events = _events.asSharedFlow()

    fun archiveNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.setArchived(noteId, archived = true)
            _events.emit(FolderEvent.Message("已归档"))
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(FolderEvent.Message("已删除记录"))
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            folderKey: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FolderViewModel(noteRepository, folderKey) }
        }
    }
}

@Composable
fun FolderRoute(
    noteRepository: NoteRepository,
    folderKey: String,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: FolderViewModel = viewModel(
        key = "folder-$folderKey",
        factory = FolderViewModel.factory(
            noteRepository = noteRepository,
            folderKey = folderKey,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareCardGenerator = remember(context) { NoteShareCardGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingShareNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FolderEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
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

    FolderScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onArchiveNote = viewModel::archiveNote,
        onDeleteNote = { note -> viewModel.deleteNote(note.id) },
        onShareNote = { pendingShareNote = it },
    )
}

@Composable
private fun FolderScreen(
    uiState: FolderUiState,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onArchiveNote: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    val accent = Color(AndroidColor.parseColor(uiState.accentHex))
    val progress = if (uiState.totalCount == 0) 0f else uiState.doneCount.toFloat() / uiState.totalCount.toFloat()
    val percent = (progress * 100).toInt()
    val visibleNotes = uiState.notes

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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconPillButton(
                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                onClick = onBack,
                                accent = accent,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = uiState.folderName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${visibleNotes.size} 条记录 · 按同类内容整理浏览",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "当前进展",
                            headline = "$percent% 已实现",
                        )
                        NeonProgress(
                            progress = progress,
                            startColor = accent,
                            endColor = accent,
                        )
                        GridTwo {
                            MetricTile(
                                label = "想法",
                                value = uiState.ideaCount.toString(),
                                modifier = Modifier.weight(1f),
                                accent = noteStatusAccent(NoteStatus.IDEA),
                            )
                            MetricTile(
                                label = "进行中",
                                value = uiState.inProgressCount.toString(),
                                modifier = Modifier.weight(1f),
                                accent = noteStatusAccent(NoteStatus.IN_PROGRESS),
                            )
                        }
                        MetricTile(
                            label = "已实现",
                            value = uiState.doneCount.toString(),
                            accent = noteStatusAccent(NoteStatus.DONE),
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "记录",
                        headline = "${visibleNotes.size} 条",
                    )
                }

                if (visibleNotes.isEmpty()) {
                    item {
                        EmptyState(
                            title = "这个文件夹还没有内容",
                            description = "等新记录被归到这里，这里就会慢慢长起来。",
                        )
                    }
                } else {
                    items(visibleNotes, key = { it.id }) { note ->
                        SwipeRevealNoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onToggleArchive = { onArchiveNote(note.id) },
                            onShare = { onShareNote(note) },
                            onDelete = { onDeleteNote(note) },
                        )
                    }
                }
            }
        }
    }
}
