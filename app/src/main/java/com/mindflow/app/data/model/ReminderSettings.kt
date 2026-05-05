package com.mindflow.app.data.model

data class ReminderSettings(
    val autoTaskRecognitionEnabled: Boolean = true,
    val articleAutoSummaryEnabled: Boolean = true,
    val morningBriefEnabled: Boolean = false,
    val eveningReviewEnabled: Boolean = false,
    val morningHour: Int = DEFAULT_MORNING_HOUR,
    val morningMinute: Int = DEFAULT_MORNING_MINUTE,
    val eveningHour: Int = DEFAULT_EVENING_HOUR,
    val eveningMinute: Int = DEFAULT_EVENING_MINUTE,
) {
    val hasAnyEnabled: Boolean
        get() = autoTaskRecognitionEnabled ||
            articleAutoSummaryEnabled ||
            morningBriefEnabled ||
            eveningReviewEnabled

    companion object {
        const val DEFAULT_MORNING_HOUR = 8
        const val DEFAULT_MORNING_MINUTE = 30
        const val DEFAULT_EVENING_HOUR = 21
        const val DEFAULT_EVENING_MINUTE = 30
    }
}
