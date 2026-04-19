package com.mindflow.app.ui.screens.editor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.MindFlowApplication
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskType
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.localmodel.EditorKnowledgeRecallPlanner
import com.mindflow.app.data.localmodel.EditorKnowledgeRecallResult
import com.mindflow.app.data.topic.ContentPolishPlanner
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal data class EditorDraftAnalysisInput(
    val isLoading: Boolean,
    val noteId: Long?,
    val topic: String,
    val content: String,
    val folderKey: String?,
    val tags: List<String>,
    val allNotes: List<NoteEntity>,
)

internal data class EditorDraftAnalysis(
    val relatedNotes: List<NoteEntity> = emptyList(),
    val suggestedThread: ThemeThread? = null,
)

private data class EditorSupportInsights(
    val relatedNotes: List<NoteEntity> = emptyList(),
    val suggestedThread: ThemeThread? = null,
)

private data class EditorKnowledgeRecallUiState(
    val isLoading: Boolean = false,
    val result: EditorKnowledgeRecallResult? = null,
)

internal data class EditorAiTraceSnapshot(
    val taskType: AiTaskType,
    val providerUsed: AiProvider,
    val fallbackOccurred: Boolean,
)

internal const val CaptureContentFieldTestTag = "capture_content_field"

internal fun shouldComputeEditorInsights(
    isLoading: Boolean,
    noteId: Long?,
    metadataExpanded: Boolean,
    extraInfoExpanded: Boolean,
): Boolean = !isLoading && noteId != null && metadataExpanded && extraInfoExpanded

internal fun shouldRequestEditorKnowledgeRecall(
    isLoading: Boolean,
    requestVersion: Int,
): Boolean = !isLoading && requestVersion > 0

internal fun buildEditorAiModeSummary(
    mode: AiExecutionMode,
    onDeviceReady: Boolean,
): String = when (mode) {
    AiExecutionMode.AUTOMATIC -> {
        if (onDeviceReady) {
            "当前策略：自动。编辑页会先云侧，失败后回退端侧。"
        } else {
            "当前策略：自动。端侧未就绪，这次会直接走云侧。"
        }
    }
    AiExecutionMode.ON_DEVICE_ONLY -> {
        if (onDeviceReady) {
            "当前策略：仅端侧。所有整理都会固定走本地模型。"
        } else {
            "当前策略：仅端侧。本地模型未就绪时，这类整理不会返回结果。"
        }
    }
    AiExecutionMode.CLOUD_ONLY -> "当前策略：仅云侧。不会占用本地模型推理资源。"
}

internal fun buildEditorAiRunFeedback(
    taskType: AiTaskType,
    provider: AiProvider,
    fallbackOccurred: Boolean,
): String {
    val actionLabel = when (taskType) {
        AiTaskType.POLISH_CONTENT -> "整理正文"
        AiTaskType.EXTRACT_TOPIC -> "整理主题"
        AiTaskType.EXTRACT_TAGS -> "整理标签"
        AiTaskType.CLASSIFY_CATEGORY -> "整理分类"
        AiTaskType.GRAPH_EXTRACT_CONCEPTS,
        AiTaskType.GRAPH_CANONICALIZE_CONCEPTS,
        AiTaskType.GRAPH_GENERATE_RELATIONS,
        -> "AI 任务"
    }
    val providerLabel = when (provider) {
        AiProvider.ON_DEVICE -> "端侧"
        AiProvider.CLOUD -> "云侧"
    }
    return if (fallbackOccurred) {
        "本次${actionLabel}由${providerLabel}完成，另一侧没有给出可用结果。"
    } else {
        "本次${actionLabel}由${providerLabel}完成。"
    }
}

internal fun parseEditorAiTraceSnapshot(raw: String): EditorAiTraceSnapshot? {
    val normalized = raw.trim()
    val taskType = extractJsonStringValue(normalized, "taskType")
        ?.let { runCatching { AiTaskType.valueOf(it) }.getOrNull() }
        ?: return null
    val providerUsed = extractJsonStringValue(normalized, "providerUsed")
        ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
        ?: return null
    val fallbackOccurred = extractJsonBooleanValue(normalized, "fallbackOccurred") ?: false
    return EditorAiTraceSnapshot(
        taskType = taskType,
        providerUsed = providerUsed,
        fallbackOccurred = fallbackOccurred,
    )
}

private fun extractJsonStringValue(
    raw: String,
    key: String,
): String? = Regex(""""$key"\s*:\s*"([^"]+)"""")
    .find(raw)
    ?.groupValues
    ?.getOrNull(1)

private fun extractJsonBooleanValue(
    raw: String,
    key: String,
): Boolean? = Regex(""""$key"\s*:\s*(true|false)""")
    .find(raw)
    ?.groupValues
    ?.getOrNull(1)
    ?.toBooleanStrictOrNull()

private fun readLatestEditorAiRunFeedback(
    context: Context,
    expectedTaskType: AiTaskType,
): String? {
    val traceFile = File(context.applicationContext.filesDir, "ai-traces/latest-successful-provider.json")
    val snapshot = traceFile.takeIf(File::exists)
        ?.readText()
        ?.let(::parseEditorAiTraceSnapshot)
        ?: return null
    if (snapshot.taskType != expectedTaskType) return null
    return buildEditorAiRunFeedback(
        taskType = snapshot.taskType,
        provider = snapshot.providerUsed,
        fallbackOccurred = snapshot.fallbackOccurred,
    )
}

@Composable
fun EditorRoute(
    noteRepository: NoteRepository,
    contentPolishPlanner: ContentPolishPlanner,
    editorKnowledgeRecallPlanner: EditorKnowledgeRecallPlanner,
    noteId: Long?,
    captureSessionKey: Long? = null,
    initialContent: String = "",
    initialTopic: String = "",
    initialFolderKey: String? = null,
    initialTags: List<String> = emptyList(),
    initialKnowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
    autoStartVoiceInput: Boolean = false,
    onOpenNote: (Long) -> Unit,
    onOpenThread: (String) -> Unit,
    onBack: () -> Unit,
    onSavedNewNote: () -> Unit,
) {
    val viewModel: NoteEditorViewModel = viewModel(
        key = if (noteId == null) {
            "editor-new-${captureSessionKey ?: 0L}"
        } else {
            "editor-$noteId"
        },
        factory = NoteEditorViewModel.factory(
            noteRepository = noteRepository,
            contentPolishPlanner = contentPolishPlanner,
            noteId = noteId,
            initialContent = initialContent,
            initialTopic = initialTopic,
            initialFolderKey = initialFolderKey,
            initialTags = initialTags,
            initialKnowledgeTrust = initialKnowledgeTrust,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autoVoiceStarted by rememberSaveable(captureSessionKey ?: -1L) { mutableStateOf(false) }
    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val recognizedText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (recognizedText.isBlank()) return@rememberLauncherForActivityResult
        val mergedContent = when {
            uiState.content.isBlank() -> recognizedText
            else -> uiState.content.trimEnd() + "\n\n" + recognizedText
        }
        viewModel.onContentChange(mergedContent)
        Toast.makeText(context, "已转成正文，可继续编辑", Toast.LENGTH_SHORT).show()
    }
    val startVoiceCapture: () -> Unit = remember(speechInputLauncher, context) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你现在想记下的内容")
            }
            runCatching { speechInputLauncher.launch(intent) }
                .onFailure {
                    val message = if (it is ActivityNotFoundException) {
                        "设备上没有可用的语音识别服务"
                    } else {
                        "暂时无法启动语音输入"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
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

    LaunchedEffect(autoStartVoiceInput, uiState.isLoading, uiState.noteId) {
        if (
            autoStartVoiceInput &&
            !autoVoiceStarted &&
            !uiState.isLoading &&
            uiState.noteId == null
        ) {
            autoVoiceStarted = true
            startVoiceCapture()
        }
    }

    if (uiState.isNew) {
        CaptureEditorScreen(
            uiState = uiState,
            onBack = onBack,
            onContentChange = viewModel::onContentChange,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onVoiceCapture = startVoiceCapture,
        )
    } else {
        FullEditorScreen(
            uiState = uiState,
            noteRepository = noteRepository,
            editorKnowledgeRecallPlanner = editorKnowledgeRecallPlanner,
            onBack = onBack,
            onContentChange = viewModel::onContentChange,
            onTopicChange = viewModel::onTopicChange,
            onFolderChange = viewModel::onFolderChange,
            onAddTag = viewModel::addTag,
            onRemoveTag = viewModel::removeTag,
            onStatusChange = viewModel::onStatusChange,
            onHorizonChange = viewModel::onHorizonChange,
            onKnowledgeTrustChange = viewModel::onKnowledgeTrustChange,
            onArchiveChange = viewModel::onArchivedChange,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onVoiceCapture = startVoiceCapture,
            onPolishContent = viewModel::polishContent,
            onApplyPolishedContent = viewModel::applyPolishedContent,
            onDiscardPolishedContent = viewModel::discardPolishedContent,
            onRetriggerFolder = viewModel::retriggerFolderClassification,
            onRetriggerTopic = viewModel::retriggerTopicExtraction,
            onRetriggerTag = viewModel::retriggerTagExtraction,
            onOpenSuggestedThread = onOpenThread,
            onOpenRelatedNote = onOpenNote,
        )
    }
}

internal fun buildEditorDraftAnalysis(input: EditorDraftAnalysisInput): EditorDraftAnalysis {
    val noteId = input.noteId ?: return EditorDraftAnalysis()
    val relatedNotes = NoteConnectionAnalyzer.buildRelatedNotes(
        currentNoteId = noteId,
        topic = input.topic,
        content = input.content,
        folderKey = input.folderKey,
        tags = input.tags,
        notes = input.allNotes,
    )
    val suggestedThread = NoteConnectionAnalyzer.bestThreadForDraft(
        currentNoteId = noteId,
        topic = input.topic,
        content = input.content,
        folderKey = input.folderKey,
        tags = input.tags,
        notes = input.allNotes,
    )
    return EditorDraftAnalysis(
        relatedNotes = relatedNotes,
        suggestedThread = suggestedThread,
    )
}

@Composable
private fun rememberEditorSupportInsights(
    shouldCompute: Boolean,
    uiState: NoteEditorUiState,
    noteRepository: NoteRepository,
): EditorSupportInsights {
    if (!shouldCompute) {
        return EditorSupportInsights()
    }

    val allNotes = noteRepository.observeAllNotes().collectAsStateWithLifecycle(initialValue = emptyList()).value
    val analysisInput = remember(
        uiState.isLoading,
        uiState.noteId,
        uiState.topic,
        uiState.content,
        uiState.folderKey,
        uiState.tags,
        allNotes,
    ) {
        EditorDraftAnalysisInput(
            isLoading = uiState.isLoading,
            noteId = uiState.noteId,
            topic = uiState.topic,
            content = uiState.content,
            folderKey = uiState.folderKey,
            tags = uiState.tags,
            allNotes = allNotes,
        )
    }
    val draftAnalysis by produceState(
        initialValue = EditorDraftAnalysis(),
        analysisInput,
    ) {
        if (analysisInput.isLoading) {
            value = EditorDraftAnalysis()
            return@produceState
        }
        delay(320)
        value = withContext(Dispatchers.Default) {
            buildEditorDraftAnalysis(analysisInput)
        }
    }

    return EditorSupportInsights(
        relatedNotes = draftAnalysis.relatedNotes,
        suggestedThread = draftAnalysis.suggestedThread,
    )
}

@Composable
internal fun CaptureEditorScreen(
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onVoiceCapture: () -> Unit,
) {
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

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
        UnsavedChangesExitDialog(
            onDismiss = { showExitDialog = false },
            onSaveAndExit = {
                showExitDialog = false
                onSaveAndExit()
            },
            onLeaveWithoutSaving = {
                showExitDialog = false
                onBack()
            },
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(
                title = "快速记录",
                onBack = ::requestBack,
            )

            PanelCard(modifier = Modifier.weight(1f)) {
                PaperField(
                    value = uiState.content,
                    onValueChange = onContentChange,
                    placeholder = "记点什么",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(CaptureContentFieldTestTag),
                    minLines = 12,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    expandToContainer = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostActionButton(
                    text = "语音",
                    onClick = onVoiceCapture,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = if (uiState.isSaving) "保存中..." else "保存",
                    onClick = onSave,
                    enabled = !uiState.isSaving && uiState.content.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Save,
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UnsavedChangesExitDialog(
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onLeaveWithoutSaving: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出前保存？") },
        text = { Text("这条记录还没保存。") },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text("保存并退出")
            }
        },
        dismissButton = {
            TextButton(onClick = onLeaveWithoutSaving) {
                Text("直接退出")
            }
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun FullEditorScreen(
    uiState: NoteEditorUiState,
    noteRepository: NoteRepository,
    editorKnowledgeRecallPlanner: EditorKnowledgeRecallPlanner,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onFolderChange: (String?) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onStatusChange: (NoteStatus) -> Unit,
    onHorizonChange: (NoteHorizon) -> Unit,
    onKnowledgeTrustChange: (KnowledgeTrust) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onVoiceCapture: () -> Unit,
    onPolishContent: () -> Unit,
    onApplyPolishedContent: () -> Unit,
    onDiscardPolishedContent: () -> Unit,
    onRetriggerFolder: () -> Unit,
    onRetriggerTopic: () -> Unit,
    onRetriggerTag: () -> Unit,
    onOpenSuggestedThread: (String) -> Unit,
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
                EditorTopBar(
                    title = "编辑记录",
                    subtitle = "正在加载内容…",
                    onBack = onBack,
                )
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
    var metadataExpanded by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(!uiState.isNew) }
    var extraInfoExpanded by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(false) }
    var recordInfoExpanded by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var knowledgeRecallRequestVersion by rememberSaveable(uiState.noteId) { mutableStateOf(0) }
    var aiRunFeedback by rememberSaveable(uiState.noteId) { mutableStateOf<String?>(null) }
    var polishWasRunning by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var topicWasRunning by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var tagsWasRunning by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    var folderWasRunning by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()
    val contentBringIntoViewRequester = remember { BringIntoViewRequester() }
    var isContentFieldFocused by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val onDeviceSettingsRepository = remember(context.applicationContext) {
        (context.applicationContext as MindFlowApplication).appContainer.onDeviceModelSettingsRepository
    }
    val onDeviceSettings by onDeviceSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = OnDeviceModelSettings(),
    )
    val aiModeSummary = remember(onDeviceSettings.executionMode, onDeviceSettings.isReady) {
        buildEditorAiModeSummary(
            mode = onDeviceSettings.executionMode,
            onDeviceReady = onDeviceSettings.isReady,
        )
    }

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
        UnsavedChangesExitDialog(
            onDismiss = { showExitDialog = false },
            onSaveAndExit = {
                showExitDialog = false
                onSaveAndExit()
            },
            onLeaveWithoutSaving = {
                showExitDialog = false
                onBack()
            },
        )
    }

    val insights = rememberEditorSupportInsights(
        shouldCompute = shouldComputeEditorInsights(
            isLoading = uiState.isLoading,
            noteId = uiState.noteId,
            metadataExpanded = metadataExpanded,
            extraInfoExpanded = extraInfoExpanded,
        ),
        uiState = uiState,
        noteRepository = noteRepository,
    )
    val knowledgeRecallUiState by produceState(
        initialValue = EditorKnowledgeRecallUiState(),
        knowledgeRecallRequestVersion,
        uiState.isLoading,
        uiState.topic,
        uiState.content,
        insights.suggestedThread?.key,
        insights.relatedNotes.map { it.id to it.updatedAt },
    ) {
        if (
            !shouldRequestEditorKnowledgeRecall(
                isLoading = uiState.isLoading,
                requestVersion = knowledgeRecallRequestVersion,
            )
        ) {
            value = EditorKnowledgeRecallUiState()
            return@produceState
        }

        value = EditorKnowledgeRecallUiState(isLoading = true)
        value = EditorKnowledgeRecallUiState(
            result = editorKnowledgeRecallPlanner.summarize(
                draftTopic = uiState.topic,
                draftContent = uiState.content,
                suggestedThreadTitle = insights.suggestedThread?.title,
                relatedNotes = insights.relatedNotes,
            ),
        )
    }

    LaunchedEffect(isContentFieldFocused) {
        if (isContentFieldFocused) {
            delay(160)
            contentBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(uiState.isPolishingContent) {
        if (uiState.isPolishingContent) {
            polishWasRunning = true
        } else if (polishWasRunning) {
            polishWasRunning = false
            aiRunFeedback = readLatestEditorAiRunFeedback(context, AiTaskType.POLISH_CONTENT)
                ?: "本次整理正文按当前策略运行，但没有记录到最终 provider。"
        }
    }

    LaunchedEffect(uiState.isRefreshingTopic) {
        if (uiState.isRefreshingTopic) {
            topicWasRunning = true
        } else if (topicWasRunning) {
            topicWasRunning = false
            aiRunFeedback = readLatestEditorAiRunFeedback(context, AiTaskType.EXTRACT_TOPIC)
                ?: "本次整理主题已结束，可能直接回到了规则结果。"
        }
    }

    LaunchedEffect(uiState.isRefreshingTags) {
        if (uiState.isRefreshingTags) {
            tagsWasRunning = true
        } else if (tagsWasRunning) {
            tagsWasRunning = false
            aiRunFeedback = readLatestEditorAiRunFeedback(context, AiTaskType.EXTRACT_TAGS)
                ?: "本次整理标签已结束，可能直接回到了规则结果。"
        }
    }

    LaunchedEffect(uiState.isRefreshingFolder) {
        if (uiState.isRefreshingFolder) {
            folderWasRunning = true
        } else if (folderWasRunning) {
            folderWasRunning = false
            aiRunFeedback = readLatestEditorAiRunFeedback(context, AiTaskType.CLASSIFY_CATEGORY)
                ?: "本次整理分类已结束，可能直接回到了规则结果。"
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
            EditorTopBar(
                title = "编辑记录",
                subtitle = "先改正文，再决定要不要交给 AI 整理。",
                onBack = ::requestBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PanelCard {
                    SectionHeader(title = "编辑")

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

                    Text("内容", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = if (isEditingContent) "原文编辑" else "Markdown 预览",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isEditingContent) {
                                Text(
                                    text = "也可以直接用语音转成正文",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                            }
                        }
                        if (isEditingContent) {
                            GhostActionButton(
                                text = "语音输入",
                                onClick = onVoiceCapture,
                            )
                        }
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
                        text = if (aiToolsExpanded) "收起 AI 整理" else "AI 整理",
                        onClick = { aiToolsExpanded = !aiToolsExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "主题、标签和分类会自动补上，需要时再让 AI 帮你整理。",
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
                                    text = "整理这条记录",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = aiModeSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                aiRunFeedback?.let { feedback ->
                                    Text(
                                        text = feedback,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentBlue,
                                    )
                                }
                                GridTwo {
                                    GhostActionButton(
                                        text = if (uiState.isPolishingContent) "整理正文中" else "整理正文",
                                        onClick = onPolishContent,
                                        enabled = !uiState.isPolishingContent && uiState.content.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!uiState.isNew) {
                                        GhostActionButton(
                                            text = if (uiState.isRefreshingTopic) "整理主题中" else "整理主题",
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
                                            text = if (uiState.isRefreshingTags) "整理标签中" else "整理标签",
                                            onClick = onRetriggerTag,
                                            enabled = !uiState.isRefreshingTags,
                                            modifier = Modifier.weight(1f),
                                        )
                                        GhostActionButton(
                                            text = if (uiState.isRefreshingFolder) "整理分类中" else "整理分类",
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
                    text = when {
                        uiState.isSaving -> "保存中..."
                        uiState.isNew -> "先存下这颗火花"
                        else -> "保存记录"
                    },
                    onClick = onSave,
                    enabled = !uiState.isSaving && uiState.content.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )

                PanelCard {
                    SectionHeader(
                        title = "补充信息",
                        headline = if (uiState.isNew && !metadataExpanded) {
                            "默认先轻记"
                        } else {
                            "${uiState.status.label} · ${uiState.horizon.label}"
                        },
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { metadataExpanded = !metadataExpanded },
                        color = WhiteGlass.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = if (uiState.isNew) "先快速记，再补结构" else "状态、研究判断和分类",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (uiState.isNew && !metadataExpanded) {
                                        "默认保持 ${uiState.status.label} · ${uiState.horizon.label} · ${uiState.knowledgeTrust.label}。需要时再展开。"
                                    } else {
                                        "需要时再补状态、证据强度、时间尺度和分类。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (metadataExpanded) "收起" else "展开",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentBlue,
                            )
                        }
                    }

                    if (metadataExpanded) {
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

                        Text("研究状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KnowledgeTrust.entries.forEach { knowledgeTrust ->
                                val accent = knowledgeTrustColor(knowledgeTrust)
                                FilterChip(
                                    selected = uiState.knowledgeTrust == knowledgeTrust,
                                    onClick = { onKnowledgeTrustChange(knowledgeTrust) },
                                    label = { Text(knowledgeTrust.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.14f),
                                        selectedLabelColor = accent,
                                    ),
                                )
                            }
                        }
                        Text(
                            text = "只有这条记录承载研究判断时再标记，系统会优先采用你的判断，而不是只靠内容猜测。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("时间尺度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NoteHorizon.entries.forEach { horizon ->
                                val accent = horizonColor(horizon)
                                FilterChip(
                                    selected = uiState.horizon == horizon,
                                    onClick = { onHorizonChange(horizon) },
                                    label = { Text("${horizon.label} · ${horizon.windowLabel}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.14f),
                                        selectedLabelColor = accent,
                                    ),
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { extraInfoExpanded = !extraInfoExpanded },
                        color = WhiteGlass.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "分类与标签",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = buildSupplementSummary(
                                        folderKey = uiState.folderKey,
                                        tagCount = uiState.tags.size,
                                        isNew = uiState.isNew,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (extraInfoExpanded) "收起" else "展开",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentBlue,
                            )
                        }
                    }

                    if (metadataExpanded && extraInfoExpanded) {
                        Text(
                            text = "先判断这条记录是短期推进、中期验证，还是长期经营；分类和标签按需要再补，不必一开始就填满。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        insights.suggestedThread?.let { suggestedThread ->
                            Text("方向提示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = WhiteGlass.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, BorderSoft),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "这条记录更像是在「${suggestedThread.title}」这条主线里。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (suggestedThread.focusLine.isNotBlank()) {
                                        Text(
                                            text = suggestedThread.focusLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentBlue,
                                        )
                                    }
                                    if (uiState.hasUnsavedChanges) {
                                        Text(
                                            text = "保存后可直接打开这条方向。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSoft,
                                        )
                                    } else {
                                        GhostActionButton(
                                            text = "打开方向",
                                            onClick = { onOpenSuggestedThread(suggestedThread.key) },
                                        )
                                    }
                                }
                            }
                        }

                        Text("旧知识召回", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            color = WhiteGlass.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "这块改成手动触发，避免一展开就悄悄拉起本地模型。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                GhostActionButton(
                                    text = if (knowledgeRecallUiState.isLoading) "召回中..." else "召回旧知识",
                                    onClick = { knowledgeRecallRequestVersion += 1 },
                                    enabled = !knowledgeRecallUiState.isLoading &&
                                        (insights.relatedNotes.isNotEmpty() || insights.suggestedThread != null),
                                )
                                if (
                                    !knowledgeRecallUiState.isLoading &&
                                    knowledgeRecallUiState.result == null &&
                                    insights.relatedNotes.isEmpty() &&
                                    insights.suggestedThread == null
                                ) {
                                    Text(
                                        text = "当前还没有足够上下文可召回，先补一点正文或标签。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSoft,
                                    )
                                }
                                knowledgeRecallUiState.result?.let { recall ->
                                    Text(
                                        text = recall.line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val providerLine = if (recall.usedOnDevice) {
                                        "本次召回由端侧完成。"
                                    } else {
                                        "本次没有命中可用端侧结果，已回退到规则说明。"
                                    }
                                    Text(
                                        text = providerLine,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (recall.usedOnDevice) AccentBlue else TextSoft,
                                    )
                                    recall.support.takeIf { it.isNotBlank() }?.let { support ->
                                        Text(
                                            text = support,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (recall.usedOnDevice) AccentBlue else TextSoft,
                                        )
                                    }
                                    if (!uiState.hasUnsavedChanges && recall.anchorNoteId != null) {
                                        GhostActionButton(
                                            text = recall.anchorLabel.ifBlank { "打开相关记录" },
                                            onClick = { onOpenRelatedNote(recall.anchorNoteId) },
                                        )
                                    }
                                }
                            }
                        }

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
                        } else {
                            Text(
                                text = "保存后会自动补主题和标签。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!uiState.isNew) {
                    PanelCard {
                        SectionHeader(
                            title = "记录信息",
                            headline = uiState.updatedAt?.let(TimeFormatter::compact).orEmpty(),
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { recordInfoExpanded = !recordInfoExpanded },
                            color = WhiteGlass.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("归档与时间", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (uiState.isArchived) {
                                            "已从首页隐藏 · ${uiState.statusHistory.size} 条状态变化"
                                        } else {
                                            "仍显示在首页 · ${uiState.statusHistory.size} 条状态变化"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = if (recordInfoExpanded) "收起" else "展开",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AccentBlue,
                                )
                            }
                        }
                        if (recordInfoExpanded) {
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
                    }

                    if (insights.relatedNotes.isNotEmpty()) {
                        PanelCard {
                            SectionHeader(title = "相关记录", headline = "${insights.relatedNotes.size} 条")
                            insights.relatedNotes.forEach { note ->
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
    expandToContainer: Boolean = false,
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
                .then(
                    if (expandToContainer) {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
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

private fun buildSupplementSummary(
    folderKey: String?,
    tagCount: Int,
    isNew: Boolean,
): String {
    val folderName = folderKey
        ?.let { key -> MindFolderCatalog.all.firstOrNull { it.key == key }?.name }
        ?: "未分类"
    return when {
        isNew -> "文件夹：$folderName · 标签保存后再补"
        tagCount > 0 -> "文件夹：$folderName · $tagCount 个标签"
        else -> "文件夹：$folderName · 还没有标签"
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
    TopicSource.AI -> "AI 生成"
    TopicSource.RULE -> "本地规则"
    TopicSource.MANUAL -> "已手动确认"
}

private fun tagSourceLabel(
    source: TagSource,
    count: Int,
): String {
    val prefix = when (source) {
        TagSource.AI -> "AI 提取"
        TagSource.RULE -> "本地规则"
        TagSource.MANUAL -> "已手动调整"
    }
    return "$prefix · $count/3"
}

private fun folderSourceLabel(source: com.mindflow.app.data.model.FolderSource): String = when (source) {
    com.mindflow.app.data.model.FolderSource.AI -> "AI 分类"
    com.mindflow.app.data.model.FolderSource.RULE -> "本地规则"
    com.mindflow.app.data.model.FolderSource.MANUAL -> "已手动确认"
}

private fun folderColor(folderKey: String): Color =
    Color(AndroidColor.parseColor(MindFolderCatalog.fromKey(folderKey)?.colorHex ?: "#64748B"))

private fun horizonColor(horizon: NoteHorizon): Color =
    when (horizon) {
        NoteHorizon.SHORT -> Color(0xFF0EA5E9)
        NoteHorizon.MEDIUM -> Color(0xFF7C3AED)
        NoteHorizon.LONG -> Color(0xFF0F766E)
    }

private fun knowledgeTrustColor(trust: KnowledgeTrust): Color =
    when (trust) {
        KnowledgeTrust.NONE -> Color(0xFF64748B)
        KnowledgeTrust.SIGNAL -> Color(0xFF0EA5E9)
        KnowledgeTrust.HYPOTHESIS -> Color(0xFFF59E0B)
        KnowledgeTrust.VERIFIED -> Color(0xFF7C3AED)
        KnowledgeTrust.VALIDATED -> Color(0xFF16A34A)
    }
