package com.mindflow.app.ui.screens.reviewchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnResult
import com.mindflow.app.ui.navigation.ReviewChatSeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewChatUiState(
    val title: String = "和历史聊聊",
    val messages: List<ReviewChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val providerLine: String = "",
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
    private val answerTurn: suspend (ReviewChatTurnRequest) -> ReviewChatTurnResult,
    private val savedConversationRepository: ReviewChatSavedConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewChatUiState())
    val uiState: StateFlow<ReviewChatUiState> = _uiState

    private var pendingQuestion: String? = null
    private var pendingPriorMessages: List<ReviewChatMessage> = emptyList()

    init {
        when {
            seed.savedSessionId != null -> loadSavedSession(seed.savedSessionId)
            seed.initialQuestion.isNotBlank() -> submitQuestion(
                question = seed.initialQuestion.trim(),
                appendUserMessage = true,
            )
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
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
                    errorMessage = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isSending = true,
                    errorMessage = null,
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                answerTurn(
                    ReviewChatTurnRequest(
                        sessionId = seed.requestId.toString(),
                        question = question,
                        priorMessages = priorMessages,
                    ),
                )
            }.onSuccess { result ->
                pendingQuestion = null
                pendingPriorMessages = emptyList()
                _uiState.update { state ->
                    state.copy(
                        title = state.title.takeIf { it != "和历史聊聊" } ?: result.titleSuggestion.ifBlank { state.title },
                        messages = state.messages + ReviewChatMessage(
                            role = ReviewChatMessageRole.ASSISTANT,
                            content = result.answer,
                            provider = result.provider,
                            createdAt = System.currentTimeMillis(),
                            referencedNoteId = result.referencedNoteId,
                        ),
                        isSending = false,
                        providerLine = result.providerLine,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "生成回答失败，请重试。",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            seed: ReviewChatSeed,
            answerTurn: suspend (ReviewChatTurnRequest) -> ReviewChatTurnResult,
            savedConversationRepository: ReviewChatSavedConversationRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReviewChatViewModel(
                    seed = seed,
                    answerTurn = answerTurn,
                    savedConversationRepository = savedConversationRepository,
                )
            }
        }
    }
}
