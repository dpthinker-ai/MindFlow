package com.mindflow.app.data.settings

import com.mindflow.app.data.model.AppearanceSettings
import kotlinx.coroutines.flow.Flow

interface AppearanceSettingsRepository {
    val settings: Flow<AppearanceSettings>

    suspend fun getCurrent(): AppearanceSettings
    suspend fun save(settings: AppearanceSettings)
    suspend fun clear()
}
