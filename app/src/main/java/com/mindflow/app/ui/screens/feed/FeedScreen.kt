package com.mindflow.app.ui.screens.feed

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.MindFlowUiTestTags
import com.mindflow.app.ui.components.PanelShape
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.AccentTeal
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.PanelBlue
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

internal val RecordSearchMinHeight = 48.dp
internal val RecordQuickCaptureVerticalPadding = 10.dp
internal val RecordFilterChipVerticalPadding = 6.dp
internal val RecordTimeBankBadgeVerticalPadding = 5.dp
internal const val RecordSearchUsesStandaloneFilterButton = false
internal const val RecordSearchUsesOutlinedTextField = false
internal const val RecordSearchPlaceholder = "搜索记录、标签、链接、任务、语音"

@Composable
fun FeedRoute(
    noteRepository: NoteRepository,
    timeBankSettingsRepository: TimeBankSettingsRepository,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenStatusFilter: (NoteStatus?, Boolean) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.factory(
            noteRepository = noteRepository,
            timeBankSettingsRepository = timeBankSettingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareCardGenerator = remember(context) { NoteShareCardGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingShareNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingDeletedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FeedEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
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
        hiddenNoteIds = pendingDeletedIds,
        snackbarHostState = snackbarHostState,
        onCreateCapture = onCreateCapture,
        onOpenStatusFilter = onOpenStatusFilter,
        onOpenNote = onOpenNote,
        onArchiveNote = viewModel::archiveNote,
        onDeleteNote = { note ->
            scope.launch {
                pendingDeletedIds = pendingDeletedIds + note.id
                val result = snackbarHostState.showSnackbar(
                    message = "已移除「${note.topic.ifBlank { "未命名想法" }}」",
                    actionLabel = "撤销",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    pendingDeletedIds = pendingDeletedIds - note.id
                } else {
                    viewModel.deleteNote(note.id)
                }
            }
        },
        onShareNote = { pendingShareNote = it },
    )
}

@Composable
private fun FeedScreen(
    uiState: FeedUiState,
    hiddenNoteIds: Set<Long>,
    snackbarHostState: SnackbarHostState,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenStatusFilter: (NoteStatus?, Boolean) -> Unit,
    onOpenNote: (Long) -> Unit,
    onArchiveNote: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FeedQuickFilter.ALL) }
    val visibleNotes = remember(uiState.notes, hiddenNoteIds, searchQuery, selectedFilter) {
        filterFeedNotes(
            notes = uiState.notes.filterNot { it.id in hiddenNoteIds },
            query = searchQuery,
            filter = selectedFilter,
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag(MindFlowUiTestTags.RECORD_LIST),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "记录",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "灵感易逝，先接住，再行动。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TimeBankBadge(
                            remainingActiveDays = uiState.timeBank.remainingActiveDays,
                        )
                    }
                }

                item {
                    RecordSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                    )
                }

                item {
                    RecordFilterChips(
                        selectedFilter = selectedFilter,
                        onSelected = { selectedFilter = it },
                    )
                }

                item {
                    QuickCaptureCard(onCreateCapture = onCreateCapture)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            text = "最近记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!uiState.isLoading) {
                            RecordStatusSummaryStrip(
                                totalCount = uiState.totalCount,
                                inProgressCount = uiState.inProgressCount,
                                ideaCount = uiState.ideaCount,
                                doneCount = uiState.doneCount,
                                archivedCount = uiState.archivedCount,
                                onOpenStatusFilter = onOpenStatusFilter,
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        EmptyState(
                            title = "正在加载记录",
                            description = "先把本地数据取出来，再展示最近记录。",
                        )
                    }
                } else if (visibleNotes.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (searchQuery.isBlank() && selectedFilter == FeedQuickFilter.ALL) {
                                "还没有记录"
                            } else {
                                "没有匹配记录"
                            },
                            description = if (searchQuery.isBlank() && selectedFilter == FeedQuickFilter.ALL) {
                                "先写下一条想法，让列表慢慢长出来。"
                            } else {
                                "换个关键词或筛选条件再试试。"
                            },
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
                            compact = true,
                        )
                    }
                }
            }
        }
        FloatingCreateButton(
            onClick = { onCreateCapture(FeedCaptureAction.TEXT.toCaptureSeed()) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = ScreenHorizontalPadding + 4.dp,
                    bottom = BottomBarClearance + 16.dp,
                ),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 18.dp),
        )
    }
}

@Composable
private fun FloatingCreateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = AccentBlue,
        shadowElevation = 8.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "新建记录",
                tint = WhiteGlass,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun RecordSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(RecordSearchMinHeight)
            .testTag(MindFlowUiTestTags.RECORD_SEARCH),
        color = WhiteGlass,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = TextSoft,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(color = TextMain),
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isBlank()) {
                            Text(
                                text = RecordSearchPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun RecordFilterChips(
    selectedFilter: FeedQuickFilter,
    onSelected: (FeedQuickFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(MindFlowUiTestTags.RECORD_FILTER_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedQuickFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            Surface(
                color = if (selected) Accent.copy(alpha = 0.12f) else WhiteGlass,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) Accent.copy(alpha = 0.24f) else BorderSoft,
                ),
                onClick = { onSelected(filter) },
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = RecordFilterChipVerticalPadding),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Accent else TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickCaptureCard(
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MindFlowUiTestTags.RECORD_QUICK_INPUT),
        color = WhiteGlass,
        shape = PanelShape,
        border = BorderStroke(1.dp, BorderSoft),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = RecordQuickCaptureVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreateCapture(FeedCaptureAction.TEXT.toCaptureSeed()) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    text = "记录此刻的想法...",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickCaptureInlineAction(
                    icon = Icons.Outlined.Mic,
                    label = "语音",
                    onClick = { onCreateCapture(FeedCaptureAction.VOICE.toCaptureSeed()) },
                    modifier = Modifier.weight(1f),
                )
                QuickCaptureDivider()
                QuickCaptureInlineAction(
                    icon = Icons.Outlined.Image,
                    label = "图片",
                    onClick = { onCreateCapture(FeedCaptureAction.IMAGE.toCaptureSeed()) },
                    modifier = Modifier.weight(1f),
                )
                QuickCaptureDivider()
                QuickCaptureInlineAction(
                    icon = Icons.Outlined.Link,
                    label = "链接",
                    onClick = { onCreateCapture(FeedCaptureAction.LINK.toCaptureSeed()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickCaptureInlineAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AccentTeal,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 7.dp),
        style = MaterialTheme.typography.labelMedium,
            color = TextSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickCaptureDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 20.dp)
            .background(BorderSoft),
    )
}

@Composable
private fun RecordStatusSummaryStrip(
    totalCount: Int,
    inProgressCount: Int,
    ideaCount: Int,
    doneCount: Int,
    archivedCount: Int,
    onOpenStatusFilter: (NoteStatus?, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MindFlowUiTestTags.RECORD_STATUS_STRIP),
        color = WhiteGlass,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordStatusSummaryItem(
                label = "全部",
                value = totalCount,
                onClick = { onOpenStatusFilter(null, false) },
                modifier = Modifier.weight(1f),
            )
            RecordStatusDivider()
            RecordStatusSummaryItem(
                label = "进行中",
                value = inProgressCount,
                onClick = { onOpenStatusFilter(NoteStatus.IN_PROGRESS, false) },
                modifier = Modifier.weight(1f),
            )
            RecordStatusDivider()
            RecordStatusSummaryItem(
                label = "待处理",
                value = ideaCount,
                onClick = { onOpenStatusFilter(NoteStatus.IDEA, false) },
                modifier = Modifier.weight(1f),
            )
            RecordStatusDivider()
            RecordStatusSummaryItem(
                label = "已完成",
                value = doneCount,
                onClick = { onOpenStatusFilter(NoteStatus.DONE, false) },
                modifier = Modifier.weight(1f),
            )
            RecordStatusDivider()
            RecordStatusSummaryItem(
                label = "归档",
                value = archivedCount,
                onClick = { onOpenStatusFilter(null, true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecordStatusSummaryItem(
    label: String,
    value: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = formatCount(value),
            style = MaterialTheme.typography.labelLarge,
            color = TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordStatusDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 24.dp)
            .background(BorderSoft),
    )
}

@Composable
private fun TimeBankBadge(
    remainingActiveDays: Int,
) {
    Surface(
        color = PanelBlue.copy(alpha = 0.6f),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft),
    ) {
        Text(
            text = "${formatCount(remainingActiveDays)} 天",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = RecordTimeBankBadgeVerticalPadding),
            style = MaterialTheme.typography.labelLarge,
            color = AccentBlue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(Locale.CHINA).format(value)
