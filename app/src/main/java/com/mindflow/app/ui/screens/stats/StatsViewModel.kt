package com.mindflow.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val stats: NoteStats = NoteStats(),
    val notes: List<NoteEntity> = emptyList(),
)

sealed interface StatsEvent {
    data class Message(val text: String) : StatsEvent
}

class StatsViewModel(
    private val noteRepository: NoteRepository,
) : ViewModel() {
    private val _events = MutableSharedFlow<StatsEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<StatsUiState> = noteRepository.observeNoteStats()
        .combine(noteRepository.observeAllNotes()) { stats, notes ->
            StatsUiState(stats = stats, notes = notes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(StatsEvent.Message("已删除记录"))
        }
    }

    companion object {
        fun factory(noteRepository: NoteRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(noteRepository) }
        }
    }
}
