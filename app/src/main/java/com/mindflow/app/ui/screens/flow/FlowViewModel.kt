package com.mindflow.app.ui.screens.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FlowUiState(
    val todayCount: Int = 0,
    val continueNote: NoteEntity? = null,
    val nextActionText: String = "",
    val nextActionSource: DailyBriefSource = DailyBriefSource.RULE,
    val explorationPrompts: List<String> = emptyList(),
    val explorationSource: DailyBriefSource = DailyBriefSource.RULE,
    val weeklyReviewLines: List<String> = emptyList(),
    val weeklyReviewSource: DailyBriefSource = DailyBriefSource.RULE,
    val themeThreads: List<ThemeThread> = emptyList(),
    val fusionSuggestions: List<String> = emptyList(),
    val fusionSource: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowViewModel(
    noteRepository: NoteRepository,
    private val dailyBriefPlanner: DailyBriefPlanner,
    private val nextActionPlanner: NextActionPlanner,
    private val weeklyReviewPlanner: WeeklyReviewPlanner,
    private val fusionSuggestionPlanner: FusionSuggestionPlanner,
) : ViewModel() {
    val uiState: StateFlow<FlowUiState> = combine(
        noteRepository.observeAllNotes(),
        dailyBriefPlanner.state,
        nextActionPlanner.state,
        weeklyReviewPlanner.state,
        fusionSuggestionPlanner.state,
    ) { allNotes, briefState, nextActionState, weeklyReviewState, fusionState ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val activeNotes = allNotes.filter { !it.isArchived }
        val continueNote = pickContinueNote(activeNotes)
        val nextActionText = if (
            continueNote != null &&
            nextActionState.noteId == continueNote.id &&
            nextActionState.noteUpdatedAt == continueNote.updatedAt
        ) {
            nextActionState.text
        } else {
            ""
        }
        FlowUiState(
            todayCount = activeNotes.count { it.createdAt.toLocalDate(zoneId) == today },
            continueNote = continueNote,
            nextActionText = nextActionText,
            nextActionSource = nextActionState.source,
            explorationPrompts = briefState.lines,
            explorationSource = briefState.source,
            weeklyReviewLines = weeklyReviewState.lines,
            weeklyReviewSource = weeklyReviewState.source,
            themeThreads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes),
            fusionSuggestions = fusionState.lines,
            fusionSource = fusionState.source,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlowUiState())

    init {
        viewModelScope.launch {
            noteRepository.observeAllNotes().collect { notes ->
                val activeNotes = notes.filter { !it.isArchived }
                dailyBriefPlanner.refreshIfNeeded(notes)
                nextActionPlanner.refreshIfNeeded(pickContinueNote(activeNotes))
                weeklyReviewPlanner.refreshIfNeeded(notes)
                fusionSuggestionPlanner.refreshIfNeeded(notes)
            }
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            dailyBriefPlanner: DailyBriefPlanner,
            nextActionPlanner: NextActionPlanner,
            weeklyReviewPlanner: WeeklyReviewPlanner,
            fusionSuggestionPlanner: FusionSuggestionPlanner,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FlowViewModel(
                    noteRepository = noteRepository,
                    dailyBriefPlanner = dailyBriefPlanner,
                    nextActionPlanner = nextActionPlanner,
                    weeklyReviewPlanner = weeklyReviewPlanner,
                    fusionSuggestionPlanner = fusionSuggestionPlanner,
                )
            }
        }
    }
}

private fun pickContinueNote(notes: List<NoteEntity>): NoteEntity? =
    notes
        .filter { it.status == NoteStatus.IN_PROGRESS }
        .maxByOrNull { it.updatedAt }
        ?: notes
            .filter { it.status == NoteStatus.IDEA }
            .maxByOrNull { it.updatedAt }

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
