package com.mindflow.app.data.settings

import com.mindflow.app.data.model.AiSettings
import kotlinx.coroutines.flow.Flow

interface AiSettingsRepository {
    val settings: Flow<AiSettings>

    suspend fun getCurrent(): AiSettings
    suspend fun getProviderSettings(providerId: String): AiSettings
    suspend fun save(settings: AiSettings)
    suspend fun updateVerificationStatus(
        providerId: String,
        fingerprint: String,
        success: Boolean,
        message: String,
        verifiedAt: Long = System.currentTimeMillis(),
    )
    suspend fun recordUsage(
        requestIncrement: Int = 0,
        successIncrement: Int = 0,
        tokenIncrement: Int = 0,
        dayKey: String,
    )
    suspend fun clearProvider(providerId: String)
    suspend fun clear()
}
