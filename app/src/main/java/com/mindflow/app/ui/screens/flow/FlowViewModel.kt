package com.mindflow.app.ui.screens.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.action.NextActionState
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.brief.DailyBriefState
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.FusionSuggestionState
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.ThreadPreferences
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.review.items
import com.mindflow.app.data.review.statsLine
import com.mindflow.app.data.settings.ThreadPreferencesRepository
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
    val staleNote: NoteEntity? = null,
    val staleReason: String = "",
    val staleBridge: String = "",
    val staleNextStep: String = "",
    val explorationPrompts: List<String> = emptyList(),
    val explorationSource: DailyBriefSource = DailyBriefSource.RULE,
    val weeklyReviewItems: List<WeeklyReviewItem> = emptyList(),
    val weeklyReviewSource: DailyBriefSource = DailyBriefSource.RULE,
    val weeklyReviewStatsLine: String = "",
    val followedThreads: List<ThemeThread> = emptyList(),
    val themeThreads: List<ThemeThread> = emptyList(),
    val fusionSuggestions: List<String> = emptyList(),
    val fusionSource: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowViewModel(
    noteRepository: NoteRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    private val dailyBriefPlanner: DailyBriefPlanner,
    private val nextActionPlanner: NextActionPlanner,
    private val weeklyReviewPlanner: WeeklyReviewPlanner,
    private val fusionSuggestionPlanner: FusionSuggestionPlanner,
) : ViewModel() {
    private data class FlowPrimaryInputs(
        val todayCount: Int,
        val continueNote: NoteEntity?,
        val nextActionText: String,
        val nextActionSource: DailyBriefSource,
        val staleNote: NoteEntity?,
        val staleReason: String,
        val staleBridge: String,
        val staleNextStep: String,
        val explorationPrompts: List<String>,
        val explorationSource: DailyBriefSource,
        val followedThreads: List<ThemeThread>,
        val themeThreads: List<ThemeThread>,
    )

    private val primaryInputs = combine(
        noteRepository.observeAllNotes(),
        threadPreferencesRepository.settings,
        dailyBriefPlanner.state,
        nextActionPlanner.state,
    ) { allNotes: List<NoteEntity>,
        threadPreferences: ThreadPreferences,
        briefState: DailyBriefState,
        nextActionState: NextActionState ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val activeNotes = allNotes.filter { !it.isArchived }
        val analyzedThreads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes, limit = 6)
        val followedThreads = threadPreferences.followedThreadKeys
            .map { threadKey -> NoteConnectionAnalyzer.threadFromKey(threadKey, activeNotes) }
            .sortedWith(compareByDescending<ThemeThread> { it.noteCount }.thenBy { it.title })
        val continueNote = pickContinueNote(activeNotes)
        val staleNote = pickStaleNote(activeNotes, continueNote?.id)
        val nextActionText = if (
            continueNote != null &&
            nextActionState.noteId == continueNote.id &&
            nextActionState.noteUpdatedAt == continueNote.updatedAt
        ) {
            nextActionState.text
        } else {
            ""
        }
        FlowPrimaryInputs(
            todayCount = activeNotes.count { it.createdAt.toLocalDate(zoneId) == today },
            continueNote = continueNote,
            nextActionText = nextActionText,
            nextActionSource = nextActionState.source,
            staleNote = staleNote,
            staleReason = staleReasonFor(staleNote),
            staleBridge = staleBridgeFor(
                note = staleNote,
                continueNote = continueNote,
                notes = activeNotes,
            ),
            staleNextStep = staleNextStepFor(staleNote),
            explorationPrompts = briefState.lines,
            explorationSource = briefState.source,
            followedThreads = followedThreads,
            themeThreads = analyzedThreads.filterNot { candidate ->
                followedThreads.any { it.key == candidate.key }
            },
        )
    }

    val uiState: StateFlow<FlowUiState> = combine(
        primaryInputs,
        weeklyReviewPlanner.state,
        fusionSuggestionPlanner.state,
    ) { primary,
        weeklyReviewState: WeeklyReviewState,
        fusionState: FusionSuggestionState ->
        FlowUiState(
            todayCount = primary.todayCount,
            continueNote = primary.continueNote,
            nextActionText = primary.nextActionText,
            nextActionSource = primary.nextActionSource,
            staleNote = primary.staleNote,
            staleReason = primary.staleReason,
            staleBridge = primary.staleBridge,
            staleNextStep = primary.staleNextStep,
            explorationPrompts = primary.explorationPrompts,
            explorationSource = primary.explorationSource,
            weeklyReviewItems = weeklyReviewState.items,
            weeklyReviewSource = weeklyReviewState.source,
            weeklyReviewStatsLine = weeklyReviewState.statsLine,
            followedThreads = primary.followedThreads,
            themeThreads = primary.themeThreads,
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
            threadPreferencesRepository: ThreadPreferencesRepository,
            dailyBriefPlanner: DailyBriefPlanner,
            nextActionPlanner: NextActionPlanner,
            weeklyReviewPlanner: WeeklyReviewPlanner,
            fusionSuggestionPlanner: FusionSuggestionPlanner,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FlowViewModel(
                    noteRepository = noteRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
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

private fun pickStaleNote(
    notes: List<NoteEntity>,
    excludeNoteId: Long?,
): NoteEntity? {
    val threshold = System.currentTimeMillis() - 12L * 24 * 60 * 60 * 1_000
    return notes
        .filter { it.id != excludeNoteId }
        .filter { it.status != NoteStatus.DONE }
        .filter { it.updatedAt < threshold }
        .minByOrNull { it.updatedAt }
}

private fun staleReasonFor(note: NoteEntity?): String =
    when (note?.status) {
        NoteStatus.IN_PROGRESS -> "这条方向已经晾了一阵，现在最适合重新接上一小步。"
        NoteStatus.IDEA -> "这条想法沉下去有点久了，值得重新补一条更具体的记录。"
        else -> ""
    }

private fun staleBridgeFor(
    note: NoteEntity?,
    continueNote: NoteEntity?,
    notes: List<NoteEntity>,
): String {
    note ?: return ""
    val sharedTag = continueNote
        ?.tags
        ?.firstOrNull { candidate -> candidate.isNotBlank() && candidate in note.tags }
    if (sharedTag != null) {
        return "它和你正在推进的内容都围绕「$sharedTag」，适合顺手接回同一条主线。"
    }

    val noteFolder = note.folderKey
    val continueFolder = continueNote?.folderKey
    if (!noteFolder.isNullOrBlank() && noteFolder == continueFolder) {
        val folderName = note.folderName()
        return if (folderName != null) {
            "它和你最近在推的内容都在「$folderName」里，说明这不是一次性念头。"
        } else {
            "它和你最近推进的内容在同一类问题里，值得重新接上。"
        }
    }

    val repeatedTag = note.tags
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                notes.count { active -> !active.isArchived && candidate in active.tags } >= 2
        }
    if (repeatedTag != null) {
        return "它属于「#$repeatedTag」这条持续出现的方向，可以重新补一笔，把线索接回来。"
    }

    return note.folderName()?.let { folderName ->
        "它还落在「$folderName」这类问题里，说明这个方向仍然值得保留。"
    }.orEmpty()
}

private fun staleNextStepFor(note: NoteEntity?): String =
    when {
        note == null -> ""
        note.status == NoteStatus.IN_PROGRESS -> "先补一句最新进展，再把它往前拱一小步。"
        note.folderName() == "工作" -> "先把它压成一个最想验证的问题，再补一条更具体的记录。"
        note.folderName() == "项目" -> "先写下一个最小可验证版本，别直接摊开完整方案。"
        note.folderName() == "健康" -> "先把它变成今天就能执行的一次小动作，再看后续反馈。"
        else -> "先补一条更具体的记录，把这个想法重新压回到可推进的状态。"
    }

private fun NoteEntity.folderName(): String? =
    com.mindflow.app.data.model.MindFolderCatalog.fromKey(folderKey)?.name

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
