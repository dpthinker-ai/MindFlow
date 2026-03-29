package com.mindflow.app.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mindflow.app.data.model.ReminderSettings
import com.mindflow.app.data.settings.ReminderSettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReminderScheduler(
    private val context: Context,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    fun syncInBackground() {
        applicationScope.launch {
            syncNow()
        }
    }

    suspend fun syncNow() {
        syncNow(reminderSettingsRepository.getCurrent())
    }

    suspend fun syncNow(settings: ReminderSettings) {
        if (settings.morningBriefEnabled) {
            scheduleDaily(
                kind = ReminderKind.MORNING,
                hour = settings.morningHour,
                minute = settings.morningMinute,
            )
        } else {
            cancel(ReminderKind.MORNING)
        }

        if (settings.eveningReviewEnabled) {
            scheduleDaily(
                kind = ReminderKind.EVENING,
                hour = settings.eveningHour,
                minute = settings.eveningMinute,
            )
        } else {
            cancel(ReminderKind.EVENING)
        }
    }

    private fun scheduleDaily(
        kind: ReminderKind,
        hour: Int,
        minute: Int,
    ) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            kind.uniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel(kind: ReminderKind) {
        WorkManager.getInstance(context).cancelUniqueWork(kind.uniqueWorkName)
    }

    private fun initialDelayMillis(
        hour: Int,
        minute: Int,
    ): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(1_000L)
    }
}

enum class ReminderKind(
    val uniqueWorkName: String,
) {
    MORNING("mindflow_morning_brief"),
    EVENING("mindflow_evening_review"),
}
