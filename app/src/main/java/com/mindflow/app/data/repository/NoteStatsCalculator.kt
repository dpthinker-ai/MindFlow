package com.mindflow.app.data.repository

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.ActivityDaySummary
import com.mindflow.app.data.model.ActivityMonthSummary
import com.mindflow.app.data.model.ActivityYearSummary
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.model.NoteStatus
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.time.temporal.WeekFields

object NoteStatsCalculator {
    fun calculate(
        notes: List<NoteEntity>,
        history: List<NoteStatusHistoryEntity>,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): NoteStats {
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val weekFields = WeekFields.of(locale)
        val currentWeek = today.get(weekFields.weekOfWeekBasedYear())
        val currentWeekYear = today.get(weekFields.weekBasedYear())

        val activityByDate = notes
            .groupingBy { Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate() }
            .eachCount()
            .toSortedMap()

        val activityDays = activityByDate.map { (date, count) ->
            ActivityDaySummary(date = date, count = count)
        }
        val yearlyActivity = activityByDate.entries
            .groupBy({ it.key.year }, { it.value })
            .map { (year, counts) ->
                ActivityYearSummary(
                    year = year,
                    totalCount = counts.sum(),
                    activeDays = counts.size,
                    peakCount = counts.maxOrNull() ?: 0,
                )
            }
            .sortedByDescending { it.year }
        val monthlyActivity = activityByDate.entries
            .groupBy({ it.key.year to it.key.monthValue }, { it.value })
            .map { (yearMonth, counts) ->
                ActivityMonthSummary(
                    year = yearMonth.first,
                    month = yearMonth.second,
                    totalCount = counts.sum(),
                    activeDays = counts.size,
                )
            }
            .sortedWith(compareByDescending<ActivityMonthSummary> { it.year }.thenByDescending { it.month })

        return NoteStats(
            totalNotes = notes.size,
            activeNotes = notes.count { !it.isArchived },
            archivedNotes = notes.count { it.isArchived },
            ideaCount = notes.count { it.status == NoteStatus.IDEA },
            inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = notes.count { it.status == NoteStatus.DONE },
            notesCreatedLast7Days = notes.count { it.createdAt >= sevenDaysAgo },
            notesUpdatedLast30Days = notes.count { it.updatedAt >= thirtyDaysAgo },
            latestDoneAt = history
                .filter { it.toStatus == NoteStatus.DONE }
                .maxOfOrNull { it.changedAt },
            todayCount = activityByDate[today] ?: 0,
            currentWeekCount = activityByDate.entries.sumOf { (date, count) ->
                if (
                    date.get(weekFields.weekBasedYear()) == currentWeekYear &&
                    date.get(weekFields.weekOfWeekBasedYear()) == currentWeek
                ) {
                    count
                } else {
                    0
                }
            },
            currentMonthCount = activityByDate.entries.sumOf { (date, count) ->
                if (date.year == today.year && date.monthValue == today.monthValue) count else 0
            },
            currentYearCount = activityByDate.entries.sumOf { (date, count) ->
                if (date.year == today.year) count else 0
            },
            activityDays = activityDays,
            yearlyActivity = yearlyActivity,
            monthlyActivity = monthlyActivity,
        )
    }
}
