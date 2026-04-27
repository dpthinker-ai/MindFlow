package com.mindflow.app.ui.screens.reviewchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatTurnEvent
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnStage
import com.mindflow.app.ui.navigation.ReviewChatSeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReviewChatProgressStepState {
    RUNNING,
    DONE,
    FAILED,
}

data class ReviewChatProgressStep(
    val id: String,
    val title: String,
    val detail: String = "",
    val state: ReviewChatProgressStepState = ReviewChatProgressStepState.RUNNING,
)

data class ReviewChatUiState(
    val title: String = "和历史聊聊",
    val messages: List<ReviewChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val providerLine: String = "",
    val streamingAnswer: String = "",
    val streamingProvider: ReviewChatProvider? = null,
    val generationStatus: String = "",
    val progressSteps: List<ReviewChatProgressStep> = emptyList(),
    val errorMessage: String? = null,
    val savedSessionId: Long? = null,
) {
    val canSend: Boolean
        get() = !isReadOnly && !isSending && draft.isNotBlank()

    val canSave: Boolean
        get() = !isReadOnly && !isSaving && messages.any { it.role == ReviewChatMessageRole.ASSISTANT }
}

class ReviewChatViewModel(
    private val seed: ReviewChatSeed,
    private val answerTurnStream: (ReviewChatTurnRequest) -> Flow<ReviewChatTurnEvent>,
    private val savedConversationRepository: ReviewChatSavedConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewChatUiState())
    val uiState: StateFlow<ReviewChatUiState> = _uiState

    private var pendingQuestion: String? = null
    private var pendingPriorMessages: List<ReviewChatMessage> = emptyList()
    private val cacheMutex = Mutex()

    init {
        when {
            seed.savedSessionId != null -> loadSavedSession(seed.savedSessionId)
            seed.initialQuestion.isNotBlank() -> submitQuestion(
                question = seed.initialQuestion.trim(),
                appendUserMessage = true,
            )
            else -> loadWorkingSession()
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
        cacheWorkingSession()
    }

    fun sendDraft() {
        val question = _uiState.value.draft.trim()
        if (question.isBlank()) return
        submitQuestion(question = question, appendUserMessage = true)
    }

    fun retry() {
        val question = pendingQuestion ?: return
        submitQuestion(question = question, appendUserMessage = false)
    }

    fun saveConversation() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val sessionId = savedConversationRepository.saveSession(
                sessionId = state.savedSessionId,
                title = state.title.ifBlank { state.messages.firstOrNull()?.content?.take(18).orEmpty() },
                messages = state.messages,
            )
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isReadOnly = true,
                    savedSessionId = sessionId,
                )
            }
        }
    }

    suspend fun flushWorkingSession() {
        persistWorkingSession()
    }

    private fun loadWorkingSession() {
        viewModelScope.launch {
            val session = savedConversationRepository.getLatestWorkingSession() ?: return@launch
            _uiState.update {
                it.copy(
                    title = session.title,
                    messages = session.messages,
                    draft = session.draft,
                    isReadOnly = false,
                    savedSessionId = session.sessionId,
                )
            }
        }
    }

    private fun loadSavedSession(sessionId: Long) {
        viewModelScope.launch {
            val session = savedConversationRepository.getSession(sessionId)
            if (session == null) {
                _uiState.update {
                    it.copy(
                        isReadOnly = true,
                        savedSessionId = sessionId,
                        errorMessage = "没找到这段已保存的对话。",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    title = session.title,
                    messages = session.messages,
                    isReadOnly = true,
                    savedSessionId = session.sessionId,
                    draft = "",
                )
            }
        }
    }

    private fun submitQuestion(
        question: String,
        appendUserMessage: Boolean,
    ) {
        val current = _uiState.value
        if (current.isSending || current.isReadOnly || question.isBlank()) return
        val priorMessages = if (appendUserMessage) current.messages else pendingPriorMessages
        pendingQuestion = question
        pendingPriorMessages = priorMessages
        if (appendUserMessage) {
            _uiState.update {
                it.copy(
                    messages = it.messages + ReviewChatMessage(
                        role = ReviewChatMessageRole.USER,
                        content = question,
                        createdAt = System.currentTimeMillis(),
                    ),
                    draft = "",
                    isSending = true,
                    streamingAnswer = "",
                    streamingProvider = null,
                    generationStatus = "正在准备问题…",
                    progressSteps = listOf(
                        ReviewChatProgressStep(
                            id = ReviewChatTurnStage.PREPARE.name,
                            title = "准备问题",
                            detail = "整理你的提问和当前对话上下文。",
                        )
                    ),
                    errorMessage = null,
                )
            }
            cacheWorkingSession()
        } else {
            _uiState.update {
                it.copy(
                    isSending = true,
                    streamingAnswer = "",
                    streamingProvider = null,
                    generationStatus = "正在准备问题…",
                    progressSteps = listOf(
                        ReviewChatProgressStep(
                            id = ReviewChatTurnStage.PREPARE.name,
                            title = "准备问题",
                            detail = "整理你的提问和当前对话上下文。",
                        )
                    ),
                    errorMessage = null,
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                answerTurnStream(
                    ReviewChatTurnRequest(
                        sessionId = seed.requestId.toString(),
                        question = question,
                        priorMessages = priorMessages,
                    )
                ).collect { event ->
                    when (event) {
                        is ReviewChatTurnEvent.Status -> {
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = event.message,
                                    streamingProvider = event.provider,
                                    providerLine = event.providerLine,
                                    progressSteps = state.progressSteps.recordStatus(event),
                                    errorMessage = null,
                                )
                            }
                        }

                        is ReviewChatTurnEvent.Partial -> {
                            _uiState.update { state ->
                                state.copy(
                                    streamingAnswer = event.content,
                                    streamingProvider = event.provider,
                                    providerLine = event.providerLine,
                                    generationStatus = "",
                                    progressSteps = state.progressSteps.recordStreaming(event.providerLine),
                                    errorMessage = null,
                                )
                            }
                        }

                        is ReviewChatTurnEvent.Complete -> {
                            val result = event.result
                            pendingQuestion = null
                            pendingPriorMessages = emptyList()
                            _uiState.update { state ->
                                state.copy(
                                    title = state.title.takeIf { it != "和历史聊聊" } ?: result.titleSuggestion.ifBlank { state.title },
                                    messages = state.messages + ReviewChatMessage(
                                        role = ReviewChatMessageRole.ASSISTANT,
                                        content = result.answer,
                                        structuredAnswer = result.structuredAnswer,
                                        provider = result.provider,
                                        createdAt = System.currentTimeMillis(),
                                        referencedNoteId = result.referencedNoteId,
                                        referencedNotes = result.referencedNotes,
                                        skillWebView = result.skillWebView,
                                    ),
                                    isSending = false,
                                    providerLine = result.providerLine,
                                    streamingAnswer = "",
                                    streamingProvider = null,
                                    generationStatus = "",
                                    progressSteps = state.progressSteps.recordComplete(result.providerLine),
                                    errorMessage = null,
                                )
                            }
                            cacheWorkingSession()
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        streamingAnswer = "",
                        streamingProvider = null,
                        generationStatus = "",
                        progressSteps = it.progressSteps.recordFailure(error.message ?: "生成回答失败，请重试。"),
                        errorMessage = error.message ?: "生成回答失败，请重试。",
                    )
                }
                cacheWorkingSession()
            }
        }
    }

    private fun cacheWorkingSession() {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            persistWorkingSession()
        }
    }

    private suspend fun persistWorkingSession() {
        cacheMutex.withLock {
            val state = _uiState.value
            if (state.isReadOnly || (state.messages.isEmpty() && state.draft.isBlank())) return@withLock
            val sessionId = savedConversationRepository.cacheWorkingSession(
                sessionId = state.savedSessionId,
                title = workingSessionTitle(state),
                messages = state.messages,
                draft = state.draft,
            )
            _uiState.update { current ->
                if (!current.isReadOnly && current.savedSessionId == state.savedSessionId) {
                    current.copy(savedSessionId = sessionId)
                } else {
                    current
                }
            }
        }
    }

    private fun workingSessionTitle(state: ReviewChatUiState): String {
        if (state.title.isNotBlank() && state.title != "和历史聊聊") return state.title
        return state.messages.firstOrNull { it.role == ReviewChatMessageRole.USER }
            ?.content
            ?.take(18)
            ?: state.draft.take(18).ifBlank { "和历史聊聊" }
    }

    companion object {
        fun factory(
            seed: ReviewChatSeed,
            answerTurnStream: (ReviewChatTurnRequest) -> Flow<ReviewChatTurnEvent>,
            savedConversationRepository: ReviewChatSavedConversationRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReviewChatViewModel(
                    seed = seed,
                    answerTurnStream = answerTurnStream,
                    savedConversationRepository = savedConversationRepository,
                )
            }
        }
    }
}

private fun List<ReviewChatProgressStep>.recordStatus(
    event: ReviewChatTurnEvent.Status,
): List<ReviewChatProgressStep> {
    val title = event.message.trim().removeSuffix("…").ifBlank { event.stage.defaultProgressTitle() }
    val detail = event.detail.ifBlank { event.providerLine }
    val incoming = ReviewChatProgressStep(
        id = event.stepId.ifBlank { event.stage.name },
        title = title,
        detail = detail,
        state = if (event.inProgress) ReviewChatProgressStepState.RUNNING else ReviewChatProgressStepState.DONE,
    )
    return upsertProgressStep(incoming)
}

private fun List<ReviewChatProgressStep>.recordStreaming(
    providerLine: String,
): List<ReviewChatProgressStep> = upsertProgressStep(
    ReviewChatProgressStep(
        id = ReviewChatTurnStage.GENERATE.name,
        title = "模型正在生成回答",
        detail = providerLine,
        state = ReviewChatProgressStepState.RUNNING,
    )
)

private fun List<ReviewChatProgressStep>.recordComplete(
    providerLine: String,
): List<ReviewChatProgressStep> = upsertProgressStep(
    ReviewChatProgressStep(
        id = ReviewChatTurnStage.COMPLETE.name,
        title = "回答生成完成",
        detail = providerLine,
        state = ReviewChatProgressStepState.DONE,
    ),
    markExistingRunningDone = true,
)

private fun List<ReviewChatProgressStep>.recordFailure(
    message: String,
): List<ReviewChatProgressStep> = map { step ->
    if (step.state == ReviewChatProgressStepState.RUNNING) {
        step.copy(state = ReviewChatProgressStepState.FAILED)
    } else {
        step
    }
}.upsertProgressStep(
    ReviewChatProgressStep(
        id = "FAILED",
        title = "回答生成失败",
        detail = message,
        state = ReviewChatProgressStepState.FAILED,
    ),
    markExistingRunningDone = false,
)

private fun List<ReviewChatProgressStep>.upsertProgressStep(
    incoming: ReviewChatProgressStep,
    markExistingRunningDone: Boolean = true,
): List<ReviewChatProgressStep> {
    val normalized = if (markExistingRunningDone) {
        map { step ->
            if (step.state == ReviewChatProgressStepState.RUNNING && step.id != incoming.id) {
                step.copy(state = ReviewChatProgressStepState.DONE)
            } else {
                step
            }
        }
    } else {
        this
    }
    val index = normalized.indexOfFirst { it.id == incoming.id }
    return if (index >= 0) {
        normalized.toMutableList().also { items ->
            items[index] = incoming
        }
    } else {
        normalized + incoming
    }
}

private fun ReviewChatTurnStage.defaultProgressTitle(): String = when (this) {
    ReviewChatTurnStage.PREPARE -> "准备问题"
    ReviewChatTurnStage.PARSE_INTENT -> "识别问题意图"
    ReviewChatTurnStage.LOAD_HISTORY -> "读取历史记录"
    ReviewChatTurnStage.RETRIEVE_HISTORY -> "检索历史记录"
    ReviewChatTurnStage.RUN_SKILL -> "运行 Skill"
    ReviewChatTurnStage.BUILD_CONTEXT -> "组织上下文"
    ReviewChatTurnStage.CLOUD_MODEL -> "请求云侧模型"
    ReviewChatTurnStage.ON_DEVICE_CHECK -> "检查端侧模型"
    ReviewChatTurnStage.ON_DEVICE_MODEL -> "准备端侧模型"
    ReviewChatTurnStage.GENERATE -> "生成回答"
    ReviewChatTurnStage.COMPLETE -> "回答完成"
}
