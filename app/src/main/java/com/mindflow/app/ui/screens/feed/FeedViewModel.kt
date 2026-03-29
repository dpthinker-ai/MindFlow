package com.mindflow.app.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val notes: List<NoteEntity> = emptyList(),
    val totalCount: Int = 0,
    val ideaCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
    val archivedCount: Int = 0,
)

sealed interface FeedEvent {
    data class Message(val text: String) : FeedEvent
}

class FeedViewModel(
    private val noteRepository: NoteRepository,
) : ViewModel() {
    val uiState: StateFlow<FeedUiState> = combine(
        noteRepository.observeFeed(),
        noteRepository.observeAllNotes(),
    ) { feedNotes, allNotes ->
        FeedUiState(
            notes = feedNotes,
            totalCount = allNotes.size,
            ideaCount = allNotes.count { it.status == NoteStatus.IDEA },
            inProgressCount = allNotes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = allNotes.count { it.status == NoteStatus.DONE },
            archivedCount = allNotes.count { it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    private val _events = MutableSharedFlow<FeedEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            noteRepository.observeSystemNotices().collect { message ->
                _events.emit(FeedEvent.Message(message))
            }
        }
    }

    fun archiveNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.setArchived(noteId, archived = true)
            _events.emit(FeedEvent.Message("已归档"))
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(FeedEvent.Message("已删除记录"))
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FeedViewModel(noteRepository) }
        }
    }
}
