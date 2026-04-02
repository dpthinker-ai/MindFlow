package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DirectionStageHistoryEntry(
    val label: String,
    val stage: DirectionStage,
)

object DirectionStageHistoryAnalyzer {
    fun build(
        notes: List<NoteEntity>,
        weeks: Int = 4,
    ): List<DirectionStageHistoryEntry> {
        if (notes.isEmpty()) return emptyList()

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val currentWeekStart = today.with(DayOfWeek.MONDAY)

        return (0 until weeks)
            .mapNotNull { index ->
                val weekStart = currentWeekStart.minusWeeks(index.toLong())
                val weekEnd = weekStart.plusDays(6)
                val lookbackStart = weekStart.minusDays(35)
                val visibleNotes = notes.filter { note ->
                    val noteDate = Instant.ofEpochMilli(note.updatedAt).atZone(zoneId).toLocalDate()
                    !noteDate.isBefore(lookbackStart) && !noteDate.isAfter(weekEnd)
                }
                if (visibleNotes.isEmpty()) {
                    null
                } else {
                    val focusNote = pickContinueNote(visibleNotes)
                    val rhythm = DirectionRhythmAnalyzer.analyze(
                        notes = visibleNotes,
                        focusNote = focusNote,
                        hasResearch = visibleNotes.any(ThreadResearchAnalyzer::isResearchMemoryNote),
                    )
                    DirectionStageHistoryEntry(
                        label = when (index) {
                            0 -> "本周"
                            1 -> "上周"
                            else -> "${index}周前"
                        },
                        stage = rhythm.stage,
                    )
                }
            }
            .reversed()
    }
}
