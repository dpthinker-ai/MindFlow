package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.repository.NoteStatsCalculator
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class NoteStatsCalculatorTest {
    @Test
    fun `calculate returns status counts and recent activity`() {
        val now = 1_000_000L
        val notes = listOf(
            NoteEntity(
                id = 1,
                content = "A",
                topic = "A",
                topicSource = TopicSource.MANUAL,
                status = NoteStatus.IDEA,
                isArchived = false,
                createdAt = now - 1_000L,
                updatedAt = now - 1_000L,
            ),
            NoteEntity(
                id = 2,
                content = "B",
                topic = "B",
                topicSource = TopicSource.MANUAL,
                status = NoteStatus.DONE,
                isArchived = true,
                createdAt = now - 10_000L,
                updatedAt = now - 2_000L,
            ),
        )
        val history = listOf(
            NoteStatusHistoryEntity(
                noteId = 2,
                fromStatus = NoteStatus.IN_PROGRESS,
                toStatus = NoteStatus.DONE,
                changedAt = now - 2_000L,
            )
        )

        val stats = NoteStatsCalculator.calculate(notes, history, now = now)

        assertThat(stats.totalNotes).isEqualTo(2)
        assertThat(stats.activeNotes).isEqualTo(1)
        assertThat(stats.archivedNotes).isEqualTo(1)
        assertThat(stats.ideaCount).isEqualTo(1)
        assertThat(stats.doneCount).isEqualTo(1)
        assertThat(stats.latestDoneAt).isEqualTo(now - 2_000L)
    }

    @Test
    fun `calculate builds daily weekly monthly and yearly activity summaries`() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 3, 28, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val notes = listOf(
            note(id = 1, createdAt = epochMillis(2026, 3, 28, zoneId)),
            note(id = 2, createdAt = epochMillis(2026, 3, 28, zoneId)),
            note(id = 3, createdAt = epochMillis(2026, 3, 25, zoneId)),
            note(id = 4, createdAt = epochMillis(2026, 3, 1, zoneId)),
            note(id = 5, createdAt = epochMillis(2026, 1, 10, zoneId)),
            note(id = 6, createdAt = epochMillis(2025, 12, 30, zoneId)),
        )

        val stats = NoteStatsCalculator.calculate(
            notes = notes,
            history = emptyList(),
            now = now,
            zoneId = zoneId,
            locale = Locale.CHINA,
        )

        assertThat(stats.todayCount).isEqualTo(2)
        assertThat(stats.currentWeekCount).isEqualTo(3)
        assertThat(stats.currentMonthCount).isEqualTo(4)
        assertThat(stats.currentYearCount).isEqualTo(5)
        assertThat(stats.activityCountOn(java.time.LocalDate.of(2026, 3, 28))).isEqualTo(2)
        assertThat(stats.availableYears).containsExactly(2026, 2025).inOrder()
        assertThat(stats.yearSummary(2026)?.totalCount).isEqualTo(5)
        assertThat(stats.yearSummary(2026)?.activeDays).isEqualTo(4)
        assertThat(stats.yearSummary(2026)?.peakCount).isEqualTo(2)
        assertThat(stats.monthSummary(2026, 3)?.totalCount).isEqualTo(4)
        assertThat(stats.monthSummary(2025, 12)?.totalCount).isEqualTo(1)
    }

    private fun note(
        id: Long,
        createdAt: Long,
    ) = NoteEntity(
        id = id,
        content = "note-$id",
        topic = "topic-$id",
        topicSource = TopicSource.MANUAL,
        status = NoteStatus.IDEA,
        isArchived = false,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        zoneId: ZoneId,
    ): Long = ZonedDateTime.of(year, month, day, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
}
