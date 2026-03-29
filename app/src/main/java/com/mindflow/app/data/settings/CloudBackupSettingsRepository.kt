package com.mindflow.app.data.settings

import com.mindflow.app.data.model.CloudBackupSettings
import kotlinx.coroutines.flow.Flow

interface CloudBackupSettingsRepository {
    val settings: Flow<CloudBackupSettings>

    suspend fun getCurrent(): CloudBackupSettings
    suspend fun save(settings: CloudBackupSettings)
    suspend fun clear()
    suspend fun updateBackupStatus(
        succeededAt: Long? = null,
        errorMessage: String = "",
    )
}
