package com.mindflow.app.data.settings

import com.mindflow.app.data.ai.AiRuntimeSettings
import kotlinx.coroutines.flow.Flow

interface AiRuntimeSettingsRepository {
    val settings: Flow<AiRuntimeSettings>
    suspend fun getCurrent(): AiRuntimeSettings
    suspend fun save(settings: AiRuntimeSettings)
}
