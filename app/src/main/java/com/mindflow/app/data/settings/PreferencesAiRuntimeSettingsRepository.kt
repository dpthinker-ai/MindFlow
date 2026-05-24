package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiRuntimeSettings
import com.mindflow.app.data.ai.CloudNotificationMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiRuntimeSettingsDataStore by preferencesDataStore(name = "mindflow_ai_runtime_settings")

class PreferencesAiRuntimeSettingsRepository(
    private val context: Context,
    private val legacyExecutionMode: suspend () -> AiExecutionMode = { AiExecutionMode.AUTOMATIC },
) : AiRuntimeSettingsRepository {
    override val settings: Flow<AiRuntimeSettings> = context.aiRuntimeSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AiRuntimeSettings(
                executionMode = preferences[EXECUTION_MODE]
                    ?.let { raw -> runCatching { AiExecutionMode.valueOf(raw) }.getOrNull() }
                    ?: legacyExecutionMode(),
                cloudAllowedForInteractive = preferences[CLOUD_ALLOWED_FOR_INTERACTIVE] ?: true,
                cloudAllowedForBackground = preferences[CLOUD_ALLOWED_FOR_BACKGROUND] ?: true,
                notifyOnCloudUse = preferences[NOTIFY_ON_CLOUD_USE] ?: true,
                backgroundCloudNotificationMode = CloudNotificationMode.fromRaw(preferences[BACKGROUND_NOTIFICATION_MODE]),
                dailyBackgroundCloudRequestLimit = preferences[DAILY_BACKGROUND_REQUEST_LIMIT] ?: 30,
                dailyBackgroundTokenSoftLimit = preferences[DAILY_BACKGROUND_TOKEN_SOFT_LIMIT] ?: 30_000,
            )
        }

    override suspend fun getCurrent(): AiRuntimeSettings = settings.first()

    override suspend fun save(settings: AiRuntimeSettings) {
        context.aiRuntimeSettingsDataStore.edit { preferences ->
            preferences[EXECUTION_MODE] = settings.executionMode.name
            preferences[CLOUD_ALLOWED_FOR_INTERACTIVE] = settings.cloudAllowedForInteractive
            preferences[CLOUD_ALLOWED_FOR_BACKGROUND] = settings.cloudAllowedForBackground
            preferences[NOTIFY_ON_CLOUD_USE] = settings.notifyOnCloudUse
            preferences[BACKGROUND_NOTIFICATION_MODE] = settings.backgroundCloudNotificationMode.name
            preferences[DAILY_BACKGROUND_REQUEST_LIMIT] = settings.dailyBackgroundCloudRequestLimit
            preferences[DAILY_BACKGROUND_TOKEN_SOFT_LIMIT] = settings.dailyBackgroundTokenSoftLimit
        }
    }

    private companion object {
        val EXECUTION_MODE = stringPreferencesKey("execution_mode")
        val CLOUD_ALLOWED_FOR_INTERACTIVE = booleanPreferencesKey("cloud_allowed_for_interactive")
        val CLOUD_ALLOWED_FOR_BACKGROUND = booleanPreferencesKey("cloud_allowed_for_background")
        val NOTIFY_ON_CLOUD_USE = booleanPreferencesKey("notify_on_cloud_use")
        val BACKGROUND_NOTIFICATION_MODE = stringPreferencesKey("background_notification_mode")
        val DAILY_BACKGROUND_REQUEST_LIMIT = intPreferencesKey("daily_background_request_limit")
        val DAILY_BACKGROUND_TOKEN_SOFT_LIMIT = intPreferencesKey("daily_background_token_soft_limit")
    }
}
