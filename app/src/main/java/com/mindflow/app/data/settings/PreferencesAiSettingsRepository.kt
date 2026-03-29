package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.AiSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiSettingsDataStore by preferencesDataStore(name = "mindflow_ai_settings")

class PreferencesAiSettingsRepository(
    private val context: Context,
    private val defaultSettings: AiSettings,
) : AiSettingsRepository {
    override val settings: Flow<AiSettings> = context.aiSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AiSettings(
                apiKey = preferences[API_KEY] ?: defaultSettings.apiKey,
                baseUrl = preferences[BASE_URL] ?: defaultSettings.baseUrl,
                model = preferences[MODEL] ?: defaultSettings.model,
                aiEnabled = preferences[AI_ENABLED] ?: true,
                lastVerifiedAt = preferences[LAST_VERIFIED_AT] ?: 0L,
                lastVerifiedSuccess = preferences[LAST_VERIFIED_SUCCESS] ?: false,
                lastVerificationMessage = preferences[LAST_VERIFICATION_MESSAGE].orEmpty(),
                verifiedFingerprint = preferences[VERIFIED_FINGERPRINT].orEmpty(),
                usageDayKey = preferences[USAGE_DAY_KEY].orEmpty(),
                requestsToday = preferences[REQUESTS_TODAY] ?: 0,
                successesToday = preferences[SUCCESS_TODAY] ?: 0,
                tokensToday = preferences[TOKENS_TODAY] ?: 0,
            )
        }

    override suspend fun getCurrent(): AiSettings = settings.first()

    override suspend fun save(settings: AiSettings) {
        context.aiSettingsDataStore.edit { preferences ->
            preferences[API_KEY] = settings.apiKey.trim()
            preferences[BASE_URL] = settings.baseUrl.trim().ifBlank { defaultSettings.baseUrl }
            preferences[MODEL] = settings.model.trim()
            preferences[AI_ENABLED] = settings.aiEnabled
        }
    }

    override suspend fun updateVerificationStatus(
        fingerprint: String,
        success: Boolean,
        message: String,
        verifiedAt: Long,
    ) {
        context.aiSettingsDataStore.edit { preferences ->
            preferences[LAST_VERIFIED_AT] = verifiedAt
            preferences[LAST_VERIFIED_SUCCESS] = success
            preferences[LAST_VERIFICATION_MESSAGE] = message.trim()
            preferences[VERIFIED_FINGERPRINT] = fingerprint
        }
    }

    override suspend fun recordUsage(
        requestIncrement: Int,
        successIncrement: Int,
        tokenIncrement: Int,
        dayKey: String,
    ) {
        context.aiSettingsDataStore.edit { preferences ->
            val isSameDay = preferences[USAGE_DAY_KEY].orEmpty() == dayKey
            val requestBase = if (isSameDay) preferences[REQUESTS_TODAY] ?: 0 else 0
            val successBase = if (isSameDay) preferences[SUCCESS_TODAY] ?: 0 else 0
            val tokenBase = if (isSameDay) preferences[TOKENS_TODAY] ?: 0 else 0

            preferences[USAGE_DAY_KEY] = dayKey
            preferences[REQUESTS_TODAY] = requestBase + requestIncrement
            preferences[SUCCESS_TODAY] = successBase + successIncrement
            preferences[TOKENS_TODAY] = tokenBase + tokenIncrement
        }
    }

    override suspend fun clear() {
        context.aiSettingsDataStore.edit { preferences ->
            preferences.remove(API_KEY)
            preferences.remove(BASE_URL)
            preferences.remove(MODEL)
            preferences.remove(AI_ENABLED)
            preferences.remove(LAST_VERIFIED_AT)
            preferences.remove(LAST_VERIFIED_SUCCESS)
            preferences.remove(LAST_VERIFICATION_MESSAGE)
            preferences.remove(VERIFIED_FINGERPRINT)
            preferences.remove(USAGE_DAY_KEY)
            preferences.remove(REQUESTS_TODAY)
            preferences.remove(SUCCESS_TODAY)
            preferences.remove(TOKENS_TODAY)
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val LAST_VERIFIED_AT = longPreferencesKey("last_verified_at")
        val LAST_VERIFIED_SUCCESS = booleanPreferencesKey("last_verified_success")
        val LAST_VERIFICATION_MESSAGE = stringPreferencesKey("last_verification_message")
        val VERIFIED_FINGERPRINT = stringPreferencesKey("verified_fingerprint")
        val USAGE_DAY_KEY = stringPreferencesKey("usage_day_key")
        val REQUESTS_TODAY = intPreferencesKey("requests_today")
        val SUCCESS_TODAY = intPreferencesKey("success_today")
        val TOKENS_TODAY = intPreferencesKey("tokens_today")
    }
}
