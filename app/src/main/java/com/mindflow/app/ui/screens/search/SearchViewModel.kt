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
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
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
    val knowledgeResults: List<KnowledgeLayerSearchItem> = emptyList(),
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
    private val directionWikiCoordinator: DirectionWikiCoordinator,
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
        directionWikiCoordinator.snapshot,
    ) { state, allNotes, wikiSnapshot ->
        Triple(state, allNotes, wikiSnapshot)
    }.combine(backgroundFolderOrganizer.status) { (state, allNotes, wikiSnapshot), organizerStatus ->
        val activeNotes = allNotes.filter { !it.isArchived }
        val tags = allNotes
            .sortedByDescending { it.updatedAt }
            .flatMap { it.tags }
            .distinct()
        val knowledgeResults = if (state.filters.query.isBlank()) {
            emptyList()
        } else {
            val queryText = state.filters.query.trim().lowercase()
            wikiSnapshot.knowledgeItems
                .mapNotNull { item ->
                    val score = knowledgeSearchScore(item, queryText, state.filters.query.trim())
                    if (score <= 0) null else item to score
                }
                .sortedWith(
                    compareByDescending<Pair<KnowledgeLayerSearchItem, Int>> { it.second }
                        .thenByDescending { trustWeight(it.first.trustLabel) }
                        .thenByDescending { it.first.updatedAt },
                )
                .map { it.first }
                .take(8)
        }
        state.copy(
            knowledgeResults = knowledgeResults,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private fun knowledgeSearchScore(
        item: KnowledgeLayerSearchItem,
        queryText: String,
        rawQuery: String,
    ): Int {
        var score = 0
        if (item.title.lowercase().contains(queryText)) score += 60
        if (item.summary.lowercase().contains(queryText)) score += 28
        if (item.supportLine.lowercase().contains(queryText)) score += 18
        if (item.trustLabel.lowercase().contains(queryText)) score += 24
        if (item.type.label.contains(rawQuery)) score += 16
        score += trustWeight(item.trustLabel) * 2
        return score
    }

    private fun trustWeight(label: String): Int = when (label) {
        ResearchEvidenceType.VALIDATED.label -> 4
        ResearchEvidenceType.VERIFIED.label -> 3
        ResearchEvidenceType.HYPOTHESIS.label -> 2
        ResearchEvidenceType.SIGNAL.label -> 1
        else -> 0
    }

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
            directionWikiCoordinator: DirectionWikiCoordinator,
            initialStatus: NoteStatus? = null,
            initialArchivedOnly: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    noteRepository = noteRepository,
                    backgroundFolderOrganizer = backgroundFolderOrganizer,
                    directionWikiCoordinator = directionWikiCoordinator,
                    initialStatus = initialStatus,
                    initialArchivedOnly = initialArchivedOnly,
                )
            }
        }
    }
}
