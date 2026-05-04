package com.mindflow.app.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.topic.NoteInsightPlanner
import com.mindflow.app.data.topic.NoteInsightResult
import com.mindflow.app.data.topic.shouldAutoGenerateVoiceInsight
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.data.topic.ContentPolishPlanner
import com.mindflow.app.data.topic.ContentPolishResult
import com.mindflow.app.data.topic.VoiceTranscriptionPlanner
import com.mindflow.app.data.topic.VoiceTranscriptionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val isNew: Boolean = true,
    val noteId: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRefreshingFolder: Boolean = false,
    val isRefreshingTopic: Boolean = false,
    val isRefreshingTags: Boolean = false,
    val isPolishingTitle: Boolean = false,
    val isPolishingContent: Boolean = false,
    val isTranscribingVoice: Boolean = false,
    val isExtractingVoiceInfo: Boolean = false,
    val content: String = "",
    val topic: String = "",
    val topicSource: TopicSource = TopicSource.RULE,
    val folderKey: String? = null,
    val folderSource: FolderSource = FolderSource.RULE,
    val tags: List<String> = emptyList(),
    val tagSource: TagSource = TagSource.RULE,
    val status: NoteStatus = NoteStatus.IDEA,
    val horizon: NoteHorizon = NoteHorizon.MEDIUM,
    val knowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
    val isArchived: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val latestDoneAt: Long? = null,
    val statusHistory: List<NoteStatusHistoryEntity> = emptyList(),
    val relatedNotes: List<NoteEntity> = emptyList(),
    val aiSummary: String = "",
    val aiKeyPoints: List<String> = emptyList(),
    val polishedOriginalContent: String? = null,
    val polishedCandidateContent: String? = null,
    val folderEdited: Boolean = false,
    val topicEdited: Boolean = false,
    val tagsEdited: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

internal val TextCaptureTypeLabels: Set<String> = setOf("想法", "任务", "文档", "灵感")

internal fun applyTextCaptureTypeSelection(
    state: NoteEditorUiState,
    rawTypeLabel: String,
): NoteEditorUiState {
    val typeLabel = NoteTagCodec.normalizeOne(rawTypeLabel) ?: return state
    if (typeLabel !in TextCaptureTypeLabels) return state
    val preservedTags = state.tags.filterNot { it in TextCaptureTypeLabels }
    val nextTags = NoteTagCodec.normalize(listOf(typeLabel) + preservedTags)
    return state.copy(
        status = if (typeLabel == "任务") NoteStatus.IN_PROGRESS else NoteStatus.IDEA,
        tags = nextTags,
        tagsEdited = true,
    )
}

sealed interface NoteEditorEvent {
    data object NavigateBack : NoteEditorEvent
    data class Message(val text: String) : NoteEditorEvent
}

internal enum class CapturePostAction {
    ADD_TO_TODAY,
    CONVERT_TO_TASK,
    ADD_TO_PROJECT,
}

internal fun applyCaptureActionState(
    state: NoteEditorUiState,
    action: CapturePostAction,
): NoteEditorUiState = when (action) {
    CapturePostAction.ADD_TO_TODAY -> state.copy(
        tags = appendCaptureTag(state.tags, "今天"),
        tagsEdited = true,
        hasUnsavedChanges = true,
    )
    CapturePostAction.CONVERT_TO_TASK -> state.copy(
        status = NoteStatus.IN_PROGRESS,
        tags = appendCaptureTag(state.tags, "任务"),
        tagsEdited = true,
        hasUnsavedChanges = true,
    )
    CapturePostAction.ADD_TO_PROJECT -> state.copy(
        folderKey = "project",
        folderSource = FolderSource.MANUAL,
        folderEdited = true,
        tags = appendCaptureTag(state.tags, "项目"),
        tagsEdited = true,
        hasUnsavedChanges = true,
    )
}

internal fun shouldAutoGenerateTextInsight(content: String): Boolean {
    if (content.isBlank()) return false
    val labels = content.lines().map { line -> line.substringBefore("：").substringBefore(":").trim() }.toSet()
    return "图片" !in labels && "原始录音" !in labels && "原始内容" !in labels
}

internal const val VoiceAudioFieldLabel = "原始录音"
internal const val VoiceTranscriptFieldLabel = "语音转写（可编辑）"
internal const val VoiceAiSummaryFieldLabel = "AI 快速提取"
internal const val VoiceKeyInfoFieldLabel = "关键信息"
internal const val VoiceRecognitionFieldLabel = "识别信息"

internal fun voiceTranscriptFromContent(content: String): String =
    extractEditorCaptureField(content, VoiceTranscriptFieldLabel)
        .ifBlank { extractEditorCaptureField(content, "原始内容") }
        .ifBlank {
            contentWithoutEditorCaptureFields(
                content = content,
                labels = setOf(
                    VoiceAudioFieldLabel,
                    VoiceTranscriptFieldLabel,
                    VoiceAiSummaryFieldLabel,
                    VoiceKeyInfoFieldLabel,
                    VoiceRecognitionFieldLabel,
                ),
            )
        }
        .trim()

internal fun voiceAudioPathFromContent(content: String): String =
    extractEditorCaptureField(content, VoiceAudioFieldLabel)

internal fun voiceRecognitionStatusFromContent(content: String): String =
    extractEditorCaptureField(content, VoiceRecognitionFieldLabel)

internal fun shouldAttemptVoiceTranscription(
    content: String,
    isTranscribingVoice: Boolean,
    audioPathOverride: String = "",
): Boolean {
    val audioPath = audioPathOverride.ifBlank { voiceAudioPathFromContent(content) }
    val recognitionStatus = voiceRecognitionStatusFromContent(content)
    return audioPath.isNotBlank() &&
        voiceTranscriptFromContent(content).isBlank() &&
        !isTranscribingVoice &&
        !recognitionStatus.contains("转写失败") &&
        !recognitionStatus.contains("无法转写")
}

internal fun voiceKeyPointsFromContent(content: String): List<String> =
    extractEditorCaptureField(content, VoiceKeyInfoFieldLabel)
        .split("；", ";", "、")
        .map { it.trim().trim('-', '•', '*', ' ') }
        .filter { it.isNotBlank() }

internal fun replaceEditorCaptureField(
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

private fun voiceContentWithAiInfo(
    content: String,
    transcript: String,
    summary: String,
    keyPoints: List<String>,
): String {
    var next = replaceEditorCaptureField(content, VoiceTranscriptFieldLabel, transcript)
    next = replaceEditorCaptureField(next, VoiceAiSummaryFieldLabel, summary)
    next = replaceEditorCaptureField(next, VoiceKeyInfoFieldLabel, keyPoints.joinToString("；"))
    next = replaceEditorCaptureField(next, VoiceRecognitionFieldLabel, "AI 已根据转写内容提取，保存后可继续编辑")
    return next
}

private fun ensureEditorCaptureSections(
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

private fun extractEditorCaptureField(
    content: String,
    label: String,
): String = content
    .lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("$label：") || it.startsWith("$label:") }
    ?.substringAfter("：")
    ?.substringAfter(":")
    ?.trim()
    .orEmpty()

private fun contentWithoutEditorCaptureFields(
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

private fun appendCaptureTag(
    current: List<String>,
    tag: String,
): List<String> {
    val normalized = NoteTagCodec.normalize(current + tag)
    return if (normalized.size <= 3) {
        normalized
    } else if (tag in normalized.take(3)) {
        normalized.take(3)
    } else {
        (normalized.take(2) + tag).distinct()
    }
}

class NoteEditorViewModel(
    private val noteRepository: NoteRepository,
    private val contentPolishPlanner: ContentPolishPlanner,
    private val topicExtractor: TopicExtractor,
    private val noteInsightPlanner: NoteInsightPlanner,
    private val voiceTranscriptionPlanner: VoiceTranscriptionPlanner,
    private val noteId: Long?,
    private val initialContent: String,
    private val initialTopic: String,
    private val initialFolderKey: String?,
    private val initialTags: List<String>,
    private val initialKnowledgeTrust: KnowledgeTrust,
) : ViewModel() {
    private data class PersistedSnapshot(
        val content: String = "",
        val topic: String = "",
        val folderKey: String? = null,
        val tags: List<String> = emptyList(),
        val status: NoteStatus = NoteStatus.IDEA,
        val horizon: NoteHorizon = NoteHorizon.MEDIUM,
        val knowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
        val isArchived: Boolean = false,
    )

    private var persistedSnapshot = PersistedSnapshot()

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            isNew = noteId == null,
            noteId = noteId,
            isLoading = noteId != null,
            content = if (noteId == null) initialContent else "",
            topic = if (noteId == null) initialTopic else "",
            horizon = if (noteId == null) NoteHorizon.inferFrom(initialContent, initialTopic) else NoteHorizon.MEDIUM,
            knowledgeTrust = if (noteId == null) initialKnowledgeTrust else KnowledgeTrust.NONE,
            folderKey = if (noteId == null) initialFolderKey else null,
            folderSource = if (noteId == null && initialFolderKey != null) FolderSource.MANUAL else FolderSource.RULE,
            tags = if (noteId == null) initialTags else emptyList(),
            tagSource = if (noteId == null && initialTags.isNotEmpty()) TagSource.MANUAL else TagSource.RULE,
            topicEdited = noteId == null && initialTopic.isNotBlank(),
            folderEdited = noteId == null && initialFolderKey != null,
            tagsEdited = noteId == null && initialTags.isNotEmpty(),
            hasUnsavedChanges = noteId == null && (
                initialContent.isNotBlank() ||
                    initialTopic.isNotBlank() ||
                    initialFolderKey != null ||
                    initialTags.isNotEmpty()
                ),
        )
    )
    val uiState = _uiState

    private val _events = MutableSharedFlow<NoteEditorEvent>()
    val events = _events.asSharedFlow()

    init {
        if (noteId != null) {
            observeHistory(noteId)
            loadNote(noteId)
        }
    }

    fun onContentChange(value: String) {
        updateDirtyState {
            it.copy(
                content = value,
                aiSummary = "",
                aiKeyPoints = emptyList(),
                polishedOriginalContent = null,
                polishedCandidateContent = null,
            )
        }
    }

    fun onTopicChange(value: String) {
        updateDirtyState { it.copy(topic = value, topicEdited = true) }
    }

    fun onFolderChange(folderKey: String?) {
        updateDirtyState {
            it.copy(
                folderKey = folderKey,
                folderSource = FolderSource.MANUAL,
                folderEdited = true,
            )
        }
    }

    fun addTag(raw: String) {
        val nextTag = NoteTagCodec.normalizeOne(raw) ?: return
        val state = _uiState.value
        if (state.tags.size >= 3 && nextTag !in state.tags) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("最多保留 3 个标签"))
            }
            return
        }

        updateDirtyState { current ->
            val nextTags = NoteTagCodec.normalize(current.tags + nextTag)
            current.copy(tags = nextTags, tagsEdited = true)
        }
    }

    fun removeTag(tag: String) {
        updateDirtyState { state ->
            state.copy(
                tags = state.tags.filterNot { it == tag },
                tagsEdited = true,
            )
        }
    }

    fun onStatusChange(status: NoteStatus) {
        updateDirtyState { it.copy(status = status) }
    }

    fun onCaptureTypeChange(typeLabel: String) {
        updateDirtyState { state -> applyTextCaptureTypeSelection(state, typeLabel) }
    }

    fun onHorizonChange(horizon: NoteHorizon) {
        updateDirtyState { it.copy(horizon = horizon) }
    }

    fun onArchivedChange(archived: Boolean) {
        updateDirtyState { it.copy(isArchived = archived) }
    }

    fun onKnowledgeTrustChange(knowledgeTrust: KnowledgeTrust) {
        updateDirtyState { it.copy(knowledgeTrust = knowledgeTrust) }
    }

    fun polishContent() {
        val state = _uiState.value
        if (state.content.isBlank()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("先写一点内容，再交给 AI 润色"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPolishingContent = true) }
            when (val result = contentPolishPlanner.polish(state.content)) {
                is ContentPolishResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPolishingContent = false,
                            polishedOriginalContent = state.content,
                            polishedCandidateContent = result.polishedText,
                        )
                    }
                    _events.emit(NoteEditorEvent.Message("AI 已生成润色结果，长按可对照原文"))
                    return@launch
                }
                ContentPolishResult.NoChange -> {
                    _events.emit(NoteEditorEvent.Message("AI 返回内容与当前正文接近，没有生成新的润色稿"))
                }
                is ContentPolishResult.Failure -> {
                    _events.emit(NoteEditorEvent.Message(result.message))
                }
            }

            _uiState.update { it.copy(isPolishingContent = false) }
        }
    }

    fun generateTitle() {
        val state = _uiState.value
        if (state.content.isBlank()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("先写一点正文，再生成标题"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTopic = true) }
            val result = runCatching { topicExtractor.extract(state.content) }.getOrElse { error ->
                _uiState.update { it.copy(isRefreshingTopic = false) }
                _events.emit(NoteEditorEvent.Message(error.message ?: "AI 标题生成失败"))
                return@launch
            }
            val generatedTitle = normalizeEditorTitle(result.suggestion.topic)
            if (generatedTitle.isBlank()) {
                _uiState.update { it.copy(isRefreshingTopic = false) }
                _events.emit(NoteEditorEvent.Message(result.notice ?: "这次没有生成可用标题"))
                return@launch
            }
            updateDirtyState {
                it.copy(
                    topic = generatedTitle,
                    topicSource = result.suggestion.source,
                    topicEdited = true,
                    isRefreshingTopic = false,
                )
            }
            _events.emit(NoteEditorEvent.Message(result.notice ?: "标题已生成，记得保存"))
        }
    }

    fun ensureVoiceAiInsight() {
        val state = _uiState.value
        val transcript = voiceTranscriptFromContent(state.content)
        if (
            transcript.isBlank() ||
            state.isExtractingVoiceInfo ||
            (state.aiSummary.isNotBlank() && state.aiKeyPoints.isNotEmpty())
        ) return

        viewModelScope.launch {
            val startContent = _uiState.value.content
            val startTranscript = voiceTranscriptFromContent(startContent)
            if (startTranscript.isBlank()) return@launch
            _uiState.update { it.copy(isExtractingVoiceInfo = true) }
            val topicResult = runCatching { topicExtractor.extract(startTranscript) }.getOrNull()
            when (val insightResult = noteInsightPlanner.generate(startTranscript)) {
                is NoteInsightResult.Success -> {
                    val generatedTitle = topicResult
                        ?.suggestion
                        ?.topic
                        ?.let(::normalizeEditorTitle)
                        .orEmpty()
                    _uiState.update {
                        if (voiceTranscriptFromContent(it.content) != startTranscript) {
                            return@update it.copy(isExtractingVoiceInfo = false)
                        }
                        it.copy(
                            aiSummary = insightResult.insight.summary,
                            aiKeyPoints = insightResult.insight.keyPoints,
                            topic = generatedTitle.ifBlank { it.topic },
                            topicSource = if (generatedTitle.isBlank()) it.topicSource else topicResult?.suggestion?.source ?: it.topicSource,
                            topicEdited = it.topicEdited || generatedTitle.isNotBlank(),
                            tags = appendCaptureTag(it.tags, "语音"),
                            tagsEdited = true,
                            isExtractingVoiceInfo = false,
                        )
                    }
                }
                NoteInsightResult.BlankContent -> {
                    _uiState.update { it.copy(isExtractingVoiceInfo = false) }
                }
                is NoteInsightResult.Failure -> {
                    _uiState.update { it.copy(isExtractingVoiceInfo = false) }
                }
            }
        }
    }

    fun ensureVoiceTranscription(audioPathOverride: String = "") {
        val state = _uiState.value
        val audioPath = audioPathOverride.trim().ifBlank { voiceAudioPathFromContent(state.content) }
        if (!shouldAttemptVoiceTranscription(state.content, state.isTranscribingVoice, audioPath)) return

        viewModelScope.launch {
            updateDirtyState { current ->
                val contentWithAudio = if (audioPathOverride.isNotBlank()) {
                    replaceEditorCaptureField(current.content, VoiceAudioFieldLabel, audioPath)
                } else {
                    current.content
                }
                val contentWithSections = ensureEditorCaptureSections(
                    content = contentWithAudio,
                    sectionLabels = listOf(VoiceTranscriptFieldLabel, VoiceRecognitionFieldLabel),
                )
                current.copy(
                    content = replaceEditorCaptureField(
                        contentWithSections,
                        VoiceRecognitionFieldLabel,
                        "正在转写音频…",
                    ),
                    isTranscribingVoice = true,
                )
            }

            when (val result = voiceTranscriptionPlanner.transcribe(audioPath = audioPath)) {
                is VoiceTranscriptionResult.Success -> {
                    var updatedState: NoteEditorUiState? = null
                    updateDirtyState { current ->
                        val currentTranscript = voiceTranscriptFromContent(current.content)
                        val nextTranscript = currentTranscript.ifBlank { result.transcript }
                        val generatedTitle = normalizeEditorTitle(result.topic)
                        val contentWithSections = ensureEditorCaptureSections(
                            content = current.content,
                            sectionLabels = listOf(VoiceTranscriptFieldLabel, VoiceRecognitionFieldLabel),
                        )
                        val contentWithTranscript = replaceEditorCaptureField(
                            contentWithSections,
                            VoiceTranscriptFieldLabel,
                            nextTranscript,
                        )
                        val next = current.copy(
                            content = replaceEditorCaptureField(
                                contentWithTranscript,
                                VoiceRecognitionFieldLabel,
                                voiceTranscriptionCompletedMessage(result),
                            ),
                            topic = generatedTitle.ifBlank { current.topic },
                            topicSource = if (generatedTitle.isBlank()) current.topicSource else TopicSource.AI,
                            topicEdited = current.topicEdited || generatedTitle.isNotBlank(),
                            tags = appendCaptureTag(current.tags, "语音"),
                            tagsEdited = true,
                            isTranscribingVoice = false,
                        )
                        updatedState = next
                        next
                    }
                    updatedState?.let { persistVoiceTranscriptionIfNeeded(it) }
                    if (updatedState?.noteId == null) {
                        ensureVoiceAiInsight()
                    }
                    _events.emit(NoteEditorEvent.Message("语音转写已完成"))
                }
                is VoiceTranscriptionResult.Failure -> {
                    updateDirtyState { current ->
                        val contentWithSections = ensureEditorCaptureSections(
                            content = current.content,
                            sectionLabels = listOf(VoiceTranscriptFieldLabel, VoiceRecognitionFieldLabel),
                        )
                        current.copy(
                            content = replaceEditorCaptureField(
                                contentWithSections,
                                VoiceRecognitionFieldLabel,
                                "转写失败：${result.message}",
                            ),
                            isTranscribingVoice = false,
                        )
                    }
                    _events.emit(NoteEditorEvent.Message(result.message))
                }
            }
        }
    }

    fun polishTitle() {
        val state = _uiState.value
        if (state.topic.isBlank() && state.content.isBlank()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("先写标题或正文，再交给 AI 润色"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPolishingTitle = true) }
            when (val result = contentPolishPlanner.polishTitle(state.topic, state.content)) {
                is ContentPolishResult.Success -> {
                    updateDirtyState {
                        it.copy(
                            topic = normalizeEditorTitle(result.polishedText),
                            topicSource = TopicSource.AI,
                            topicEdited = true,
                            isPolishingTitle = false,
                        )
                    }
                    _events.emit(NoteEditorEvent.Message("标题已润色，记得保存"))
                    return@launch
                }
                ContentPolishResult.NoChange -> {
                    _events.emit(NoteEditorEvent.Message("AI 返回标题与当前标题接近，没有生成新标题"))
                }
                is ContentPolishResult.Failure -> {
                    _events.emit(NoteEditorEvent.Message(result.message))
                }
            }

            _uiState.update { it.copy(isPolishingTitle = false) }
        }
    }

    fun applyPolishedContent() {
        val state = _uiState.value
        val polished = state.polishedCandidateContent ?: return
        updateDirtyState { current ->
            current.copy(
                content = polished,
                polishedOriginalContent = null,
                polishedCandidateContent = null,
            )
        }
        viewModelScope.launch {
            _events.emit(NoteEditorEvent.Message("已应用润色内容，记得保存"))
        }
    }

    fun discardPolishedContent() {
        _uiState.update {
            it.copy(
                polishedOriginalContent = null,
                polishedCandidateContent = null,
            )
        }
        viewModelScope.launch {
            _events.emit(NoteEditorEvent.Message("已保留原文"))
        }
    }

    fun save(exitAfterSave: Boolean = false) {
        val state = _uiState.value
        if (state.content.isBlank()) {
            viewModelScope.launch { _events.emit(NoteEditorEvent.Message("先写下你的想法")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            if (state.isNew) {
                val hasPrecomputedInsight = state.aiSummary.isNotBlank() && state.aiKeyPoints.isNotEmpty()
                noteRepository.createNote(
                    content = state.content,
                    topic = state.topic,
                    folderKey = state.folderKey,
                    tags = state.tags,
                    status = state.status,
                    horizon = state.horizon,
                    knowledgeTrust = state.knowledgeTrust,
                    isArchived = state.isArchived,
                    folderManuallyEdited = state.folderEdited,
                    topicManuallyEdited = state.topicEdited,
                    tagsManuallyEdited = state.tagsEdited,
                    aiSummary = if (hasPrecomputedInsight) state.aiSummary else "",
                    aiKeyPoints = if (hasPrecomputedInsight) state.aiKeyPoints else emptyList(),
                    aiInsightContentHash = if (hasPrecomputedInsight) {
                        NoteInsightPlanner.contentHash(state.content)
                    } else {
                        ""
                    },
                    aiInsightUpdatedAt = if (hasPrecomputedInsight) System.currentTimeMillis() else 0L,
                )
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(NoteEditorEvent.Message("已保存到 MindFlow"))
                _events.emit(NoteEditorEvent.NavigateBack)
            } else {
                val existingId = state.noteId ?: return@launch
                noteRepository.updateNote(
                    noteId = existingId,
                    content = state.content,
                    topic = state.topic,
                    folderKey = state.folderKey,
                    tags = state.tags,
                    status = state.status,
                    horizon = state.horizon,
                    knowledgeTrust = state.knowledgeTrust,
                    isArchived = state.isArchived,
                    folderManuallyEdited = state.folderEdited,
                    topicManuallyEdited = state.topicEdited,
                    tagsManuallyEdited = state.tagsEdited,
                )
                persistedSnapshot = PersistedSnapshot(
                    content = state.content,
                    topic = state.topic,
                    folderKey = state.folderKey,
                    tags = state.tags,
                    status = state.status,
                    horizon = state.horizon,
                    isArchived = state.isArchived,
                )
                loadNote(existingId)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        folderEdited = false,
                        topicEdited = false,
                        tagsEdited = false,
                        hasUnsavedChanges = false,
                    )
                }
                _events.emit(NoteEditorEvent.Message("已更新"))
                if (exitAfterSave) {
                    _events.emit(NoteEditorEvent.NavigateBack)
                }
            }
        }
    }

    internal fun saveWithCaptureAction(action: CapturePostAction) {
        updateDirtyState { state -> applyCaptureActionState(state, action) }
        save(exitAfterSave = false)
    }

    fun retriggerTopicExtraction() {
        val existingId = _uiState.value.noteId ?: return
        if (hasUnsavedChanges()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("你有未保存修改，请先保存记录再重新提取主题"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTopic = true) }
            val result = noteRepository.retriggerTopicExtraction(existingId)
            loadNote(existingId)
            _uiState.update { it.copy(isRefreshingTopic = false, topicEdited = false) }
            _events.emit(
                NoteEditorEvent.Message(
                    result.notice ?: "主题已重新生成"
                )
            )
        }
    }

    fun retriggerFolderClassification() {
        val existingId = _uiState.value.noteId ?: return
        if (hasUnsavedChanges()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("你有未保存修改，请先保存记录再重新分类"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingFolder = true) }
            val result = noteRepository.retriggerFolderClassification(existingId)
            loadNote(existingId)
            _uiState.update { it.copy(isRefreshingFolder = false, folderEdited = false, hasUnsavedChanges = false) }
            _events.emit(NoteEditorEvent.Message(result.notice ?: "文件夹已重新分类"))
        }
    }

    fun retriggerTagExtraction() {
        val existingId = _uiState.value.noteId ?: return
        if (hasUnsavedChanges()) {
            viewModelScope.launch {
                _events.emit(NoteEditorEvent.Message("你有未保存修改，请先保存记录再重新提取标签"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTags = true) }
            val result = noteRepository.retriggerTagExtraction(existingId)
            loadNote(existingId)
            _uiState.update { it.copy(isRefreshingTags = false, tagsEdited = false, hasUnsavedChanges = false) }
            _events.emit(
                NoteEditorEvent.Message(
                    result.notice ?: "标签已重新生成"
                )
            )
        }
    }

    private fun observeHistory(noteId: Long) {
        viewModelScope.launch {
            noteRepository.observeStatusHistory(noteId).collectLatest { history ->
                _uiState.update {
                    it.copy(
                        statusHistory = history,
                        latestDoneAt = history.firstOrNull { entry -> entry.toStatus == NoteStatus.DONE }?.changedAt,
                    )
                }
            }
        }
    }

    private fun loadNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getNote(noteId)
            if (note == null) {
                _events.emit(NoteEditorEvent.Message("记录不存在"))
                return@launch
            }

            persistedSnapshot = PersistedSnapshot(
                content = note.content,
                topic = note.topic,
                folderKey = note.folderKey,
                tags = note.tags,
                status = note.status,
                horizon = note.horizon,
                knowledgeTrust = note.knowledgeTrust,
                isArchived = note.isArchived,
            )
            _uiState.update {
                it.copy(
                    isNew = false,
                    noteId = note.id,
                    isLoading = false,
                    content = note.content,
                    topic = note.topic,
                    topicSource = note.topicSource,
                    folderKey = note.folderKey,
                    folderSource = note.folderSource,
                    tags = note.tags,
                    tagSource = note.tagSource,
                    status = note.status,
                    horizon = note.horizon,
                    knowledgeTrust = note.knowledgeTrust,
                    isArchived = note.isArchived,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    aiSummary = validAiSummary(note),
                    aiKeyPoints = validAiKeyPoints(note),
                    polishedOriginalContent = null,
                    polishedCandidateContent = null,
                    folderEdited = false,
                    topicEdited = false,
                    tagsEdited = false,
                    hasUnsavedChanges = false,
                )
            }
            maybeEnsureAiInsight(note)
        }
    }

    private suspend fun maybeEnsureAiInsight(note: NoteEntity) {
        if (!shouldAutoGenerateTextInsight(note.content) && !shouldAutoGenerateVoiceInsight(note.content)) return
        val changed = noteRepository.ensureAiInsight(note.id)
        if (!changed) return
        val refreshed = noteRepository.getNote(note.id) ?: return
        _uiState.update {
            it.copy(
                topic = refreshed.topic,
                topicSource = refreshed.topicSource,
                aiSummary = validAiSummary(refreshed),
                aiKeyPoints = validAiKeyPoints(refreshed),
            )
        }
    }

    private suspend fun persistVoiceTranscriptionIfNeeded(state: NoteEditorUiState) {
        val existingId = state.noteId ?: return
        noteRepository.updateNote(
            noteId = existingId,
            content = state.content,
            topic = state.topic,
            folderKey = state.folderKey,
            tags = state.tags,
            status = state.status,
            horizon = state.horizon,
            knowledgeTrust = state.knowledgeTrust,
            isArchived = state.isArchived,
            folderManuallyEdited = state.folderEdited,
            topicManuallyEdited = state.topicEdited,
            tagsManuallyEdited = state.tagsEdited,
        )
        persistedSnapshot = PersistedSnapshot(
            content = state.content,
            topic = state.topic,
            folderKey = state.folderKey,
            tags = state.tags,
            status = state.status,
            horizon = state.horizon,
            knowledgeTrust = state.knowledgeTrust,
            isArchived = state.isArchived,
        )
        _uiState.update { current ->
            if (current.noteId != existingId || current.content != state.content) {
                current
            } else {
                current.copy(
                    folderEdited = false,
                    topicEdited = false,
                    tagsEdited = false,
                    hasUnsavedChanges = false,
                )
            }
        }
    }

    private fun validAiSummary(note: NoteEntity): String =
        if (hasValidAiInsight(note)) note.aiSummary else ""

    private fun validAiKeyPoints(note: NoteEntity): List<String> =
        if (hasValidAiInsight(note)) note.aiKeyPoints else emptyList()

    private fun hasValidAiInsight(note: NoteEntity): Boolean =
        note.aiSummary.isNotBlank() &&
            note.aiKeyPoints.isNotEmpty() &&
            note.aiInsightContentHash == NoteInsightPlanner.contentHash(note.content)

    private fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        return state.content != persistedSnapshot.content ||
            state.topic != persistedSnapshot.topic ||
            state.folderKey != persistedSnapshot.folderKey ||
            state.tags != persistedSnapshot.tags ||
            state.status != persistedSnapshot.status ||
            state.horizon != persistedSnapshot.horizon ||
            state.knowledgeTrust != persistedSnapshot.knowledgeTrust ||
            state.isArchived != persistedSnapshot.isArchived ||
            state.folderEdited ||
            state.topicEdited ||
            state.tagsEdited
    }

    private fun updateDirtyState(
        transform: (NoteEditorUiState) -> NoteEditorUiState,
    ) {
        _uiState.update { current ->
            val next = transform(current)
            next.copy(
                hasUnsavedChanges = next.content != persistedSnapshot.content ||
                    next.topic != persistedSnapshot.topic ||
                    next.folderKey != persistedSnapshot.folderKey ||
                    next.tags != persistedSnapshot.tags ||
                    next.status != persistedSnapshot.status ||
                    next.horizon != persistedSnapshot.horizon ||
                    next.knowledgeTrust != persistedSnapshot.knowledgeTrust ||
                    next.isArchived != persistedSnapshot.isArchived ||
                    next.folderEdited ||
                    next.topicEdited ||
                    next.tagsEdited,
            )
        }
    }

    private fun normalizeEditorTitle(raw: String): String =
        raw
            .lineSequence()
            .map { it.trim().trim('"', '“', '”', '\'', '`') }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .take(48)

    private fun voiceTranscriptionCompletedMessage(result: VoiceTranscriptionResult.Success): String {
        val providerLabel = when (result.provider) {
            com.mindflow.app.data.ai.AiProvider.ON_DEVICE -> "Gemma 4 端侧"
            com.mindflow.app.data.ai.AiProvider.CLOUD -> "云侧"
        }
        val seconds = (result.latencyMs / 1000.0).let { "%.1f".format(it) }
        return "$providerLabel 已完成转写（${seconds}s）"
    }




    companion object {
        fun factory(
            noteRepository: NoteRepository,
            contentPolishPlanner: ContentPolishPlanner,
            topicExtractor: TopicExtractor,
            noteInsightPlanner: NoteInsightPlanner,
            voiceTranscriptionPlanner: VoiceTranscriptionPlanner,
            noteId: Long?,
            initialContent: String,
            initialTopic: String,
            initialFolderKey: String?,
            initialTags: List<String>,
            initialKnowledgeTrust: KnowledgeTrust,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteEditorViewModel(
                    noteRepository = noteRepository,
                    contentPolishPlanner = contentPolishPlanner,
                    topicExtractor = topicExtractor,
                    noteInsightPlanner = noteInsightPlanner,
                    voiceTranscriptionPlanner = voiceTranscriptionPlanner,
                    noteId = noteId,
                    initialContent = initialContent,
                    initialTopic = initialTopic,
                    initialFolderKey = initialFolderKey,
                    initialTags = initialTags,
                    initialKnowledgeTrust = initialKnowledgeTrust,
                )
            }
        }
    }
}
