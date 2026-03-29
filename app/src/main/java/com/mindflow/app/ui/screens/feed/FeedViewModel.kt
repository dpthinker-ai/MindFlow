package com.mindflow.app.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
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
    val folderCounts: Map<String, Int> = emptyMap(),
    val uncategorizedCount: Int = 0,
    val pendingFolderClassificationCount: Int = 0,
    val lastAutoOrganizedAt: Long = 0L,
    val lastAutoOrganizedCount: Int = 0,
)

sealed interface FeedEvent {
    data class Message(val text: String) : FeedEvent
}

class FeedViewModel(
    private val noteRepository: NoteRepository,
    private val backgroundFolderOrganizer: BackgroundFolderOrganizer,
) : ViewModel() {
    val uiState: StateFlow<FeedUiState> = combine(
        noteRepository.observeFeed(),
        noteRepository.observeAllNotes(),
        backgroundFolderOrganizer.status,
    ) { feedNotes, allNotes, organizerStatus ->
        FeedUiState(
            notes = feedNotes,
            totalCount = allNotes.size,
            ideaCount = allNotes.count { it.status == NoteStatus.IDEA },
            inProgressCount = allNotes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = allNotes.count { it.status == NoteStatus.DONE },
            archivedCount = allNotes.count { it.isArchived },
            folderCounts = MindFolderCatalog.all.associate { folder ->
                folder.key to feedNotes.count { note -> note.folderKey == folder.key }
            },
            uncategorizedCount = feedNotes.count { it.folderKey == null },
            pendingFolderClassificationCount = allNotes.count {
                !it.isArchived && it.folderKey == null && it.folderSource != FolderSource.MANUAL
            },
            lastAutoOrganizedAt = organizerStatus.lastOrganizedAt,
            lastAutoOrganizedCount = organizerStatus.lastOrganizedCount,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

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

    fun classifyPendingFolders() {
        viewModelScope.launch {
            val classifiedCount = backgroundFolderOrganizer.organizeNow()
            val message = if (classifiedCount > 0) {
                "已自动归类 $classifiedCount 条记录"
            } else {
                "没有可整理的未分类记录"
            }
            _events.emit(FeedEvent.Message(message))
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            backgroundFolderOrganizer: BackgroundFolderOrganizer,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FeedViewModel(noteRepository, backgroundFolderOrganizer) }
        }
    }
}
