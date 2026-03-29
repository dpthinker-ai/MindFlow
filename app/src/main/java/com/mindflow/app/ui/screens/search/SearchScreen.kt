package com.mindflow.app.ui.screens.search

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TimeRange
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.navigation.MindFlowDestinations
import com.mindflow.app.util.TimeFormatter

@Composable
fun SearchRoute(
    noteRepository: NoteRepository,
    backgroundFolderOrganizer: BackgroundFolderOrganizer,
    initialStatus: NoteStatus?,
    initialArchivedOnly: Boolean,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(
            noteRepository = noteRepository,
            backgroundFolderOrganizer = backgroundFolderOrganizer,
            initialStatus = initialStatus,
            initialArchivedOnly = initialArchivedOnly,
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
                is SearchEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
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

    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onFolderChange = viewModel::updateFolder,
        onTagChange = viewModel::updateTag,
        onStatusChange = viewModel::updateStatus,
        onTimeRangeChange = viewModel::updateTimeRange,
        onToggleArchived = viewModel::toggleArchived,
        onOpenNote = onOpenNote,
        onClassifyPendingFolders = viewModel::classifyPendingFolders,
        onDeleteNote = viewModel::deleteNote,
        onShareNote = { pendingShareNote = it },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onFolderChange: (String?) -> Unit,
    onTagChange: (String?) -> Unit,
    onStatusChange: (NoteStatus?) -> Unit,
    onTimeRangeChange: (TimeRange) -> Unit,
    onToggleArchived: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onClassifyPendingFolders: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    var showAllTags by remember(uiState.availableTags) { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var foldersExpanded by rememberSaveable { mutableStateOf(false) }
    val visibleTags = remember(uiState.availableTags, showAllTags) {
        if (showAllTags) uiState.availableTags else uiState.availableTags.take(6)
    }
    val activeFilterLabels = remember(uiState) {
        buildList {
            if (uiState.filters.query.isNotBlank()) add("关键词")
            uiState.filters.folderKey?.let { key ->
                add(
                    if (key == MindFlowDestinations.UNCATEGORIZED_FOLDER) {
                        "未分类"
                    } else {
                        MindFolderCatalog.fromKey(key)?.name ?: "文件夹"
                    },
                )
            }
            uiState.filters.tag?.let { add("#$it") }
            uiState.filters.status?.let { add(it.label) }
            if (uiState.filters.timeRange != TimeRange.ALL) add(uiState.filters.timeRange.label)
            when {
                uiState.filters.archivedOnly -> add("仅归档")
                uiState.filters.includeArchived -> add("含归档")
            }
        }
    }
    val filterSummary = remember(activeFilterLabels) {
        if (activeFilterLabels.isEmpty()) {
            "按关键词、状态、标签或时间筛选"
        } else {
            activeFilterLabels.joinToString(" · ")
        }
    }
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
                    PanelCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "筛选",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${uiState.results.size} 条匹配",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            IconPillButton(
                                icon = if (filtersExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.Tune,
                                contentDescription = if (filtersExpanded) "收起筛选" else "展开筛选",
                                onClick = { filtersExpanded = !filtersExpanded },
                                accent = AccentBlue,
                            )
                        }

                            Text(
                                text = filterSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                            )

                        if (filtersExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
                                    value = uiState.filters.query,
                                    onValueChange = onQueryChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("关键词") },
                                    placeholder = { Text("搜主题或正文") },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = WhiteGlass,
                                        unfocusedContainerColor = WhiteGlass,
                                    ),
                                )

                                FilterSectionLabel(text = "状态")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = uiState.filters.status == null,
                                        onClick = { onStatusChange(null) },
                                        label = { Text("全部", maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Accent.copy(alpha = 0.14f),
                                            selectedLabelColor = Accent,
                                        ),
                                    )
                                    NoteStatus.entries.forEach { status ->
                                        FilterChip(
                                            selected = uiState.filters.status == status,
                                            onClick = { onStatusChange(status) },
                                            label = { Text(status.label, maxLines = 1) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = noteStatusAccent(status).copy(alpha = 0.14f),
                                                selectedLabelColor = noteStatusAccent(status),
                                            ),
                                        )
                                    }
                                }

                                FilterSectionLabel(
                                    text = if (showAllTags || uiState.availableTags.size <= 6) "标签" else "标签 · 最近 6 个",
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = uiState.filters.tag == null,
                                        onClick = { onTagChange(null) },
                                        label = { Text("全部标签", maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentBlue.copy(alpha = 0.14f),
                                            selectedLabelColor = AccentBlue,
                                        ),
                                    )
                                    visibleTags.forEach { tag ->
                                        FilterChip(
                                            selected = uiState.filters.tag == tag,
                                            onClick = { onTagChange(tag) },
                                            label = { Text(tag, maxLines = 1) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Accent.copy(alpha = 0.14f),
                                                selectedLabelColor = Accent,
                                            ),
                                        )
                                    }
                                }
                                if (uiState.availableTags.size > 6) {
                                    Text(
                                        text = if (showAllTags) "收起标签" else "更多标签",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentBlue,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable { showAllTags = !showAllTags },
                                    )
                                }

                                FilterSectionLabel(text = "时间")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TimeRange.entries.forEach { timeRange ->
                                        FilterChip(
                                            selected = uiState.filters.timeRange == timeRange,
                                            onClick = { onTimeRangeChange(timeRange) },
                                            label = { Text(timeRange.label, maxLines = 1) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentBlue.copy(alpha = 0.14f),
                                                selectedLabelColor = AccentBlue,
                                            ),
                                        )
                                    }
                                }

                                Surface(
                                    color = WhiteGlass.copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text("含归档", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = uiState.filters.includeArchived || uiState.filters.archivedOnly,
                                            onCheckedChange = { onToggleArchived() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "文件夹",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (uiState.uncategorizedCount > 0) {
                                        "${uiState.folderCounts.values.count { it > 0 }} 类 · 未分类 ${uiState.uncategorizedCount}"
                                    } else {
                                        "${uiState.folderCounts.values.count { it > 0 }} 类"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            IconPillButton(
                                icon = if (foldersExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.FolderOpen,
                                contentDescription = if (foldersExpanded) "收起文件夹" else "展开文件夹",
                                onClick = { foldersExpanded = !foldersExpanded },
                                accent = AccentBlue,
                            )
                        }

                        Text(
                            text = if (uiState.lastAutoOrganizedAt > 0L) {
                                "最近整理 ${TimeFormatter.compact(uiState.lastAutoOrganizedAt)} · 归类 ${uiState.lastAutoOrganizedCount} 条"
                            } else {
                                "按文件夹筛选记录；未分类内容可手动整理。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )

                        if (foldersExpanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = uiState.filters.folderKey == null,
                                    onClick = { onFolderChange(null) },
                                    label = { Text("全部文件夹", maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentBlue.copy(alpha = 0.14f),
                                        selectedLabelColor = AccentBlue,
                                    ),
                                )
                                uiState.availableFolders.forEach { folder ->
                                    val accent = Color(AndroidColor.parseColor(folder.colorHex))
                                    val count = uiState.folderCounts[folder.key] ?: 0
                                    FilterChip(
                                        selected = uiState.filters.folderKey == folder.key,
                                        onClick = { onFolderChange(folder.key) },
                                        label = { Text("${folder.name} $count", maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.14f),
                                            selectedLabelColor = accent,
                                        ),
                                    )
                                }
                                FilterChip(
                                    selected = uiState.filters.folderKey == MindFlowDestinations.UNCATEGORIZED_FOLDER,
                                    onClick = { onFolderChange(MindFlowDestinations.UNCATEGORIZED_FOLDER) },
                                    label = { Text("未分类 ${uiState.uncategorizedCount}", maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Accent.copy(alpha = 0.14f),
                                        selectedLabelColor = Accent,
                                    ),
                                )
                            }

                            if (uiState.uncategorizedCount > 0) {
                                GhostActionButton(
                                    text = if (uiState.pendingFolderClassificationCount > 0) {
                                        "自动归类未分类记录"
                                    } else {
                                        "尝试整理未分类记录"
                                    },
                                    onClick = onClassifyPendingFolders,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Outlined.AutoFixHigh,
                                )
                            }
                        }
                    }
                }

                if (uiState.results.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "结果",
                            headline = "${uiState.results.size} 条",
                        )
                    }
                }

                if (uiState.results.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有匹配结果",
                            description = "试试更短的关键词，或者放宽状态与时间窗口。",
                        )
                    }
                } else {
                    items(uiState.results, key = { it.id }) { note ->
                        SwipeRevealNoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onToggleArchive = null,
                            onShare = { onShareNote(note) },
                            onDelete = { onDeleteNote(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.heightIn(min = 20.dp),
    )
}
