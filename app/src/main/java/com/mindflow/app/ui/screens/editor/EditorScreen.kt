package com.mindflow.app.ui.screens.editor

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
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
import com.mindflow.app.data.topic.ArticleContentExtractor
import com.mindflow.app.data.topic.ImageUnderstandingPlanner
import com.mindflow.app.data.topic.NoteInsightPlanner
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.data.topic.VoiceTranscriptionPlanner
import com.mindflow.app.ui.navigation.CaptureMode
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.MarkdownText
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.util.TimeFormatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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
        AiTaskType.POLISH_TITLE -> "润色标题"
        AiTaskType.SUMMARIZE_NOTE -> "整理洞察"
        AiTaskType.EXTRACT_TOPIC -> "整理主题"
        AiTaskType.EXTRACT_TAGS -> "整理标签"
        AiTaskType.CLASSIFY_CATEGORY -> "整理分类"
        AiTaskType.TRANSCRIBE_AUDIO -> "语音识别"
        AiTaskType.TRANSLATE_AUDIO -> "语音翻译"
        AiTaskType.UNDERSTAND_IMAGE -> "图片理解"
        AiTaskType.GRAPH_EXTRACT_CONCEPTS,
        AiTaskType.GRAPH_CANONICALIZE_CONCEPTS,
        AiTaskType.GRAPH_GENERATE_RELATIONS,
        AiTaskType.GRAPH_GENERATE_SNAPSHOT,
        AiTaskType.TEST_CONNECTION,
        AiTaskType.DAILY_BRIEF,
        AiTaskType.NEXT_ACTION,
        AiTaskType.WEEKLY_REVIEW,
        AiTaskType.FUSION_SUGGESTION,
        AiTaskType.FLOW_MAINLINE,
        AiTaskType.FLOW_SETTLED_KNOWLEDGE,
        AiTaskType.FLOW_BREAKTHROUGH_GAP,
        AiTaskType.THREAD_WORKSPACE,
        AiTaskType.RESEARCH_BRIEF,
        AiTaskType.RESEARCH_ACTION_SUMMARY,
        AiTaskType.THREAD_EXECUTION,
        AiTaskType.EXTERNAL_RESEARCH,
        AiTaskType.STALE_RECONNECT,
        AiTaskType.REVIEW_CHAT_REPLY,
        AiTaskType.REVIEW_CHAT_QUERY_PLAN,
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

internal fun textContentEditorMinLines(content: String): Int {
    val explicitLines = content.lineSequence().count().coerceAtLeast(1)
    return explicitLines.coerceIn(3, 6)
}

internal fun voiceTranscriptEditorMinLines(transcript: String): Int {
    val explicitLines = transcript.lineSequence().count().coerceAtLeast(1)
    val lengthLines = when {
        transcript.length >= 420 -> 8
        transcript.length >= 220 -> 6
        else -> 4
    }
    return maxOf(explicitLines, lengthLines).coerceIn(4, 10)
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
    topicExtractor: TopicExtractor,
    noteInsightPlanner: NoteInsightPlanner,
    voiceTranscriptionPlanner: VoiceTranscriptionPlanner,
    articleContentExtractor: ArticleContentExtractor,
    imageUnderstandingPlanner: ImageUnderstandingPlanner,
    editorKnowledgeRecallPlanner: EditorKnowledgeRecallPlanner,
    noteId: Long?,
    captureSessionKey: Long? = null,
    captureMode: CaptureMode = CaptureMode.TEXT,
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
            topicExtractor = topicExtractor,
            noteInsightPlanner = noteInsightPlanner,
            voiceTranscriptionPlanner = voiceTranscriptionPlanner,
            articleContentExtractor = articleContentExtractor,
            imageUnderstandingPlanner = imageUnderstandingPlanner,
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
    var inlineVoiceRequested by rememberSaveable(noteId, captureSessionKey) { mutableStateOf(false) }
    val launchVoiceCapture = remember {
        { inlineVoiceRequested = true }
    }
    val launchedAsVoiceCapture = uiState.isNew && captureMode == CaptureMode.VOICE
    val showVoiceCapture = shouldShowVoiceCaptureScreen(
        isNew = uiState.isNew,
        launchCaptureMode = captureMode,
        inlineVoiceRequested = inlineVoiceRequested,
    )
    val leaveVoiceCapture: () -> Unit = {
        if (inlineVoiceRequested && !launchedAsVoiceCapture) {
            inlineVoiceRequested = false
        } else {
            onBack()
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

    if (showVoiceCapture) {
        VoiceCaptureScreen(
            uiState = uiState,
            autoStartRecording = shouldAutoStartVoiceCapture(
                launchAutoStart = autoStartVoiceInput,
                inlineVoiceRequested = inlineVoiceRequested,
            ),
            cleanupFilesOnLeaveWithoutSaving = launchedAsVoiceCapture,
            onBack = leaveVoiceCapture,
            onContentChange = viewModel::onContentChange,
            onEnsureVoiceInsight = viewModel::ensureVoiceAiInsight,
            onEnsureVoiceTranscription = viewModel::ensureVoiceTranscription,
            onPrepareNewVoiceRecording = viewModel::prepareNewVoiceRecording,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onCaptureAction = viewModel::saveWithCaptureAction,
        )
    } else if (uiState.isNew && captureMode == CaptureMode.ARTICLE) {
        ArticleCaptureScreen(
            uiState = uiState,
            onBack = onBack,
            onContentChange = viewModel::onContentChange,
            onEnsureArticleExtraction = viewModel::ensureArticleExtraction,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onCaptureAction = viewModel::saveWithCaptureAction,
        )
    } else if (uiState.isNew && captureMode == CaptureMode.IMAGE) {
        ImageCaptureScreen(
            uiState = uiState,
            onBack = onBack,
            onContentChange = viewModel::onContentChange,
            onEnsureImageUnderstanding = viewModel::ensureImageUnderstanding,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onCaptureAction = viewModel::saveWithCaptureAction,
        )
    } else if (uiState.isNew) {
        CaptureEditorScreen(
            uiState = uiState,
            onBack = onBack,
            onContentChange = viewModel::onContentChange,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
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
            onStatusChange = viewModel::onStatusChange,
            onArchiveChange = viewModel::onArchivedChange,
            onSave = { viewModel.save(exitAfterSave = false) },
            onSaveAndExit = { viewModel.save(exitAfterSave = true) },
            onPolishContent = viewModel::polishContent,
            onGenerateTitle = viewModel::generateTitle,
            onCaptureTypeSelect = viewModel::onCaptureTypeChange,
            onApplyPolishedContent = viewModel::applyPolishedContent,
            onDiscardPolishedContent = viewModel::discardPolishedContent,
            onEnsureVoiceTranscription = { viewModel.ensureVoiceTranscription() },
            onEnsureArticleExtraction = { viewModel.ensureArticleExtraction() },
            onRetriggerFolder = viewModel::retriggerFolderClassification,
            onRetriggerTopic = viewModel::retriggerTopicExtraction,
            onRetriggerTag = viewModel::retriggerTagExtraction,
            onOpenSuggestedThread = onOpenThread,
            onOpenRelatedNote = onOpenNote,
        )
    }
}

internal fun shouldShowVoiceCaptureScreen(
    isNew: Boolean,
    launchCaptureMode: CaptureMode,
    inlineVoiceRequested: Boolean,
): Boolean = inlineVoiceRequested || (isNew && launchCaptureMode == CaptureMode.VOICE)

internal fun shouldAutoStartVoiceCapture(
    launchAutoStart: Boolean,
    inlineVoiceRequested: Boolean,
): Boolean = launchAutoStart

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
) {
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val writingCardHeight = remember(screenHeightDp) { textCaptureWritingCardHeight(screenHeightDp) }
    val sectionSpacing = if (screenHeightDp < 720) 8.dp else 10.dp
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
                leaveNewCaptureWithoutSaving(context, uiState, onBack)
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
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            EditorTopBar(
                title = "记录",
                onBack = ::requestBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                Text(
                    text = "内容",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CaptureWritingCard(
                    uiState = uiState,
                    onContentChange = onContentChange,
                    height = writingCardHeight,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InputStickyActionBar(
                    actions = listOf(
                        InputActionSpec(
                            text = if (uiState.isSaving) "保存中..." else "保存",
                            icon = Icons.Outlined.Check,
                            onClick = onSave,
                            enabled = !uiState.isSaving && uiState.content.isNotBlank(),
                            primary = true,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CaptureWritingCard(
    uiState: NoteEditorUiState,
    onContentChange: (String) -> Unit,
    height: Dp,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PaperField(
                value = uiState.content,
                onValueChange = onContentChange,
                placeholder = "写下此刻想记录的内容。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 26.dp)
                    .testTag(CaptureContentFieldTestTag),
                minLines = 7,
                maxLines = 18,
                textStyle = MaterialTheme.typography.bodyMedium,
                expandToContainer = true,
            )
            Text(
                text = "${uiState.content.length} 字",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun textCaptureWritingCardHeight(screenHeightDp: Int): Dp = when {
    screenHeightDp < 700 -> 300.dp
    screenHeightDp < 820 -> 348.dp
    else -> 384.dp
}

private enum class CaptureType(
    val label: String,
    val icon: ImageVector,
) {
    IDEA("想法", Icons.Outlined.AutoFixHigh),
    TASK("任务", Icons.Outlined.CheckCircle),
    DOCUMENT("文档", Icons.AutoMirrored.Outlined.Article),
    SPARK("灵感", Icons.Outlined.Edit),
}

@Composable
private fun CaptureTypeChip(
    type: CaptureType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (selected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent,
            shape = RoundedCornerShape(999.dp),
            border = if (selected) BorderStroke(1.dp, AccentBlue.copy(alpha = 0.28f)) else null,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun inferCaptureType(uiState: NoteEditorUiState): CaptureType {
    val tagSet = uiState.tags.toSet()
    val haystack = (uiState.content + " " + uiState.topic).lowercase()
    return when {
        "想法" in tagSet -> CaptureType.IDEA
        "任务" in tagSet -> CaptureType.TASK
        "文档" in tagSet -> CaptureType.DOCUMENT
        "灵感" in tagSet -> CaptureType.SPARK
        uiState.status == NoteStatus.IN_PROGRESS || uiState.status == NoteStatus.DONE || "任务" in haystack || "todo" in haystack ->
            CaptureType.TASK
        "文档" in haystack || "资料" in haystack || "链接" in haystack || "http://" in haystack || "https://" in haystack ->
            CaptureType.DOCUMENT
        "灵感" in haystack || "spark" in haystack ->
            CaptureType.SPARK
        else -> CaptureType.IDEA
    }
}

private fun textInputPreviewTags(content: String): List<String> {
    val haystack = content.lowercase()
    val tags = buildList {
        add("文本")
        if ("产品" in haystack || "用户" in haystack) add("产品")
        if ("ai" in haystack || "AI" in content || "智能" in content) add("AI")
        if ("设计" in haystack || "体验" in content) add("体验")
        if (size == 1) add("待整理")
    }
    return tags.distinct().take(3)
}

@Composable
internal fun ArticleCaptureScreen(
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onEnsureArticleExtraction: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onCaptureAction: (CapturePostAction) -> Unit,
) {
    val context = LocalContext.current
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val model = remember(uiState.topic, uiState.content, uiState.tags, uiState.updatedAt) {
        buildArticleCaptureModel(
            topic = uiState.topic,
            content = uiState.content,
            tags = uiState.tags,
            updatedAt = uiState.updatedAt,
        )
    }
    val urlValue = remember(uiState.content) { articleUrlFromContent(uiState.content) }
    val articleBody = remember(uiState.content) { articleBodyFromContent(uiState.content) }
    val noteValue = remember(uiState.content) { extractCaptureField(uiState.content, ArticleNoteFieldLabel) }
    val extractStatus = remember(uiState.content) { articleStatusFromContent(uiState.content) }
    val canExtractArticle = !uiState.isExtractingArticle && urlValue.isNotBlank() && articleBody.isBlank()
    val extractActionText = when {
        uiState.isExtractingArticle -> "提取中..."
        articleBody.isNotBlank() -> "正文已填"
        else -> "解析正文"
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
                leaveNewCaptureWithoutSaving(context, uiState, onBack)
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
                title = "链接输入",
                onBack = ::requestBack,
                actionIcon = Icons.Outlined.MoreVert,
                actionContentDescription = "更多",
                onAction = {},
                actionAccent = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ArticleSourceCard(model = model)
                ArticleSectionCard(title = "链接") {
                    PaperField(
                        value = urlValue,
                        onValueChange = { value ->
                            onContentChange(replaceCaptureField(uiState.content, ArticleUrlFieldLabel, value))
                        },
                        placeholder = "粘贴网页链接",
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionButton(
                            text = extractActionText,
                            onClick = { onEnsureArticleExtraction(urlValue) },
                            enabled = canExtractArticle,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.AutoFixHigh,
                        )
                    }
                    if (extractStatus.isNotBlank()) {
                        Text(
                            text = extractStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (extractStatus.startsWith("提取失败")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                ArticleSectionCard(title = "正文内容") {
                    PaperField(
                        value = articleBody,
                        onValueChange = { value ->
                            onContentChange(replaceCaptureField(uiState.content, ArticleBodyFieldLabel, value))
                        },
                        placeholder = if (uiState.isExtractingArticle) {
                            "正在提取网页正文…"
                        } else {
                            "解析后正文会显示在这里，也可以手动粘贴正文。"
                        },
                        minLines = 7,
                        maxLines = 16,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
                ArticleSectionCard(title = "AI 洞察") {
                    val hasInsight = uiState.aiSummary.isNotBlank() || uiState.aiKeyPoints.isNotEmpty()
                    if (hasInsight) {
                        uiState.aiSummary.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.aiKeyPoints.forEach { point -> BulletText(text = point) }
                        }
                    } else {
                        AiInsightPendingRow(
                            text = if (articleBody.isBlank()) {
                                "正文提取后自动生成洞察"
                            } else {
                                "保存后继续整理为 AI 洞察"
                            },
                        )
                    }
                }
                ArticleSectionCard(title = "相关主题") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        model.topics.forEach { topic ->
                            AssistChip(text = topic, accent = AccentBlue)
                        }
                    }
                }
                ArticleSectionCard(title = "补充说明") {
                    PaperField(
                        value = noteValue,
                        onValueChange = { value ->
                            onContentChange(replaceCaptureField(uiState.content, ArticleNoteFieldLabel, value))
                        },
                        placeholder = "为什么保存这篇内容，或下一步想怎么用。",
                        minLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            CaptureStickyActionBar(
                isSaving = uiState.isSaving,
                canSave = uiState.content.isNotBlank(),
                primaryText = "保存链接",
                secondaryText = extractActionText,
                secondaryIcon = Icons.Outlined.AutoFixHigh,
                secondaryEnabled = canExtractArticle,
                onSecondary = { onEnsureArticleExtraction(urlValue) },
                onSave = onSave,
                onCaptureAction = onCaptureAction,
            )
        }
    }
}

@Composable
internal fun VoiceCaptureScreen(
    uiState: NoteEditorUiState,
    autoStartRecording: Boolean = false,
    cleanupFilesOnLeaveWithoutSaving: Boolean = false,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onEnsureVoiceInsight: () -> Unit,
    onEnsureVoiceTranscription: (String) -> Unit,
    onPrepareNewVoiceRecording: () -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onCaptureAction: (CapturePostAction) -> Unit,
) {
    val context = LocalContext.current
    val model = remember(uiState.topic, uiState.content, uiState.tags, uiState.updatedAt) {
        buildVoiceCaptureModel(
            topic = uiState.topic,
            content = uiState.content,
            tags = uiState.tags,
            updatedAt = uiState.updatedAt,
        )
    }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var recordingState by rememberSaveable { mutableStateOf(VoiceRecordingState.IDLE) }
    var recordedPath by rememberSaveable(uiState.content) { mutableStateOf(extractCaptureField(uiState.content, "原始录音")) }
    var elapsedSeconds by rememberSaveable { mutableStateOf(0) }
    var recordingStartedAt by rememberSaveable { mutableStateOf(0L) }
    var autoStartConsumed by rememberSaveable { mutableStateOf(false) }
    var amplitudeLevel by rememberSaveable { mutableStateOf(0f) }
    var waveformTick by rememberSaveable { mutableStateOf(0) }
    val recorder = remember(context.applicationContext) { VoiceCaptureRecorder(context.applicationContext) }
    val transcriptText = remember(uiState.content) { voiceTranscriptFromContent(uiState.content) }
    val recognitionStatus = remember(uiState.content) { voiceRecognitionStatusFromContent(uiState.content) }
    val fallbackVoiceAiSummary = remember(uiState.content) { extractCaptureField(uiState.content, VoiceAiSummaryFieldLabel) }
    val fallbackVoiceKeyPoints = remember(uiState.content) { voiceKeyPointsFromContent(uiState.content) }
    val voiceAiSummary = uiState.aiSummary.ifBlank { fallbackVoiceAiSummary }
    val voiceKeyPoints = if (uiState.aiKeyPoints.isNotEmpty()) uiState.aiKeyPoints else fallbackVoiceKeyPoints

    fun requestBack() {
        if (uiState.hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    fun startRecording() {
        val previousPath = recordedPath
        runCatching {
            recorder.start()
        }.onSuccess {
            deleteUnsavedVoiceRecordingForRetake(context, uiState, previousPath)
            onPrepareNewVoiceRecording()
            recordingStartedAt = System.currentTimeMillis()
            elapsedSeconds = 0
            recordedPath = it.absolutePath
            recordingState = VoiceRecordingState.RECORDING
        }.onFailure {
            recordingState = VoiceRecordingState.IDLE
            Toast.makeText(context, "暂时无法开始录音", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(context, "需要录音权限才能保存原始音频", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecording() {
        val file = recorder.stop()
        recordingState = if (file != null) VoiceRecordingState.SAVED else VoiceRecordingState.IDLE
        if (file == null) {
            Toast.makeText(context, "录音太短，未保存文件", Toast.LENGTH_SHORT).show()
            return
        }
        recordedPath = file.absolutePath
        val contentForNewRecording = clearVoiceCaptureForNewRecording(uiState.content)
        onContentChange(
            replaceCaptureField(
                ensureCaptureSections(
                    replaceCaptureField(contentForNewRecording, "原始录音", file.absolutePath),
                    listOf(VoiceTranscriptFieldLabel, VoiceRecognitionFieldLabel),
                ),
                VoiceRecognitionFieldLabel,
                "正在转写音频…",
            ),
        )
        onEnsureVoiceTranscription(file.absolutePath)
        Toast.makeText(context, "原始录音已保存", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(recordingState, recordingStartedAt) {
        while (recordingState == VoiceRecordingState.RECORDING && isActive) {
            elapsedSeconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000L).toInt().coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(recordingState) {
        while (recordingState == VoiceRecordingState.RECORDING && isActive) {
            val measuredLevel = recorder.maxAmplitudeLevel()
            val voiceLevel = normalizeVoiceAmplitude(measuredLevel)
            amplitudeLevel = maxOf(voiceLevel, amplitudeLevel * 0.55f)
            if (voiceLevel > 0.02f) {
                waveformTick += 1
            }
            delay(90)
        }
        amplitudeLevel = 0f
    }

    LaunchedEffect(autoStartRecording, autoStartConsumed) {
        if (autoStartRecording && !autoStartConsumed && recordingState == VoiceRecordingState.IDLE) {
            autoStartConsumed = true
            requestStartRecording()
        }
    }

    LaunchedEffect(transcriptText, voiceAiSummary, voiceKeyPoints, uiState.isExtractingVoiceInfo) {
        if (
            transcriptText.isNotBlank() &&
            voiceAiSummary.isBlank() &&
            voiceKeyPoints.isEmpty() &&
            !uiState.isExtractingVoiceInfo
        ) {
            delay(700)
            onEnsureVoiceInsight()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
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
                if (cleanupFilesOnLeaveWithoutSaving) {
                    cleanupCaptureFilesForDiscard(context, uiState.content)
                }
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
                title = model.title,
                onBack = ::requestBack,
                actionIcon = Icons.Outlined.MoreVert,
                actionContentDescription = "更多",
                onAction = {},
                actionAccent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VoiceRecorderPanel(
                    state = recordingState,
                    elapsedSeconds = elapsedSeconds,
                    recordedPath = recordedPath,
                    amplitudeLevel = amplitudeLevel,
                    waveformTick = waveformTick,
                    onStart = ::requestStartRecording,
                    onPause = {
                        recorder.pause()
                        recordingState = VoiceRecordingState.PAUSED
                    },
                    onResume = {
                        recorder.resume()
                        recordingState = VoiceRecordingState.RECORDING
                    },
                    onStop = ::stopRecording,
                )
                ArticleSectionCard(title = "原始内容信息") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "转写内容",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatRecordingDuration(elapsedSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PaperField(
                        value = transcriptText,
                        onValueChange = { value ->
                            onContentChange(
                                replaceCaptureField(uiState.content, VoiceTranscriptFieldLabel, value),
                            )
                        },
                        placeholder = voiceTranscriptPlaceholder(
                            hasRecording = !recordedPath.isNullOrBlank(),
                            recordingState = recordingState.name,
                            isTranscribing = uiState.isTranscribingVoice,
                        ),
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    voiceTranscriptHelperText(
                        recognitionStatus = recognitionStatus,
                        isTranscribing = uiState.isTranscribingVoice,
                        transcript = transcriptText,
                    )?.let { helperText ->
                        Text(
                            text = helperText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (helperText.startsWith("转写失败")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                VoiceRecognitionStatusStrip(
                    items = listOf(
                        "音频" to if (recordedPath.isNullOrBlank()) "未保存" else "已暂存",
                        "转写" to voiceTranscriptStatus(
                            hasRecording = !recordedPath.isNullOrBlank(),
                            transcript = transcriptText,
                            isTranscribing = uiState.isTranscribingVoice,
                            recognitionStatus = recognitionStatus,
                        ),
                        "时长" to formatRecordingDuration(elapsedSeconds),
                        "整理" to if (voiceAiSummary.isBlank()) "待提取" else "已提取",
                    ),
                )
                ArticleSectionCard(title = "AI 洞察") {
                    if (voiceAiSummary.isNotBlank() || voiceKeyPoints.isNotEmpty()) {
                        voiceAiSummary.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            voiceKeyPoints.forEach { point -> BulletText(text = point) }
                        }
                    } else {
                        AiInsightPendingRow(
                            text = if (uiState.isExtractingVoiceInfo) {
                                "正在整理这段语音"
                            } else {
                                "转写完成后自动生成洞察"
                            },
                        )
                    }
                }
            }
            InputStickyActionBar(
                actions = listOf(
                    InputActionSpec(
                        text = "删除",
                        icon = Icons.Outlined.Close,
                        onClick = {
                            if (recordingState == VoiceRecordingState.RECORDING || recordingState == VoiceRecordingState.PAUSED) {
                                recorder.stop()?.delete()
                                recordingState = VoiceRecordingState.IDLE
                            }
                            leaveNewCaptureWithoutSaving(context, uiState, onBack)
                        },
                    ),
                    InputActionSpec(
                        text = "继续录入",
                        icon = Icons.Outlined.Mic,
                        onClick = ::requestStartRecording,
                    ),
                    InputActionSpec(
                        text = when {
                            uiState.isSaving -> "保存中..."
                            uiState.isTranscribingVoice -> "转写中..."
                            else -> "完成解析"
                        },
                        icon = Icons.Outlined.Check,
                        onClick = onSave,
                        enabled = !uiState.isSaving && !uiState.isTranscribingVoice && uiState.content.isNotBlank(),
                        primary = true,
                    ),
                ),
            )
        }
    }
}

@Composable
internal fun ImageCaptureScreen(
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onEnsureImageUnderstanding: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onCaptureAction: (CapturePostAction) -> Unit,
) {
    val context = LocalContext.current
    val model = remember(uiState.topic, uiState.content, uiState.tags, uiState.updatedAt) {
        buildImageCaptureModel(
            topic = uiState.topic,
            content = uiState.content,
            tags = uiState.tags,
            updatedAt = uiState.updatedAt,
        )
    }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var selectedImagePath by rememberSaveable(uiState.content) { mutableStateOf(extractCaptureField(uiState.content, "图片")) }
    val imageBitmap by rememberImageBitmap(selectedImagePath)
    val imageSummary = remember(uiState.content) { imageSummaryFromContent(uiState.content) }
    val imageKeyInfo = remember(uiState.content) { imageKeyInfoFromContent(uiState.content) }
    val imageOcr = remember(uiState.content) { imageOcrFromContent(uiState.content) }
    val imageRecognitionStatus = remember(uiState.content) { extractCaptureField(uiState.content, ImageRecognitionFieldLabel) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { copyPickedImageToPrivateStorage(context, uri) }
            .onSuccess { file ->
                selectedImagePath = file.absolutePath
                onContentChange(
                    ensureCaptureSections(
                        replaceCaptureField(uiState.content, ImagePathFieldLabel, file.absolutePath),
                        listOf(
                            ImageSummaryFieldLabel,
                            ImageKeyInfoFieldLabel,
                            ImageObjectsFieldLabel,
                            ImageOcrFieldLabel,
                            ImageRecognitionFieldLabel,
                        ),
                    ),
                )
                onEnsureImageUnderstanding(file.absolutePath)
                Toast.makeText(context, "图片已保存到本地", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "暂时无法读取这张图片", Toast.LENGTH_SHORT).show()
            }
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
                leaveNewCaptureWithoutSaving(context, uiState, onBack)
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
                title = model.title,
                onBack = ::requestBack,
                actionIcon = Icons.Outlined.MoreVert,
                actionContentDescription = "更多",
                onAction = {},
                actionAccent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ArticleSectionCard(title = "图片预览") {
                    ImagePickerPanel(
                        imageBitmap = imageBitmap,
                        selectedImagePath = selectedImagePath,
                        onPickImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                }
                ArticleSectionCard(title = "图像理解结果") {
                    PaperField(
                        value = imageSummary,
                        onValueChange = { value ->
                            onContentChange(replaceCaptureField(uiState.content, ImageSummaryFieldLabel, value))
                        },
                        placeholder = if (uiState.isUnderstandingImage) {
                            "正在理解图片…"
                        } else {
                            "图片理解摘要会显示在这里，也可以手动补充。"
                        },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    if (imageRecognitionStatus.isNotBlank()) {
                        Text(
                            text = imageRecognitionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (imageRecognitionStatus.startsWith("识别失败")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                ArticleSectionCard(title = "关键信息提取") {
                    if (imageKeyInfo.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            imageKeyInfo.forEach { point -> BulletText(text = point) }
                        }
                    } else {
                        AiInsightPendingRow(
                            text = if (selectedImagePath.isNullOrBlank()) {
                                "选择图片后自动提取关键信息"
                            } else {
                                "等待图片理解结果"
                            },
                        )
                    }
                }
                ArticleSectionCard(title = "结构化识别") {
                    val objectSummary = extractCaptureField(uiState.content, ImageObjectsFieldLabel)
                    RecognitionInfoGrid(
                        items = listOf(
                            "类型" to objectSummary.ifBlank { "待识别" },
                            "主体" to imageKeyInfo.firstOrNull().orEmpty().ifBlank { "待提取" },
                            "文字" to if (imageOcr.isBlank()) "待 OCR" else "已提取",
                            "状态" to when {
                                uiState.isUnderstandingImage -> "识别中"
                                imageSummary.isNotBlank() || imageOcr.isNotBlank() -> "已完成"
                                selectedImagePath.isNullOrBlank() -> "待导入"
                                else -> "待解析"
                            },
                        ),
                    )
                }
                ArticleSectionCard(title = "OCR 文本(可选)") {
                    PaperField(
                        value = imageOcr,
                        onValueChange = { value ->
                            onContentChange(replaceCaptureField(uiState.content, ImageOcrFieldLabel, value))
                        },
                        placeholder = model.sourcePlaceholder,
                        minLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            InputStickyActionBar(
                actions = listOf(
                    InputActionSpec(
                        text = "重新拍摄",
                        icon = Icons.Outlined.Image,
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ),
                    InputActionSpec(
                        text = "从相册导入",
                        icon = Icons.Outlined.Add,
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ),
                    InputActionSpec(
                        text = if (uiState.isSaving) "保存中..." else "继续解析",
                        icon = Icons.Outlined.Check,
                        onClick = onSave,
                        enabled = !uiState.isSaving && !uiState.isUnderstandingImage && uiState.content.isNotBlank(),
                        primary = true,
                    ),
                ),
            )
        }
    }
}

private enum class VoiceRecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    SAVED,
}

internal fun voiceTranscriptPlaceholder(
    hasRecording: Boolean,
    recordingState: String,
    isTranscribing: Boolean = false,
): String = when (recordingState) {
    VoiceRecordingState.RECORDING.name -> "录音中，结束后会保存音频。"
    VoiceRecordingState.PAUSED.name -> "录音已暂停，结束后会保存音频。"
    else -> if (isTranscribing) {
        "正在转写…"
    } else if (hasRecording) {
        "转写暂未完成，可先手动补充内容。"
    } else {
        "录音后，转写内容会显示在这里。"
    }
}

internal fun voiceTranscriptStatus(
    hasRecording: Boolean,
    transcript: String,
    isTranscribing: Boolean = false,
    recognitionStatus: String = "",
): String = when {
    transcript.isNotBlank() -> "可编辑"
    isTranscribing -> "转写中"
    recognitionStatus.startsWith("转写失败") -> "失败"
    hasRecording -> "待转写"
    else -> "待录音"
}

internal fun voiceTranscriptHelperText(
    recognitionStatus: String,
    isTranscribing: Boolean,
    transcript: String,
): String? = when {
    transcript.isNotBlank() -> null
    isTranscribing -> "正在调用 Gemma 4 端侧转写"
    recognitionStatus.startsWith("转写失败") -> voiceRecognitionStatusForDisplay(recognitionStatus)
    recognitionStatus.isNotBlank() && recognitionStatus != "正在转写音频…" -> recognitionStatus
    else -> null
}

internal fun voiceRecognitionStatusForDisplay(status: String): String =
    if (status.contains("Gemma 4 模型已就绪")) {
        "转写失败：旧版录音格式未被端侧音频输入识别；重新录音后会使用 16kHz WAV 转写"
    } else {
        status
    }

@Composable
private fun VoiceRecorderPanel(
    state: VoiceRecordingState,
    elapsedSeconds: Int,
    recordedPath: String?,
    amplitudeLevel: Float,
    waveformTick: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordingWaveform(
                active = state == VoiceRecordingState.RECORDING,
                amplitudeLevel = amplitudeLevel,
                tick = waveformTick,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .size(58.dp)
                    .background(AccentBlue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(30.dp),
                )
            }
            RecordingWaveform(
                active = state == VoiceRecordingState.RECORDING,
                amplitudeLevel = amplitudeLevel,
                tick = waveformTick + 4,
            )
        }
        Text(
            text = when (state) {
                VoiceRecordingState.RECORDING -> "正在聆听中..."
                VoiceRecordingState.PAUSED -> "录音已暂停"
                VoiceRecordingState.SAVED -> "原始录音已保存"
                VoiceRecordingState.IDLE -> "准备录音"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                VoiceRecordingState.IDLE,
                VoiceRecordingState.SAVED,
                -> GhostActionButton(
                    text = if (state == VoiceRecordingState.SAVED) "重新录音" else "开始录音",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Mic,
                )
                VoiceRecordingState.RECORDING -> {
                    GhostActionButton(
                        text = "暂停",
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        text = "结束录音",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Check,
                    )
                }
                VoiceRecordingState.PAUSED -> {
                    GhostActionButton(
                        text = "继续",
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        text = "结束录音",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Check,
                    )
                }
            }
        }
        if (!recordedPath.isNullOrBlank()) {
            Text(
                text = "本地文件：${File(recordedPath).name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecordingWaveform(
    active: Boolean,
    amplitudeLevel: Float = 0f,
    tick: Int = 0,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val level = if (active) amplitudeLevel.coerceIn(0f, 1f) else 0f
        val quietProfile = listOf(16f, 18f, 20f, 22f, 24f, 22f, 20f, 18f, 16f)
        val heights = if (active && level > 0.01f) {
            List(9) { index ->
                val pulse = ((index * 31 + tick * 23) % 100) / 100f
                val dynamicHeight = quietProfile[index] + (pulse * 34f * level)
                dynamicHeight.dp
            }
        } else {
            quietProfile.map { it.dp }
        }
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .background(AccentBlue.copy(alpha = 0.45f), RoundedCornerShape(999.dp)),
            )
        }
    }
}

internal fun normalizeVoiceAmplitude(rawLevel: Float): Float {
    val noiseFloor = 0.04f
    if (rawLevel <= noiseFloor) return 0f
    val normalized = ((rawLevel - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
    return kotlin.math.sqrt(normalized)
}

@Composable
private fun ImagePickerPanel(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    selectedImagePath: String?,
    onPickImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "图片预览",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            color = AccentBlue.copy(alpha = 0.08f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = "选择图片后在这里预览",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        ActionButton(
            text = "选择图片",
            onClick = onPickImage,
            icon = Icons.Outlined.Image,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!selectedImagePath.isNullOrBlank()) {
            Text(
                text = "本地文件：${File(selectedImagePath).name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedTopicSection(topics: List<String>) {
    ArticleSectionCard(title = "相关主题") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topics.forEach { topic ->
                AssistChip(text = topic, accent = AccentBlue)
            }
        }
    }
}

private data class InputActionSpec(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val primary: Boolean = false,
)

@Composable
private fun InputStickyActionBar(actions: List<InputActionSpec>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                if (action.primary) {
                    ActionButton(
                        text = action.text,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                        icon = action.icon,
                    )
                } else {
                    GhostActionButton(
                        text = action.text,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                        icon = action.icon,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentReferenceActionRow(
    onInsertToday: () -> Unit,
    onLinkTask: () -> Unit,
    onImportProject: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArticleActionButton(
            icon = Icons.Outlined.CalendarMonth,
            text = "插入今天",
            accent = Color(0xFF4C7EFF),
            onClick = onInsertToday,
            modifier = Modifier.weight(1f),
        )
        ArticleActionButton(
            icon = Icons.Outlined.CheckCircle,
            text = "链接任务",
            accent = Color(0xFF8B6CFF),
            onClick = onLinkTask,
            modifier = Modifier.weight(1f),
        )
        ArticleActionButton(
            icon = Icons.Outlined.Folder,
            text = "导入项目",
            accent = Color(0xFF3CBF7C),
            onClick = onImportProject,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecognitionInfoGrid(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    MetaRow(label, value, Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VoiceRecognitionStatusStrip(items: List<Pair<String, String>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        RecognitionInfoGrid(
            items = items,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CaptureStickyActionBar(
    isSaving: Boolean,
    canSave: Boolean,
    primaryText: String,
    secondaryText: String,
    secondaryIcon: ImageVector,
    secondaryEnabled: Boolean = true,
    onSecondary: () -> Unit,
    onSave: () -> Unit,
    onCaptureAction: (CapturePostAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArticleActionButton(
                    icon = Icons.Outlined.CalendarMonth,
                    text = "插入今天",
                    accent = Color(0xFF4C7EFF),
                    onClick = { onCaptureAction(CapturePostAction.ADD_TO_TODAY) },
                    modifier = Modifier.weight(1f),
                )
                ArticleActionButton(
                    icon = Icons.Outlined.CheckCircle,
                    text = "链接任务",
                    accent = Color(0xFF8B6CFF),
                    onClick = { onCaptureAction(CapturePostAction.CONVERT_TO_TASK) },
                    modifier = Modifier.weight(1f),
                )
                ArticleActionButton(
                    icon = Icons.Outlined.Folder,
                    text = "导入项目",
                    accent = Color(0xFF3CBF7C),
                    onClick = { onCaptureAction(CapturePostAction.ADD_TO_PROJECT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostActionButton(
                    text = secondaryText,
                    onClick = onSecondary,
                    enabled = secondaryEnabled,
                    modifier = Modifier.weight(1f),
                    icon = secondaryIcon,
                )
                ActionButton(
                    text = if (isSaving) "保存中..." else primaryText,
                    onClick = onSave,
                    enabled = !isSaving && canSave,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Check,
                )
            }
        }
    }
}

@Composable
private fun rememberImageBitmap(path: String?): androidx.compose.runtime.State<androidx.compose.ui.graphics.ImageBitmap?> =
    produceState(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf(File::exists)
                ?.let { file -> BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
        }
    }

private fun copyPickedImageToPrivateStorage(
    context: Context,
    uri: Uri,
): File {
    val mimeType = context.contentResolver.getType(uri).orEmpty()
    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType)
        ?.takeIf { it.isNotBlank() }
        ?: "jpg"
    val directory = File(context.filesDir, "captures/images").apply { mkdirs() }
    val target = File(directory, "image-${System.currentTimeMillis()}.$extension")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot open picked image" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

private fun leaveNewCaptureWithoutSaving(
    context: Context,
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
) {
    if (uiState.isNew) {
        cleanupCaptureFilesForDiscard(context, uiState.content)
    }
    onBack()
}

private fun cleanupCaptureFilesForDiscard(
    context: Context,
    content: String,
) {
    capturePrivateFilesForDiscard(content, context.applicationContext.filesDir).forEach { file ->
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

private fun deleteUnsavedVoiceRecordingForRetake(
    context: Context,
    uiState: NoteEditorUiState,
    previousPath: String?,
) {
    if (!uiState.isNew) return
    val path = previousPath
        ?.takeIf { it.isNotBlank() }
        ?: extractCaptureField(uiState.content, VoiceAudioFieldLabel).takeIf { it.isNotBlank() }
        ?: return
    val voiceRoot = File(context.applicationContext.filesDir, "captures/voice").canonicalFile
    val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return
    if (!file.isInsideDirectory(voiceRoot)) return
    runCatching {
        if (file.exists()) {
            file.delete()
        }
    }
}

internal fun capturePrivateFilesForDiscard(
    content: String,
    appFilesDir: File,
): List<File> {
    val captureRoot = File(appFilesDir, "captures").canonicalFile
    return listOf("原始录音", "图片")
        .mapNotNull { label -> extractCaptureField(content, label).takeIf(String::isNotBlank) }
        .distinct()
        .map { path -> File(path).canonicalFile }
        .filter { file -> file.isInsideDirectory(captureRoot) }
}

private fun File.isInsideDirectory(directory: File): Boolean {
    var current: File? = this
    while (current != null) {
        if (current == directory) return true
        current = current.parentFile
    }
    return false
}

private fun extractCaptureField(
    content: String,
    label: String,
): String = content
    .lineSequence()
    .map { it.trim() }
    .mapNotNull { line ->
        when {
            line.startsWith("$label：") -> line.removePrefix("$label：")
            line.startsWith("$label:") -> line.removePrefix("$label:")
            else -> null
        }
    }
    .firstOrNull()
    ?.trim()
    .orEmpty()

private fun replaceCaptureField(
    content: String,
    label: String,
    value: String,
): String {
    val lines = content.lines().toMutableList()
    val index = lines.indexOfFirst { line ->
        val trimmed = line.trim()
        trimmed.startsWith("$label：") || trimmed.startsWith("$label:")
    }
    val replacement = "$label：$value"
    return if (index >= 0) {
        lines[index] = replacement
        lines.joinToString("\n").trimEnd()
    } else {
        listOf(content.trimEnd(), replacement)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}

private fun mergeCaptureField(
    editableContent: String,
    label: String,
    value: String,
): String =
    if (value.isBlank()) {
        editableContent.trimEnd()
    } else {
        replaceCaptureField(editableContent, label, value)
    }

private fun contentWithoutCaptureFields(
    content: String,
    labels: Set<String>,
): String = content
    .lineSequence()
    .filterNot { line ->
        val trimmed = line.trim()
        labels.any { label ->
            trimmed.startsWith("$label：") || trimmed.startsWith("$label:")
        }
    }
    .joinToString("\n")
    .trim()

private fun ensureCaptureSections(
    content: String,
    sectionLabels: List<String>,
): String {
    val existing = content.lines().map { it.substringBefore("：").substringBefore(":").trim() }.toSet()
    val additions = sectionLabels
        .filterNot { it in existing }
        .map { "$it：" }
    return (listOf(content.trimEnd()) + additions)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

private fun formatRecordingDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%02d:%02d".format(minutes, rest)
}

private fun formatVoicePlaybackTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return formatRecordingDuration(totalSeconds)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParsedCaptureScreen(
    uiState: NoteEditorUiState,
    model: ParsedCaptureModel,
    sourceIcon: ImageVector,
    showImagePreview: Boolean,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    val context = LocalContext.current
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
                leaveNewCaptureWithoutSaving(context, uiState, onBack)
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
                title = model.title,
                onBack = ::requestBack,
                actionIcon = Icons.Outlined.MoreVert,
                actionContentDescription = "更多",
                onAction = {},
                actionAccent = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ParsedSourceCard(
                    model = model,
                    sourceIcon = sourceIcon,
                    showImagePreview = showImagePreview,
                    value = uiState.content,
                    onValueChange = onContentChange,
                )
                ArticleSectionCard(title = "AI 生成摘要") {
                    Text(
                        text = model.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ArticleSectionCard(title = "关键要点") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.keyPoints.forEach { point ->
                            BulletText(text = point)
                        }
                    }
                }
                ArticleSectionCard(title = "相关主题") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        model.topics.forEach { topic ->
                            AssistChip(text = topic, accent = AccentBlue)
                        }
                    }
                }
                ArticleSectionCard(title = "操作") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ArticleActionButton(
                            icon = Icons.Outlined.CalendarMonth,
                            text = "插入今天",
                            modifier = Modifier.weight(1f),
                        )
                        ArticleActionButton(
                            icon = Icons.Outlined.CheckCircle,
                            text = "链接任务",
                            modifier = Modifier.weight(1f),
                        )
                        ArticleActionButton(
                            icon = Icons.Outlined.Folder,
                            text = "导入项目",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GhostActionButton(
                        text = "存为草稿",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.MoreHoriz,
                    )
                    ActionButton(
                        text = if (uiState.isSaving) "保存中..." else model.saveLabel,
                        onClick = onSave,
                        enabled = !uiState.isSaving && uiState.content.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Check,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParsedSourceCard(
    model: ParsedCaptureModel,
    sourceIcon: ImageVector,
    showImagePreview: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
) {
    ArticleSectionCard(title = model.sourceTitle) {
        if (showImagePreview) {
            ParsedImagePreview(model = model, sourceIcon = sourceIcon)
        } else {
            VoiceInputPreview(model = model, sourceIcon = sourceIcon)
        }
        Text(
            text = model.sourceDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaperField(
            value = value,
            onValueChange = onValueChange,
            placeholder = model.sourcePlaceholder,
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun VoiceInputPreview(
    model: ParsedCaptureModel,
    sourceIcon: ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AccentBlue.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(AccentBlue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = sourceIcon,
                    contentDescription = null,
                    tint = AccentBlue,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(16.dp, 30.dp, 22.dp, 38.dp, 26.dp, 18.dp, 34.dp, 20.dp).forEach { height ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(height)
                            .background(AccentBlue.copy(alpha = 0.45f), RoundedCornerShape(999.dp)),
                    )
                }
            }
            Text(
                text = model.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = AccentBlue,
            )
        }
    }
}

@Composable
private fun ParsedImagePreview(
    model: ParsedCaptureModel,
    sourceIcon: ImageVector,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        color = AccentBlue.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = sourceIcon,
                        contentDescription = null,
                        tint = AccentBlue,
                    )
                }
                Text(
                    text = model.sourceLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBlue,
                )
            }
        }
    }
}

private enum class EditorContentKind {
    TEXT,
    VOICE,
    IMAGE,
    LINK,
}

private data class EditorContentReference(
    val kind: EditorContentKind,
    val title: String,
    val bodyLabel: String,
    val summaryLabel: String,
    val pointsLabel: String,
    val recordInfoLabel: String,
)

private fun editorContentReference(content: String): EditorContentReference {
    val labels = content.lines().map { line -> line.substringBefore("：").substringBefore(":").trim() }.toSet()
    return when {
        "链接" in labels || "原文链接" in labels || Regex("""https?://\S+""").containsMatchIn(content) -> EditorContentReference(
            kind = EditorContentKind.LINK,
            title = "链接记录",
            bodyLabel = "正文内容",
            summaryLabel = "AI 洞察",
            pointsLabel = "",
            recordInfoLabel = "记录信息",
        )
        "图片" in labels -> EditorContentReference(
            kind = EditorContentKind.IMAGE,
            title = "图片记录",
            bodyLabel = "图片理解摘要（可编辑）",
            summaryLabel = "AI 洞察",
            pointsLabel = "视觉识别结果",
            recordInfoLabel = "记录信息（可修改）",
        )
        "原始录音" in labels || "原始内容" in labels -> EditorContentReference(
            kind = EditorContentKind.VOICE,
            title = "语音记录",
            bodyLabel = "语音转写（可编辑）",
            summaryLabel = "AI 洞察",
            pointsLabel = "相关主题",
            recordInfoLabel = "记录信息",
        )
        else -> EditorContentReference(
            kind = EditorContentKind.TEXT,
            title = "文本记录",
            bodyLabel = "正文",
            summaryLabel = "AI 洞察",
            pointsLabel = "",
            recordInfoLabel = "记录信息",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextContentEditorMain(
    uiState: NoteEditorUiState,
    aiRunFeedback: String?,
    previewingOriginal: Boolean,
    contentBringIntoViewRequester: BringIntoViewRequester,
    onTopicChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onContentFocusChanged: (Boolean) -> Unit,
    onGenerateTitle: () -> Unit,
    onPolishContent: () -> Unit,
    onTogglePolishPreview: () -> Unit,
    onApplyPolishedContent: () -> Unit,
    onDiscardPolishedContent: () -> Unit,
) {
    val bodyMinLines = remember(uiState.content) { textContentEditorMinLines(uiState.content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorFieldHeader(label = "标题") {
            FieldIconAction(
                icon = Icons.Outlined.Refresh,
                contentDescription = if (uiState.isRefreshingTopic) "正在重新生成标题" else "重新生成标题",
                onClick = onGenerateTitle,
                enabled = !uiState.isRefreshingTopic && uiState.content.isNotBlank(),
            )
        }
        PaperField(
            value = uiState.topic,
            onValueChange = onTopicChange,
            placeholder = "产品体验分析与用户反馈思考",
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall,
        )

        EditorFieldHeader(label = "正文") {
            SubtleFieldAction(
                text = if (uiState.isPolishingContent) "润色中" else "润色",
                icon = Icons.Outlined.AutoFixHigh,
                contentDescription = "润色正文",
                onClick = onPolishContent,
                enabled = !uiState.isPolishingContent && uiState.content.isNotBlank(),
            )
        }
        PaperField(
            value = uiState.content,
            onValueChange = onContentChange,
            placeholder = "在这里写正文。",
            modifier = Modifier.bringIntoViewRequester(contentBringIntoViewRequester),
            minLines = bodyMinLines,
            textStyle = MaterialTheme.typography.bodyMedium,
            onFocusChanged = onContentFocusChanged,
        )

        aiRunFeedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.labelSmall,
                color = AccentBlue,
            )
        }

        if (!uiState.polishedOriginalContent.isNullOrBlank() && !uiState.polishedCandidateContent.isNullOrBlank()) {
            PolishPreviewCard(
                showingOriginal = previewingOriginal,
                original = uiState.polishedOriginalContent,
                polished = uiState.polishedCandidateContent,
                onTogglePreview = onTogglePolishPreview,
                onApply = onApplyPolishedContent,
                onDiscard = onDiscardPolishedContent,
            )
        }
    }
}

@Composable
private fun EditorFieldHeader(
    label: String,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}

@Composable
private fun FieldIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SubtleFieldAction(
    text: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.heightIn(min = 44.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) AccentBlue.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) AccentBlue.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReferenceInlineAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.24f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = AccentBlue,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaContentEditorMain(
    uiState: NoteEditorUiState,
    reference: EditorContentReference,
    aiToolsExpanded: Boolean,
    aiModeSummary: String,
    aiRunFeedback: String?,
    previewingOriginal: Boolean,
    contentBringIntoViewRequester: BringIntoViewRequester,
    onContentChange: (String) -> Unit,
    onToggleAiTools: () -> Unit,
    onContentFocusChanged: (Boolean) -> Unit,
    onPolishContent: () -> Unit,
    onRetriggerTopic: () -> Unit,
    onRetriggerTag: () -> Unit,
    onRetriggerFolder: () -> Unit,
    onTogglePolishPreview: () -> Unit,
    onApplyPolishedContent: () -> Unit,
    onDiscardPolishedContent: () -> Unit,
) {
    val hiddenImagePath = extractCaptureField(uiState.content, "图片")
    val contentFieldValue = when (reference.kind) {
        EditorContentKind.IMAGE -> contentWithoutCaptureFields(
            content = uiState.content,
            labels = setOf("图片"),
        )
        EditorContentKind.LINK -> articleBodyFromContent(uiState.content)
        EditorContentKind.TEXT,
        EditorContentKind.VOICE -> uiState.content
    }
    val onContentFieldChange: (String) -> Unit = when (reference.kind) {
        EditorContentKind.IMAGE -> { edited ->
            onContentChange(mergeCaptureField(edited, "图片", hiddenImagePath))
        }
        EditorContentKind.LINK -> { edited ->
            onContentChange(replaceCaptureField(uiState.content, ArticleBodyFieldLabel, edited))
        }
        EditorContentKind.TEXT,
        EditorContentKind.VOICE -> onContentChange
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = reference.bodyLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaperField(
            value = contentFieldValue,
            onValueChange = onContentFieldChange,
            placeholder = when (reference.kind) {
                EditorContentKind.VOICE -> "语音转写、整理后的关键句，或你手动补充的原始内容。"
                EditorContentKind.IMAGE -> "图片理解摘要、OCR 文本，或你对这张图的补充说明。"
                EditorContentKind.LINK -> "网页正文、摘录，或你手动粘贴的原文。"
                EditorContentKind.TEXT -> "在这里写正文。"
            },
            modifier = Modifier.bringIntoViewRequester(contentBringIntoViewRequester),
            minLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium,
            onFocusChanged = onContentFocusChanged,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ReferenceInlineAction(
                text = if (aiToolsExpanded) "收起 AI 整理" else "AI 整理",
                icon = Icons.Outlined.AutoFixHigh,
                onClick = onToggleAiTools,
            )
        }

        if (aiToolsExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                onTogglePreview = onTogglePolishPreview,
                onApply = onApplyPolishedContent,
                onDiscard = onDiscardPolishedContent,
            )
        }
    }
}

@Composable
private fun ContentReferenceDetailSections(
    uiState: NoteEditorUiState,
    reference: EditorContentReference,
    onContentChange: (String) -> Unit,
    onCaptureTypeSelect: (String) -> Unit,
) {
    when (reference.kind) {
        EditorContentKind.TEXT -> TextContentReferenceSections(
            uiState = uiState,
            onCaptureTypeSelect = onCaptureTypeSelect,
        )
        EditorContentKind.VOICE -> VoiceContentReferenceSections(
            uiState = uiState,
        )
        EditorContentKind.IMAGE -> ImageContentReferenceSections(
            uiState = uiState,
            reference = reference,
            onContentChange = onContentChange,
        )
        EditorContentKind.LINK -> LinkContentReferenceSections(uiState = uiState)
    }
}

@Composable
private fun MediaContentPrimarySection(
    reference: EditorContentReference,
    content: String,
) {
    when (reference.kind) {
        EditorContentKind.VOICE -> VoiceAudioReferenceSection(
            audioPath = extractCaptureField(content, VoiceAudioFieldLabel),
        )
        EditorContentKind.IMAGE -> ImagePreviewReferenceSection(
            imagePath = extractCaptureField(content, "图片"),
        )
        EditorContentKind.LINK -> Unit
        EditorContentKind.TEXT -> Unit
    }
}

@Composable
private fun TextContentReferenceSections(
    uiState: NoteEditorUiState,
    onCaptureTypeSelect: (String) -> Unit,
) {
    TextRecordTypeSection(
        uiState = uiState,
        onCaptureTypeSelect = onCaptureTypeSelect,
    )
    RelatedTopicSection(textContentRelatedTopics(uiState.tags))
    if (!uiState.isNew) {
        TextAiInsightSection(uiState = uiState, title = "AI 洞察")
    }
    ArticleSectionCard(title = "附件") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AttachmentButton(
                icon = Icons.Outlined.Image,
                text = "添加图片",
                modifier = Modifier.weight(1f),
            )
            AttachmentButton(
                icon = Icons.Outlined.Link,
                text = "添加链接",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextRecordTypeSection(
    uiState: NoteEditorUiState,
    onCaptureTypeSelect: (String) -> Unit,
) {
    val selected = remember(uiState.tags, uiState.status, uiState.content, uiState.topic) {
        inferCaptureType(uiState)
    }
    ArticleSectionCard(title = "记录类型") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CaptureType.entries.forEach { type ->
                CaptureTypeChip(
                    type = type,
                    selected = type == selected,
                    onClick = { onCaptureTypeSelect(type.label) },
                )
            }
        }
    }
}

private fun textContentRelatedTopics(tags: List<String>): List<String> =
    tags.filterNot { it in TextCaptureTypeLabels }
        .ifEmpty { listOf("文本") }

@Composable
private fun TextAiInsightSection(
    uiState: NoteEditorUiState,
    title: String,
) {
    ArticleSectionCard(title = title) {
        val hasInsight = uiState.aiSummary.isNotBlank() || uiState.aiKeyPoints.isNotEmpty()
        if (hasInsight) {
            uiState.aiSummary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.aiKeyPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.aiKeyPoints.forEach { point -> BulletText(text = point) }
                }
            }
        } else {
            AiInsightPendingRow(text = "正在整理这条记录")
        }
    }
}

@Composable
private fun AiInsightPendingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(AccentBlue.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoFixHigh,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceContentEditorMain(
    uiState: NoteEditorUiState,
    contentBringIntoViewRequester: BringIntoViewRequester,
    onTopicChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onContentFocusChanged: (Boolean) -> Unit,
) {
    val transcript = remember(uiState.content) { voiceTranscriptFromContent(uiState.content) }
    val recognitionStatus = remember(uiState.content) { voiceRecognitionStatusFromContent(uiState.content) }
    val titleValue = remember(uiState.topic, uiState.topicSource, transcript) {
        voiceTitleForDisplay(
            topic = uiState.topic,
            topicSource = uiState.topicSource,
            transcript = transcript,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        EditorFieldHeader(label = "标题") {}
        PaperField(
            value = titleValue,
            onValueChange = { edited ->
                if (edited != titleValue) {
                    onTopicChange(edited)
                }
            },
            placeholder = if (uiState.isTranscribingVoice) {
                "正在根据语音生成标题"
            } else {
                "转写完成后自动生成标题"
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall,
        )
        ArticleSectionCard(
            title = "语音转写（可编辑）",
            titleTrailing = {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            },
        ) {
            PaperField(
                value = transcript,
                onValueChange = { edited ->
                    if (edited == transcript) return@PaperField
                    val nextContent = replaceCaptureField(
                        content = uiState.content,
                        label = VoiceTranscriptFieldLabel,
                        value = edited,
                    )
                    if (nextContent != uiState.content) {
                        onContentChange(nextContent)
                    }
                },
                placeholder = if (uiState.isTranscribingVoice) {
                    "正在转写…"
                } else if (extractCaptureField(uiState.content, VoiceAudioFieldLabel).isBlank()) {
                    "录音后，转写内容会显示在这里。"
                } else {
                    "转写暂未完成，可先手动补充内容。"
                },
                modifier = Modifier.bringIntoViewRequester(contentBringIntoViewRequester),
                minLines = voiceTranscriptEditorMinLines(transcript),
                textStyle = MaterialTheme.typography.bodyMedium,
                onFocusChanged = onContentFocusChanged,
            )
            voiceTranscriptHelperText(
                recognitionStatus = recognitionStatus,
                isTranscribing = uiState.isTranscribingVoice,
                transcript = transcript,
            )?.let { helperText ->
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (helperText.startsWith("转写失败")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceAudioReferenceSection(audioPath: String) {
    val normalizedPath = audioPath.trim()
    val audioFile = remember(normalizedPath) {
        normalizedPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf(File::exists)
    }
    var player by remember(normalizedPath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(normalizedPath) { mutableStateOf(false) }
    var positionMs by remember(normalizedPath) { mutableStateOf(0) }
    var durationMs by remember(normalizedPath) { mutableStateOf(0) }
    var playbackError by remember(normalizedPath) { mutableStateOf<String?>(null) }

    DisposableEffect(normalizedPath) {
        positionMs = 0
        durationMs = 0
        isPlaying = false
        playbackError = null
        val preparedPlayer = audioFile?.let { file ->
            runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    durationMs = duration.coerceAtLeast(0)
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = durationMs
                    }
                }
            }.onFailure {
                playbackError = "录音文件暂时无法打开"
            }.getOrNull()
        }
        player = preparedPlayer
        onDispose {
            runCatching {
                if (preparedPlayer?.isPlaying == true) {
                    preparedPlayer.stop()
                }
            }
            preparedPlayer?.release()
        }
    }

    LaunchedEffect(isPlaying, player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (isPlaying && isActive) {
            runCatching {
                positionMs = activePlayer.currentPosition.coerceAtLeast(0)
            }.onFailure {
                isPlaying = false
                playbackError = "播放状态暂时无法读取"
            }
            delay(250)
        }
    }

    val canPlay = player != null
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ArticleSectionCard(
        title = "语音暂存音频（可回放）",
        titleTrailing = {
            Text(
                text = formatVoicePlaybackTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        VoicePlaybackWaveform(active = isPlaying, tick = positionMs / 250)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = if (canPlay) AccentBlue else AccentBlue.copy(alpha = 0.18f),
                shape = CircleShape,
                enabled = canPlay,
                onClick = {
                    val activePlayer = player
                    if (activePlayer != null) {
                        runCatching {
                            if (activePlayer.isPlaying) {
                                activePlayer.pause()
                                isPlaying = false
                            } else {
                                activePlayer.start()
                                isPlaying = true
                            }
                        }.onFailure {
                            isPlaying = false
                            playbackError = "录音文件暂时无法播放"
                        }
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "暂停音频" else "播放音频",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = if (canPlay) {
                    if (isPlaying) "暂停音频" else "播放音频"
                } else {
                    "暂无音频"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (canPlay) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(AccentBlue, RoundedCornerShape(999.dp)),
                )
            }
            Text(
                text = "${formatVoicePlaybackTime(positionMs)} / ${formatVoicePlaybackTime(durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Surface(
                color = AccentBlue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = "1x",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                )
            }
        }
        playbackError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkContentEditorMain(
    uiState: NoteEditorUiState,
    contentBringIntoViewRequester: BringIntoViewRequester,
    onContentChange: (String) -> Unit,
    onContentFocusChanged: (Boolean) -> Unit,
    onEnsureArticleExtraction: () -> Unit,
) {
    val urlValue = remember(uiState.content) { articleUrlFromContent(uiState.content) }
    val body = remember(uiState.content) { articleBodyFromContent(uiState.content) }
    val status = remember(uiState.content) { articleStatusFromContent(uiState.content) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        EditorFieldHeader(label = "链接") {
            SubtleFieldAction(
                text = if (uiState.isExtractingArticle) "提取中" else "解析",
                icon = Icons.Outlined.AutoFixHigh,
                contentDescription = "解析网页正文",
                onClick = onEnsureArticleExtraction,
                enabled = !uiState.isExtractingArticle && urlValue.isNotBlank(),
            )
        }
        if (!uiState.isNew && normalizeExternalLinkUrl(urlValue) != null) {
            OpenableLinkText(url = urlValue)
        } else {
            PaperField(
                value = urlValue,
                onValueChange = { value ->
                    onContentChange(replaceCaptureField(uiState.content, ArticleUrlFieldLabel, value))
                },
                placeholder = "粘贴网页链接",
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        status.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (message.startsWith("提取失败")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        EditorFieldHeader(label = "正文内容") {}
        PaperField(
            value = body,
            onValueChange = { edited ->
                onContentChange(replaceCaptureField(uiState.content, ArticleBodyFieldLabel, edited))
            },
            placeholder = if (uiState.isExtractingArticle) {
                "正在提取网页正文…"
            } else {
                "解析后正文会显示在这里，也可以手动粘贴正文。"
            },
            modifier = Modifier.bringIntoViewRequester(contentBringIntoViewRequester),
            minLines = 7,
            textStyle = MaterialTheme.typography.bodyMedium,
            onFocusChanged = onContentFocusChanged,
        )
    }
}

@Composable
private fun VoicePlaybackWaveform(
    active: Boolean,
    tick: Int,
) {
    val baseHeights = listOf(7, 12, 20, 28, 16, 9, 22, 14)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(40) { index ->
            val base = baseHeights[index % baseHeights.size]
            val pulse = if (active) ((index * 17 + tick * 13) % 9) - 4 else 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((base + pulse).coerceIn(5, 32).dp)
                    .background(AccentBlue.copy(alpha = 0.82f), RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun VoiceContentReferenceSections(
    uiState: NoteEditorUiState,
) {
    if (!uiState.isNew) {
        VoiceAiInsightSection(uiState = uiState)
    }
}

@Composable
private fun LinkContentReferenceSections(uiState: NoteEditorUiState) {
    if (!uiState.isNew) {
        TextAiInsightSection(uiState = uiState, title = "AI 洞察")
    }
}

internal fun normalizeExternalLinkUrl(rawUrl: String): String? {
    val candidate = rawUrl
        .trim()
        .trimEnd('，', '。', ',', '.', ')', '）', ']', '】', '>')
    val schemeDivider = candidate.indexOf("://")
    if (schemeDivider <= 0) return null
    val scheme = candidate.substring(0, schemeDivider).lowercase()
    return candidate.takeIf { scheme == "http" || scheme == "https" }
}

@Composable
private fun OpenableLinkText(url: String) {
    val context = LocalContext.current
    val normalizedUrl = remember(url) { normalizeExternalLinkUrl(url) }
    if (normalizedUrl == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { openExternalLink(context, normalizedUrl) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = normalizedUrl,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                color = AccentBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openExternalLink(
    context: Context,
    url: String,
) {
    val normalizedUrl = normalizeExternalLinkUrl(url)
    if (normalizedUrl == null) {
        Toast.makeText(context, "链接格式暂时无法打开", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = Uri.parse(normalizedUrl)
    val launched = startExternalActivity(context, externalLinkIntent(uri, context))
    if (!launched) {
        Toast.makeText(context, "没有找到可以打开这个链接的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun externalLinkIntent(uri: Uri, context: Context): Intent =
    Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .withLaunchFlags(context)

private fun Intent.withLaunchFlags(context: Context): Intent = apply {
    if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun startExternalActivity(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

@Composable
private fun VoiceAiInsightSection(uiState: NoteEditorUiState) {
    val transcript = remember(uiState.content) { voiceTranscriptFromContent(uiState.content) }
    val recognitionStatus = remember(uiState.content) { voiceRecognitionStatusFromContent(uiState.content) }
    ArticleSectionCard(title = "AI 洞察") {
        val hasInsight = uiState.aiSummary.isNotBlank() || uiState.aiKeyPoints.isNotEmpty()
        if (hasInsight) {
            uiState.aiSummary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.aiKeyPoints.isNotEmpty()) {
                Text(
                    text = "关键信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.aiKeyPoints.forEach { point -> BulletText(text = point) }
                }
            }
        } else {
            AiInsightPendingRow(
                text = voiceAiInsightPendingText(
                    transcript = transcript,
                    recognitionStatus = recognitionStatus,
                    isTranscribing = uiState.isTranscribingVoice,
                ),
            )
        }
    }
}

internal fun voiceTitleForDisplay(
    topic: String,
    topicSource: TopicSource,
    transcript: String,
): String {
    if (topic.isVoiceCaptureMetadataTitle()) return "语音记录"
    return if (transcript.isBlank() && topicSource != TopicSource.MANUAL) {
        ""
    } else {
        topic
    }
}

internal fun String.isVoiceCaptureMetadataTitle(): Boolean {
    val normalized = trim()
    val lower = normalized.lowercase()
    return normalized.startsWith("原始录音：") ||
        normalized.startsWith("原始录音:") ||
        "/data/" in lower ||
        "/storage/" in lower ||
        "content://" in lower ||
        "file://" in lower ||
        ".m4a" in lower ||
        ".wav" in lower ||
        ".aac" in lower ||
        "com.mindflow.app/files" in lower
}

internal fun voiceAiInsightPendingText(
    transcript: String,
    recognitionStatus: String,
    isTranscribing: Boolean,
): String = when {
    transcript.isNotBlank() -> "正在整理转写内容"
    isTranscribing -> "转写完成后会自动整理关键信息"
    recognitionStatus.startsWith("转写失败") -> "当前没有转写内容，暂不生成 AI 洞察"
    else -> "转写完成后会自动整理关键信息"
}

@Composable
private fun ImagePreviewReferenceSection(
    imagePath: String,
) {
    val imageBitmap by rememberImageBitmap(imagePath)
    ArticleSectionCard(title = "图片预览") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            color = AccentBlue.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = "原图本地保留，可继续补充识别内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (imagePath.isNotBlank()) {
            Text(
                text = File(imagePath).name,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageContentReferenceSections(
    uiState: NoteEditorUiState,
    reference: EditorContentReference,
    onContentChange: (String) -> Unit,
) {
    val imagePath = extractCaptureField(uiState.content, "图片")
    val imageSummary = imageSummaryFromContent(uiState.content)
    val imageKeyInfo = imageKeyInfoFromContent(uiState.content)
    val imageOcr = imageOcrFromContent(uiState.content)
    if (!uiState.isNew) {
        TextAiInsightSection(uiState = uiState, title = reference.summaryLabel)
    }
    ArticleSectionCard(title = reference.pointsLabel) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (imagePath.isNotBlank()) {
                BulletText(text = "原图文件：${File(imagePath).name}")
            }
            if (imageSummary.isNotBlank()) {
                BulletText(text = imageSummary)
            }
            imageKeyInfo.forEach { point -> BulletText(text = point) }
            RecognitionInfoGrid(
                items = listOf(
                    "类型" to extractCaptureField(uiState.content, ImageObjectsFieldLabel).ifBlank { "待识别" },
                    "主体" to imageKeyInfo.firstOrNull().orEmpty().ifBlank { "待提取" },
                    "文字" to if (imageOcr.isBlank()) "待 OCR" else "已提取",
                    "状态" to extractCaptureField(uiState.content, ImageRecognitionFieldLabel).ifBlank { "待解析" },
                ),
            )
        }
    }
    ArticleSectionCard(title = "OCR 全文（可选）") {
        Text(
            text = imageOcr.ifBlank { "暂无 OCR 文本。" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssistChip(
    text: String,
    accent: Color,
) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AttachmentButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleSourceCard(model: ArticleCaptureModel) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(AccentBlue.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    tint = AccentBlue,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.url.isNotBlank()) {
                    Text(
                    text = model.url,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { openExternalLink(context, model.url) },
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                }
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArticleSectionCard(
    title: String,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            titleTrailing?.invoke()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(AccentBlue, CircleShape),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArticleActionButton(
    icon: ImageVector,
    text: String,
    accent: Color = AccentBlue,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleSourceInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    ArticleSectionCard(title = "来源内容") {
        PaperField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "粘贴文章链接，或补充你收藏它的原因。",
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
    actionAccent: Color = AccentBlue,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorBarIconButton(
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
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionIcon != null && actionContentDescription != null && onAction != null) {
            EditorBarIconButton(
                icon = actionIcon,
                contentDescription = actionContentDescription,
                onClick = onAction,
                accent = actionAccent,
            )
        }
    }
}

@Composable
private fun EditorBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Color = AccentBlue,
) {
    Surface(
        modifier = Modifier.size(38.dp),
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(25.dp),
            )
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
    onStatusChange: (NoteStatus) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onPolishContent: () -> Unit,
    onGenerateTitle: () -> Unit,
    onCaptureTypeSelect: (String) -> Unit,
    onApplyPolishedContent: () -> Unit,
    onDiscardPolishedContent: () -> Unit,
    onEnsureVoiceTranscription: () -> Unit,
    onEnsureArticleExtraction: () -> Unit,
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
    var previewingOriginal by rememberSaveable(
        uiState.polishedOriginalContent,
        uiState.polishedCandidateContent,
    ) { mutableStateOf(false) }
    var aiToolsExpanded by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(false) }
    var metadataExpanded by rememberSaveable(uiState.noteId, uiState.isNew) { mutableStateOf(false) }
    var knowledgeRecallRequestVersion by rememberSaveable(uiState.noteId) { mutableStateOf(0) }
    var aiRunFeedback by rememberSaveable(uiState.noteId) { mutableStateOf<String?>(null) }
    var titlePolishWasRunning by rememberSaveable(uiState.noteId) { mutableStateOf(false) }
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
    val contentReference = remember(uiState.content) { editorContentReference(uiState.content) }
    LaunchedEffect(contentReference.kind, uiState.content, uiState.isTranscribingVoice) {
        if (
            contentReference.kind == EditorContentKind.VOICE &&
            shouldAutoAttemptVoiceTranscription(
                isNew = uiState.isNew,
                content = uiState.content,
                isTranscribingVoice = uiState.isTranscribingVoice,
            )
        ) {
            onEnsureVoiceTranscription()
        }
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
            extraInfoExpanded = metadataExpanded,
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

    LaunchedEffect(uiState.isPolishingTitle) {
        if (uiState.isPolishingTitle) {
            titlePolishWasRunning = true
        } else if (titlePolishWasRunning) {
            titlePolishWasRunning = false
            aiRunFeedback = readLatestEditorAiRunFeedback(context, AiTaskType.POLISH_TITLE)
                ?: "本次润色标题按当前策略运行，但没有记录到最终 provider。"
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
                title = contentReference.title,
                onBack = ::requestBack,
                actionIcon = Icons.Outlined.Check,
                actionContentDescription = "保存记录",
                onAction = onSave,
                actionAccent = AccentBlue,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (contentReference.kind) {
                    EditorContentKind.TEXT -> {
                        TextContentEditorMain(
                            uiState = uiState,
                            aiRunFeedback = aiRunFeedback,
                            previewingOriginal = previewingOriginal,
                            contentBringIntoViewRequester = contentBringIntoViewRequester,
                            onTopicChange = onTopicChange,
                            onContentChange = onContentChange,
                            onContentFocusChanged = { isContentFieldFocused = it },
                            onGenerateTitle = onGenerateTitle,
                            onPolishContent = onPolishContent,
                            onTogglePolishPreview = { previewingOriginal = !previewingOriginal },
                            onApplyPolishedContent = {
                                previewingOriginal = false
                                onApplyPolishedContent()
                            },
                            onDiscardPolishedContent = {
                                previewingOriginal = false
                                onDiscardPolishedContent()
                            },
                        )
                    }
                    EditorContentKind.VOICE -> {
                        MediaContentPrimarySection(
                            reference = contentReference,
                            content = uiState.content,
                        )
                        VoiceContentEditorMain(
                            uiState = uiState,
                            contentBringIntoViewRequester = contentBringIntoViewRequester,
                            onTopicChange = onTopicChange,
                            onContentChange = onContentChange,
                            onContentFocusChanged = { isContentFieldFocused = it },
                        )
                    }
                    EditorContentKind.IMAGE -> {
                        MediaContentPrimarySection(
                            reference = contentReference,
                            content = uiState.content,
                        )
                        MediaContentEditorMain(
                            uiState = uiState,
                            reference = contentReference,
                            aiToolsExpanded = aiToolsExpanded,
                            aiModeSummary = aiModeSummary,
                            aiRunFeedback = aiRunFeedback,
                            previewingOriginal = previewingOriginal,
                            contentBringIntoViewRequester = contentBringIntoViewRequester,
                            onContentChange = onContentChange,
                            onToggleAiTools = { aiToolsExpanded = !aiToolsExpanded },
                            onContentFocusChanged = { isContentFieldFocused = it },
                            onPolishContent = onPolishContent,
                            onRetriggerTopic = onRetriggerTopic,
                            onRetriggerTag = onRetriggerTag,
                            onRetriggerFolder = onRetriggerFolder,
                            onTogglePolishPreview = { previewingOriginal = !previewingOriginal },
                            onApplyPolishedContent = {
                                previewingOriginal = false
                                onApplyPolishedContent()
                            },
                            onDiscardPolishedContent = {
                                previewingOriginal = false
                                onDiscardPolishedContent()
                            },
                        )
                    }
                    EditorContentKind.LINK -> {
                        LinkContentEditorMain(
                            uiState = uiState,
                            contentBringIntoViewRequester = contentBringIntoViewRequester,
                            onContentChange = onContentChange,
                            onContentFocusChanged = { isContentFieldFocused = it },
                            onEnsureArticleExtraction = onEnsureArticleExtraction,
                        )
                    }
                }

                ContentReferenceDetailSections(
                    uiState = uiState,
                    reference = contentReference,
                    onContentChange = onContentChange,
                    onCaptureTypeSelect = onCaptureTypeSelect,
                )

                ContentReferenceActionRow(
                    onInsertToday = { onAddTag("今天") },
                    onLinkTask = {
                        onStatusChange(NoteStatus.IN_PROGRESS)
                        onAddTag("任务")
                    },
                    onImportProject = {
                        onFolderChange("project")
                        onAddTag("项目")
                    },
                )

                CompactRecordInfoSection(
                    uiState = uiState,
                    reference = contentReference,
                    expanded = metadataExpanded,
                    onToggleExpanded = { metadataExpanded = !metadataExpanded },
                    onFolderChange = onFolderChange,
                    onStatusChange = onStatusChange,
                    onArchiveChange = onArchiveChange,
                )

                if (!uiState.isNew) {
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
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactRecordInfoSection(
    uiState: NoteEditorUiState,
    reference: EditorContentReference,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFolderChange: (String?) -> Unit,
    onStatusChange: (NoteStatus) -> Unit,
    onArchiveChange: (Boolean) -> Unit,
) {
    PanelCard {
        SectionHeader(
            title = reference.recordInfoLabel,
            headline = compactRecordInfoHeadline(uiState),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "记录概览",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (expanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentBlue,
                    )
                }
                CompactRecordInfoLine("状态", "${uiState.status.label} · ${uiState.horizon.label}")
                CompactRecordInfoLine(
                    label = "分类",
                    value = compactRecordInfoFolderSummary(uiState),
                )
                if (!uiState.isNew) {
                    CompactRecordInfoLine(
                        label = "时间",
                        value = uiState.updatedAt?.let { "更新 ${TimeFormatter.compact(it)}" }.orEmpty(),
                    )
                    CompactRecordInfoLine(
                        label = "归档",
                        value = if (uiState.isArchived) "已从首页隐藏" else "仍显示在首页",
                    )
                }
            }
        }

        if (expanded) {
            Text(
                text = "快速调整",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

            Text(
                text = "分类",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

            if (!uiState.isNew) {
                CompactRecordInfoLine("标签", compactRecordInfoTags(uiState.tags))
                CompactRecordInfoLine("时间", compactRecordInfoTime(uiState))
                RecordVisibilityRow(
                    isArchived = uiState.isArchived,
                    onArchiveChange = onArchiveChange,
                )
            } else {
                Text(
                    text = "保存后会自动补主题和标签。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordVisibilityRow(
    isArchived: Boolean,
    onArchiveChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "首页显示",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isArchived) "当前已隐藏" else "当前可见",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = !isArchived,
            onCheckedChange = { visible -> onArchiveChange(!visible) },
        )
    }
}

private fun compactRecordInfoTags(tags: List<String>): String =
    tags.takeIf { it.isNotEmpty() }
        ?.joinToString("、")
        ?: "暂无标签"

private fun compactRecordInfoTime(uiState: NoteEditorUiState): String {
    val updated = uiState.updatedAt?.let(TimeFormatter::compact)
    val created = uiState.createdAt?.let(TimeFormatter::compact)
    return when {
        updated != null && created != null -> "更新 $updated · 创建 $created"
        updated != null -> "更新 $updated"
        created != null -> "创建 $created"
        else -> "未记录"
    }
}

@Composable
private fun CompactRecordInfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(46.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "未记录" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun compactRecordInfoHeadline(uiState: NoteEditorUiState): String {
    val time = uiState.updatedAt?.let(TimeFormatter::compact)
    return listOfNotNull(
        "${uiState.status.label} · ${uiState.horizon.label}",
        time,
    ).joinToString(" · ")
}

private fun compactRecordInfoFolderSummary(uiState: NoteEditorUiState): String =
    buildSupplementSummary(
        folderKey = uiState.folderKey,
        tagCount = uiState.tags.size,
        isNew = uiState.isNew,
    ).removePrefix("文件夹：")

@Composable
private fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onFocusChanged: (Boolean) -> Unit = {},
    expandToContainer: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
            maxLines = maxLines,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
