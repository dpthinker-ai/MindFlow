package com.mindflow.app.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate
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
    val isPolishingContent: Boolean = false,
    val content: String = "",
    val topic: String = "",
    val topicSource: TopicSource = TopicSource.RULE,
    val folderKey: String? = null,
    val folderSource: FolderSource = FolderSource.RULE,
    val tags: List<String> = emptyList(),
    val tagSource: TagSource = TagSource.RULE,
    val status: NoteStatus = NoteStatus.IDEA,
    val isArchived: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val latestDoneAt: Long? = null,
    val statusHistory: List<NoteStatusHistoryEntity> = emptyList(),
    val relatedNotes: List<NoteEntity> = emptyList(),
    val polishedOriginalContent: String? = null,
    val polishedCandidateContent: String? = null,
    val folderEdited: Boolean = false,
    val topicEdited: Boolean = false,
    val tagsEdited: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

sealed interface NoteEditorEvent {
    data object NavigateBack : NoteEditorEvent
    data class Message(val text: String) : NoteEditorEvent
}

class NoteEditorViewModel(
    private val noteRepository: NoteRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
    private val noteId: Long?,
    private val initialContent: String,
    private val initialTopic: String,
) : ViewModel() {
    private data class PersistedSnapshot(
        val content: String = "",
        val topic: String = "",
        val folderKey: String? = null,
        val tags: List<String> = emptyList(),
        val status: NoteStatus = NoteStatus.IDEA,
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
            topicEdited = noteId == null && initialTopic.isNotBlank(),
            hasUnsavedChanges = noteId == null && (initialContent.isNotBlank() || initialTopic.isNotBlank()),
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

    fun onArchivedChange(archived: Boolean) {
        updateDirtyState { it.copy(isArchived = archived) }
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
            val settings = aiSettingsRepository.getCurrent()
            if (!settings.isConfigured) {
                _uiState.update { it.copy(isPolishingContent = false) }
                _events.emit(NoteEditorEvent.Message("请先在设置里完成 AI 配置"))
                return@launch
            }

            val dayKey = LocalDate.now().toString()
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )

            when (val result = aiServiceClient.polishContent(settings, state.content)) {
                is AiChatResult.Success -> {
                    aiSettingsRepository.recordUsage(
                        successIncrement = 1,
                        tokenIncrement = result.totalTokens ?: 0,
                        dayKey = dayKey,
                    )
                    val polished = normalizePolishedContent(result.content)
                    if (polished.isBlank()) {
                        _events.emit(NoteEditorEvent.Message("AI 没有返回可用内容"))
                    } else if (polished == state.content.trim()) {
                        _events.emit(NoteEditorEvent.Message("AI 返回内容与当前正文接近，没有生成新的润色稿"))
                    } else {
                        _uiState.update {
                            it.copy(
                                isPolishingContent = false,
                                polishedOriginalContent = state.content,
                                polishedCandidateContent = polished,
                            )
                        }
                        _events.emit(NoteEditorEvent.Message("AI 已生成润色结果，长按可对照原文"))
                        return@launch
                    }
                }
                is AiChatResult.Failure -> {
                    _events.emit(NoteEditorEvent.Message(result.message))
                }
            }

            _uiState.update { it.copy(isPolishingContent = false) }
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
                noteRepository.createNote(
                    content = state.content,
                    folderKey = state.folderKey,
                    folderManuallyEdited = state.folderEdited,
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
                    isArchived = note.isArchived,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    polishedOriginalContent = null,
                    polishedCandidateContent = null,
                    folderEdited = false,
                    topicEdited = false,
                    tagsEdited = false,
                    hasUnsavedChanges = false,
                )
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        return state.content != persistedSnapshot.content ||
            state.topic != persistedSnapshot.topic ||
            state.folderKey != persistedSnapshot.folderKey ||
            state.tags != persistedSnapshot.tags ||
            state.status != persistedSnapshot.status ||
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
                    next.isArchived != persistedSnapshot.isArchived ||
                    next.folderEdited ||
                    next.topicEdited ||
                    next.tagsEdited,
            )
        }
    }

    private fun normalizePolishedContent(raw: String): String =
        raw
            .trim()
            .removePrefix("```markdown")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            aiSettingsRepository: AiSettingsRepository,
            aiServiceClient: AiServiceClient,
            noteId: Long?,
            initialContent: String,
            initialTopic: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteEditorViewModel(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    aiServiceClient = aiServiceClient,
                    noteId = noteId,
                    initialContent = initialContent,
                    initialTopic = initialTopic,
                )
            }
        }
    }
}
