package com.mindflow.app.data.settings

import com.mindflow.app.data.model.TimeBankSettings
import kotlinx.coroutines.flow.Flow

interface TimeBankSettingsRepository {
    val settings: Flow<TimeBankSettings>

    suspend fun getCurrent(): TimeBankSettings
    suspend fun save(settings: TimeBankSettings)
    suspend fun clear()
}
