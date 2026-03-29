package com.mindflow.app.data.settings

import com.mindflow.app.data.model.ReminderSettings
import kotlinx.coroutines.flow.Flow

interface ReminderSettingsRepository {
    val settings: Flow<ReminderSettings>

    suspend fun getCurrent(): ReminderSettings
    suspend fun save(settings: ReminderSettings)
    suspend fun clear()
}
