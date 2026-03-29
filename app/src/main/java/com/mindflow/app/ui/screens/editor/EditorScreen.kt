package com.mindflow.app.ui.screens.editor

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.MarkdownText
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditorRoute(
    noteRepository: NoteRepository,
    aiSettingsRepository: AiSettingsRepository,
    aiServiceClient: AiServiceClient,
    noteId: Long?,
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    onSavedNewNote: () -> Unit,
) {
    val viewModel: NoteEditorViewModel = viewModel(
        key = "editor-$noteId",
        factory = NoteEditorViewModel.factory(
            noteRepository = noteRepository,
            aiSettingsRepository = aiSettingsRepository,
            aiServiceClient = aiServiceClient,
            noteId = noteId,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allNotes by noteRepository.observeAllNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val relatedNotes = remember(
        uiState.isLoading,
        uiState.noteId,
        uiState.topic,
        uiState.content,
        uiState.folderKey,
        uiState.tags,
        allNotes,
    ) {
        if (uiState.isLoading || uiState.noteId == null) {
            emptyList()
        } else {
            NoteConnectionAnalyzer.buildRelatedNotes(
                currentNoteId = uiState.noteId,
                topic = uiState.topic,
                content = uiState.content,
                folderKey = uiState.folderKey,
                tags = uiState.tags,
                notes = allNotes,
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is NoteEditorEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                NoteEditorEvent.NavigateBack -> if (noteId == null) onSavedNewNote() else onBack()
            }
        }
    }

    EditorScreen(
        uiState = uiState,
        onBack = onBack,
        onContentChange = viewModel::onContentChange,
        onTopicChange = viewModel::onTopicChange,
        onFolderChange = viewModel::onFolderChange,
        onAddTag = viewModel::addTag,
        onRemoveTag = viewModel::removeTag,
        onStatusChange = viewModel::onStatusChange,
        onArchiveChange = viewModel::onArchivedChange,
        onSave = { viewModel.save(exitAfterSave = false) },
        onSaveAndExit = { viewModel.save(exitAfterSave = true) },
        onPolishContent = viewModel::polishContent,
        onApplyPolishedContent = viewModel::applyPolishedContent,
        onDiscardPolishedContent = viewModel::discardPolishedContent,
        onRetriggerFolder = viewModel::retriggerFolderClassification,
        onRetriggerTopic = viewModel::retriggerTopicExtraction,
        onRetriggerTag = viewModel::retriggerTagExtraction,
        relatedNotes = relatedNotes,
        onOpenRelatedNote = onOpenNote,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun EditorScreen(
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onFolderChange: (String?) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onStatusChange: (NoteStatus) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onPolishContent: () -> Unit,
    onApplyPolishedContent: () -> Unit,
    onDiscardPolishedContent: () -> Unit,
    onRetriggerFolder: () -> Unit,
    onRetriggerTopic: () -> Unit,
    onRetriggerTag: () -> Unit,
    relatedNotes: List<NoteEntity>,
    onOpenRelatedNote: (Long) -> Unit,
) {
    if (uiState.isLoading) {
        ScreenBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconPillButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "编辑记录",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "正在加载内容…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PanelCard {
                    Text(
                        text = "正在打开这条记录，请稍等。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTag by rememberSaveable(uiState.noteId) { mutableStateOf("") }
    var previewingOriginal by rememberSaveable(
        uiState.polishedOriginalContent,
        uiState.polishedCandidateContent,
    ) { mutableStateOf(false) }
    var isEditingContent by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(uiState.isNew) }
    var aiToolsExpanded by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()
    val contentBringIntoViewRequester = remember { BringIntoViewRequester() }
    var isContentFieldFocused by rememberSaveable { mutableStateOf(false) }

    fun requestBack() {
        if (uiState.hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        requestBack()
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("先保存记录") },
            text = { Text("记录修改后还没有保存，请保存后再退出") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onSaveAndExit()
                    },
                ) {
                    Text("保存记录")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    },
                ) {
                    Text("继续退出")
                }
            },
        )
    }

    LaunchedEffect(isContentFieldFocused, uiState.content) {
        if (isContentFieldFocused) {
            delay(160)
            contentBringIntoViewRequester.bringIntoView()
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPillButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = ::requestBack,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (uiState.isNew) "新建记录" else "编辑记录",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = if (uiState.isNew) "先接住，再慢慢整理。" else "调整正文、主题和标签。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PanelCard {
                    SectionHeader(title = if (uiState.isNew) "新记录" else "编辑")

                    if (!uiState.isNew) {
                        Text("主题", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PaperField(
                            value = uiState.topic,
                            onValueChange = onTopicChange,
                            placeholder = "一个短一点、能快速认出的主题",
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = topicSourceLabel(uiState.topicSource),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text("内容", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isEditingContent) "原文编辑" else "Markdown 预览",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GhostActionButton(
                            text = if (isEditingContent) "完成预览" else "编辑正文",
                            onClick = {
                                isEditingContent = !isEditingContent
                                if (!isEditingContent) {
                                    isContentFieldFocused = false
                                }
                            },
                            icon = if (isEditingContent) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                        )
                    }
                    if (isEditingContent) {
                        PaperField(
                            value = uiState.content,
                            onValueChange = onContentChange,
                            placeholder = "比如一个想法、要试的事、要观察的问题",
                            modifier = Modifier.bringIntoViewRequester(contentBringIntoViewRequester),
                            minLines = if (uiState.isNew) 10 else 7,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            onFocusChanged = { isContentFieldFocused = it },
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isEditingContent = true },
                            color = WhiteGlass.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            if (uiState.content.isBlank()) {
                                Text(
                                    text = "点一下开始写内容",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                MarkdownText(
                                    markdown = uiState.content,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                    GhostActionButton(
                        text = if (aiToolsExpanded) "收起 AI 助手" else "AI 助手",
                        onClick = { aiToolsExpanded = !aiToolsExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "需要时再展开，避免打断写作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (aiToolsExpanded) {
                        Surface(
                            color = WhiteGlass.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "AI 工具",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                GridTwo {
                                    GhostActionButton(
                                        text = if (uiState.isPolishingContent) "正在润色" else "润色正文",
                                        onClick = onPolishContent,
                                        enabled = !uiState.isPolishingContent && uiState.content.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!uiState.isNew) {
                                        GhostActionButton(
                                            text = if (uiState.isRefreshingTopic) "正在提炼" else "提炼主题",
                                            onClick = onRetriggerTopic,
                                            enabled = !uiState.isRefreshingTopic,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                                if (!uiState.isNew) {
                                    GridTwo {
                                        GhostActionButton(
                                            text = if (uiState.isRefreshingTags) "正在提取" else "提取标签",
                                            onClick = onRetriggerTag,
                                            enabled = !uiState.isRefreshingTags,
                                            modifier = Modifier.weight(1f),
                                        )
                                        GhostActionButton(
                                            text = if (uiState.isRefreshingFolder) "正在归类" else "归类内容",
                                            onClick = onRetriggerFolder,
                                            enabled = !uiState.isRefreshingFolder,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!uiState.polishedOriginalContent.isNullOrBlank() && !uiState.polishedCandidateContent.isNullOrBlank()) {
                        PolishPreviewCard(
                            showingOriginal = previewingOriginal,
                            original = uiState.polishedOriginalContent,
                            polished = uiState.polishedCandidateContent,
                            onTogglePreview = { previewingOriginal = !previewingOriginal },
                            onApply = {
                                previewingOriginal = false
                                onApplyPolishedContent()
                            },
                            onDiscard = {
                                previewingOriginal = false
                                onDiscardPolishedContent()
                            },
                        )
                    }

                }

                ActionButton(
                    text = if (uiState.isSaving) "保存中..." else "保存记录",
                    onClick = onSave,
                    enabled = !uiState.isSaving && uiState.content.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )

                PanelCard {
                    SectionHeader(title = "整理")

                    Text("文件夹", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.folderKey == null,
                            onClick = { onFolderChange(null) },
                            label = { Text("未分类") },
                        )
                        MindFolderCatalog.all.forEach { folder ->
                            val accent = folderColor(folder.key)
                            FilterChip(
                                selected = uiState.folderKey == folder.key,
                                onClick = { onFolderChange(folder.key) },
                                label = { Text(folder.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.14f),
                                    selectedLabelColor = accent,
                                ),
                            )
                        }
                    }
                    Text(
                        text = folderSourceLabel(uiState.folderSource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!uiState.isNew) {
                        Text("标签", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = tagSourceLabel(uiState.tagSource, uiState.tags.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.tags.forEach { tag ->
                                    EditableTagChip(
                                        tag = tag,
                                        onRemove = { onRemoveTag(tag) },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PaperField(
                                value = pendingTag,
                                onValueChange = { pendingTag = it },
                                placeholder = if (uiState.tags.size >= 3) "最多 3 个标签" else "添加一个标签",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                            )
                            GhostActionButton(
                                text = "添加",
                                onClick = {
                                    onAddTag(pendingTag)
                                    pendingTag = ""
                                },
                                enabled = pendingTag.isNotBlank() && uiState.tags.size < 3,
                                modifier = Modifier.weight(0.42f),
                            )
                        }

                        Text("进展状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NoteStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = uiState.status == status,
                                    onClick = { onStatusChange(status) },
                                    label = { Text(status.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = noteStatusAccent(status).copy(alpha = 0.14f),
                                        selectedLabelColor = noteStatusAccent(status),
                                    ),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "保存后会自动补主题和标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!uiState.isNew) {
                    PanelCard {
                        SectionHeader(title = "信息", headline = uiState.status.label)
                        Surface(
                            color = WhiteGlass.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("归档", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (uiState.isArchived) "已从首页隐藏" else "仍显示在首页",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = uiState.isArchived,
                                    onCheckedChange = onArchiveChange,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            MetaRow("创建于", uiState.createdAt?.let(TimeFormatter::compact).orEmpty(), Modifier.weight(1f))
                            MetaRow("更新于", uiState.updatedAt?.let(TimeFormatter::compact).orEmpty(), Modifier.weight(1f))
                        }
                        uiState.latestDoneAt?.let { MetaRow("完成于", TimeFormatter.compact(it)) }
                        if (uiState.statusHistory.isNotEmpty()) {
                            Text(
                                text = "最近变化",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.statusHistory.take(3).forEach { entry ->
                                Surface(
                                    color = WhiteGlass.copy(alpha = 0.92f),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = TimeFormatter.full(entry.changedAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "${entry.fromStatus?.label ?: "初始"} -> ${entry.toStatus.label}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "还没有状态变化。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (relatedNotes.isNotEmpty()) {
                        PanelCard {
                            SectionHeader(title = "相关记录", headline = "${relatedNotes.size} 条")
                            relatedNotes.forEach { note ->
                                val cardModifier = if (uiState.hasUnsavedChanges) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenRelatedNote(note.id) }
                                }
                                Surface(
                                    modifier = cardModifier,
                                    color = WhiteGlass.copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = note.topic.ifBlank { "未命名记录" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle,
            placeholder = {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditableTagChip(
    tag: String,
    onRemove: () -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "删除标签",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PolishPreviewCard(
    showingOriginal: Boolean,
    original: String,
    polished: String,
    onTogglePreview: () -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "AI 润色预览",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = if (showingOriginal) "当前查看原文，长按下方卡片切回润色稿" else "当前查看润色稿，长按下方卡片切换原文",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
                Text(
                    text = if (showingOriginal) "原文" else "润色稿",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBlue,
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onTogglePreview,
                    ),
                color = Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            ) {
                MarkdownText(
                    markdown = if (showingOriginal) original else polished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                )
            }

            GridTwo {
                GhostActionButton(
                    text = "保留原文",
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "应用润色",
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun topicSourceLabel(source: TopicSource): String = when (source) {
    TopicSource.AI -> "当前主题来自 AI"
    TopicSource.RULE -> "当前主题来自本地规则"
    TopicSource.MANUAL -> "当前主题由你手动确认"
}

private fun tagSourceLabel(
    source: TagSource,
    count: Int,
): String {
    val prefix = when (source) {
        TagSource.AI -> "当前标签来自 AI"
        TagSource.RULE -> "当前标签来自本地规则"
        TagSource.MANUAL -> "当前标签已手动调整"
    }
    return "$prefix · $count/3"
}

private fun folderSourceLabel(source: com.mindflow.app.data.model.FolderSource): String = when (source) {
    com.mindflow.app.data.model.FolderSource.AI -> "当前文件夹来自 AI 分类"
    com.mindflow.app.data.model.FolderSource.RULE -> "当前文件夹来自本地规则"
    com.mindflow.app.data.model.FolderSource.MANUAL -> "当前文件夹由你手动确认"
}

private fun folderColor(folderKey: String): Color =
    Color(AndroidColor.parseColor(MindFolderCatalog.fromKey(folderKey)?.colorHex ?: "#64748B"))
