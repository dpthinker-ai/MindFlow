package com.mindflow.app.data.model

import java.time.LocalDate

data class NoteStats(
    val totalNotes: Int = 0,
    val activeNotes: Int = 0,
    val archivedNotes: Int = 0,
    val ideaCount: Int = 0,
    val inProgressCount: Int = 0,
    val doneCount: Int = 0,
    val notesCreatedLast7Days: Int = 0,
    val notesUpdatedLast30Days: Int = 0,
    val latestDoneAt: Long? = null,
    val todayCount: Int = 0,
    val currentWeekCount: Int = 0,
    val currentMonthCount: Int = 0,
    val currentYearCount: Int = 0,
    val activityDays: List<ActivityDaySummary> = emptyList(),
    val yearlyActivity: List<ActivityYearSummary> = emptyList(),
    val monthlyActivity: List<ActivityMonthSummary> = emptyList(),
) {
    val completionRate: Int
        get() = if (totalNotes == 0) 0 else ((doneCount.toFloat() / totalNotes) * 100).toInt()

    val availableYears: List<Int>
        get() = yearlyActivity.map { it.year }

    fun yearSummary(year: Int): ActivityYearSummary? = yearlyActivity.firstOrNull { it.year == year }

    fun monthSummary(year: Int, month: Int): ActivityMonthSummary? =
        monthlyActivity.firstOrNull { it.year == year && it.month == month }

    fun activityCountOn(date: LocalDate): Int = activityDays.firstOrNull { it.date == date }?.count ?: 0
}
