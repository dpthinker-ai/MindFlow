package com.mindflow.app.data.model

import java.time.LocalDate

data class ActivityYearSummary(
    val year: Int,
    val totalCount: Int,
    val activeDays: Int,
    val peakCount: Int,
)

data class ActivityMonthSummary(
    val year: Int,
    val month: Int,
    val totalCount: Int,
    val activeDays: Int,
)

data class ActivityDaySummary(
    val date: LocalDate,
    val count: Int,
)
