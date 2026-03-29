package com.mindflow.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.MindFolder
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TimeRange
import com.mindflow.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val results: List<NoteEntity> = emptyList(),
    val availableFolders: List<MindFolder> = MindFolderCatalog.all,
    val availableTags: List<String> = emptyList(),
    val folderCounts: Map<String, Int> = emptyMap(),
    val uncategorizedCount: Int = 0,
    val pendingFolderClassificationCount: Int = 0,
    val lastAutoOrganizedAt: Long = 0L,
    val lastAutoOrganizedCount: Int = 0,
)

sealed interface SearchEvent {
    data class Message(val text: String) : SearchEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val noteRepository: NoteRepository,
    private val backgroundFolderOrganizer: BackgroundFolderOrganizer,
    initialStatus: NoteStatus? = null,
    initialArchivedOnly: Boolean = false,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val selectedFolder = MutableStateFlow<String?>(null)
    private val selectedStatus = MutableStateFlow(initialStatus)
    private val selectedTimeRange = MutableStateFlow(TimeRange.ALL)
    private val includeArchived = MutableStateFlow(false)
    private val archivedOnly = MutableStateFlow(initialArchivedOnly)
    private val _events = MutableSharedFlow<SearchEvent>()
    val events = _events.asSharedFlow()

    private val filtersFlow = combine(
        combine(
            query,
            selectedTag,
            selectedFolder,
            selectedStatus,
            selectedTimeRange,
        ) { queryText, tag, folder, status, timeRange ->
            SearchFilters(
                query = queryText,
                tag = tag,
                folderKey = folder,
                status = status,
                timeRange = timeRange,
            )
        },
        archivedOnly,
    ) { baseFilters, archivedOnlyValue ->
        baseFilters.copy(archivedOnly = archivedOnlyValue)
    }.combine(includeArchived) { filters, archived ->
        filters.copy(includeArchived = if (filters.archivedOnly) false else archived)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filtersFlow.flatMapLatest { filters ->
            noteRepository.observeSearchResults(filters).map { results ->
                SearchUiState(filters = filters, results = results)
            }
        },
        noteRepository.observeAllNotes(),
    ) { state, allNotes ->
        state to allNotes
    }.combine(backgroundFolderOrganizer.status) { (state, allNotes), organizerStatus ->
        val activeNotes = allNotes.filter { !it.isArchived }
        val tags = allNotes
            .sortedByDescending { it.updatedAt }
            .flatMap { it.tags }
            .distinct()
        state.copy(
            availableTags = tags,
            folderCounts = MindFolderCatalog.all.associate { folder ->
                folder.key to activeNotes.count { note ->
                    MindFolderCatalog.normalizedKey(note.folderKey) == folder.key
                }
            },
            uncategorizedCount = activeNotes.count {
                MindFolderCatalog.normalizedKey(it.folderKey) == null
            },
            pendingFolderClassificationCount = activeNotes.count {
                MindFolderCatalog.normalizedKey(it.folderKey) == null && it.folderSource != FolderSource.MANUAL
            },
            lastAutoOrganizedAt = organizerStatus.lastOrganizedAt,
            lastAutoOrganizedCount = organizerStatus.lastOrganizedCount,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateTag(tag: String?) {
        selectedTag.value = tag
    }

    fun updateFolder(folderKey: String?) {
        selectedFolder.value = folderKey
    }

    fun updateStatus(status: NoteStatus?) {
        selectedStatus.value = status
    }

    fun updateTimeRange(timeRange: TimeRange) {
        selectedTimeRange.value = timeRange
    }

    fun toggleArchived() {
        if (archivedOnly.value) {
            archivedOnly.value = false
            includeArchived.value = false
        } else {
            includeArchived.update { !it }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(SearchEvent.Message("已删除记录"))
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
            _events.emit(SearchEvent.Message(message))
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            backgroundFolderOrganizer: BackgroundFolderOrganizer,
            initialStatus: NoteStatus? = null,
            initialArchivedOnly: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    noteRepository = noteRepository,
                    backgroundFolderOrganizer = backgroundFolderOrganizer,
                    initialStatus = initialStatus,
                    initialArchivedOnly = initialArchivedOnly,
                )
            }
        }
    }
}
