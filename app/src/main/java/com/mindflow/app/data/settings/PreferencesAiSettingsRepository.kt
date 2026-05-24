package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.ai.cloud.CloudAiProviderRegistry
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
        .map { preferences -> preferences.activeSettings() }

    override suspend fun getCurrent(): AiSettings = settings.first()

    override suspend fun getProviderSettings(providerId: String): AiSettings =
        context.aiSettingsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences.providerSettings(
                    providerId = normalizedProviderId(providerId),
                    activeFallback = preferences.activeSettings(),
                )
            }
            .first()

    override suspend fun save(settings: AiSettings) {
        context.aiSettingsDataStore.edit { preferences ->
            val providerId = normalizedProviderId(
                settings.providerId.trim().ifBlank {
                    CloudAiProviderRegistry.resolveProviderId(settings.baseUrl)
                }
            )
            val provider = CloudAiProviderRegistry.require(providerId)
            val baseUrl = settings.baseUrl.trim().ifBlank {
                provider.baseUrl.ifBlank { defaultSettings.baseUrl }
            }
            val model = settings.model.trim().ifBlank { provider.defaultModel }
            val apiKey = settings.apiKey.trim()

            preferences[PROVIDER_ID] = providerId
            preferences[API_KEY] = apiKey
            preferences[BASE_URL] = baseUrl
            preferences[MODEL] = model
            preferences[AI_ENABLED] = settings.aiEnabled

            preferences[providerApiKeyKey(providerId)] = apiKey
            preferences[providerBaseUrlKey(providerId)] = baseUrl
            preferences[providerModelKey(providerId)] = model
            preferences[providerAiEnabledKey(providerId)] = settings.aiEnabled
        }
    }

    override suspend fun updateVerificationStatus(
        providerId: String,
        fingerprint: String,
        success: Boolean,
        message: String,
        verifiedAt: Long,
    ) {
        context.aiSettingsDataStore.edit { preferences ->
            val normalized = normalizedProviderId(providerId)
            if (preferences.activeProviderId() == normalized) {
                preferences[LAST_VERIFIED_AT] = verifiedAt
                preferences[LAST_VERIFIED_SUCCESS] = success
                preferences[LAST_VERIFICATION_MESSAGE] = message.trim()
                preferences[VERIFIED_FINGERPRINT] = fingerprint
            }
            preferences[providerLastVerifiedAtKey(normalized)] = verifiedAt
            preferences[providerLastVerifiedSuccessKey(normalized)] = success
            preferences[providerLastVerificationMessageKey(normalized)] = message.trim()
            preferences[providerVerifiedFingerprintKey(normalized)] = fingerprint
        }
    }

    override suspend fun recordUsage(
        requestIncrement: Int,
        successIncrement: Int,
        tokenIncrement: Int,
        dayKey: String,
    ) {
        context.aiSettingsDataStore.edit { preferences ->
            val providerId = preferences.activeProviderId()
            val isSameDay = preferences[USAGE_DAY_KEY].orEmpty() == dayKey
            val requestBase = if (isSameDay) preferences[REQUESTS_TODAY] ?: 0 else 0
            val successBase = if (isSameDay) preferences[SUCCESS_TODAY] ?: 0 else 0
            val tokenBase = if (isSameDay) preferences[TOKENS_TODAY] ?: 0 else 0
            val providerIsSameDay = preferences[providerUsageDayKey(providerId)].orEmpty() == dayKey
            val providerRequestBase = if (providerIsSameDay) preferences[providerRequestsTodayKey(providerId)] ?: 0 else 0
            val providerSuccessBase = if (providerIsSameDay) preferences[providerSuccessTodayKey(providerId)] ?: 0 else 0
            val providerTokenBase = if (providerIsSameDay) preferences[providerTokensTodayKey(providerId)] ?: 0 else 0

            preferences[USAGE_DAY_KEY] = dayKey
            preferences[REQUESTS_TODAY] = requestBase + requestIncrement
            preferences[SUCCESS_TODAY] = successBase + successIncrement
            preferences[TOKENS_TODAY] = tokenBase + tokenIncrement
            preferences[providerUsageDayKey(providerId)] = dayKey
            preferences[providerRequestsTodayKey(providerId)] = providerRequestBase + requestIncrement
            preferences[providerSuccessTodayKey(providerId)] = providerSuccessBase + successIncrement
            preferences[providerTokensTodayKey(providerId)] = providerTokenBase + tokenIncrement
        }
    }

    override suspend fun clearProvider(providerId: String) {
        context.aiSettingsDataStore.edit { preferences ->
            val normalized = normalizedProviderId(providerId)
            val activeProviderId = preferences.activeProviderId()
            preferences.remove(providerApiKeyKey(normalized))
            preferences.remove(providerBaseUrlKey(normalized))
            preferences.remove(providerModelKey(normalized))
            preferences.remove(providerAiEnabledKey(normalized))
            preferences.remove(providerLastVerifiedAtKey(normalized))
            preferences.remove(providerLastVerifiedSuccessKey(normalized))
            preferences.remove(providerLastVerificationMessageKey(normalized))
            preferences.remove(providerVerifiedFingerprintKey(normalized))
            preferences.remove(providerUsageDayKey(normalized))
            preferences.remove(providerRequestsTodayKey(normalized))
            preferences.remove(providerSuccessTodayKey(normalized))
            preferences.remove(providerTokensTodayKey(normalized))
            if (activeProviderId == normalized) {
                clearActivePreferences(preferences)
            }
        }
    }

    override suspend fun clear() {
        context.aiSettingsDataStore.edit { preferences ->
            clearActivePreferences(preferences)
        }
    }

    private fun Preferences.activeSettings(): AiSettings {
        val baseUrl = this[BASE_URL] ?: defaultSettings.baseUrl
        val providerId = activeProviderId(baseUrl)
        val legacyFallback = AiSettings(
            providerId = providerId,
            apiKey = this[API_KEY] ?: defaultSettings.apiKey,
            baseUrl = baseUrl,
            model = this[MODEL] ?: defaultSettings.model,
            aiEnabled = this[AI_ENABLED] ?: true,
            lastVerifiedAt = this[LAST_VERIFIED_AT] ?: 0L,
            lastVerifiedSuccess = this[LAST_VERIFIED_SUCCESS] ?: false,
            lastVerificationMessage = this[LAST_VERIFICATION_MESSAGE].orEmpty(),
            verifiedFingerprint = this[VERIFIED_FINGERPRINT].orEmpty(),
            usageDayKey = this[USAGE_DAY_KEY].orEmpty(),
            requestsToday = this[REQUESTS_TODAY] ?: 0,
            successesToday = this[SUCCESS_TODAY] ?: 0,
            tokensToday = this[TOKENS_TODAY] ?: 0,
        )
        return providerSettings(providerId, legacyFallback)
    }

    private fun Preferences.activeProviderId(
        baseUrl: String = this[BASE_URL] ?: defaultSettings.baseUrl,
    ): String =
        normalizedProviderId(
            this[PROVIDER_ID]
                ?: defaultSettings.providerId.takeIf { it.isNotBlank() }
                ?: CloudAiProviderRegistry.resolveProviderId(baseUrl)
        )

    private fun Preferences.providerSettings(
        providerId: String,
        activeFallback: AiSettings,
    ): AiSettings {
        val normalized = normalizedProviderId(providerId)
        val provider = CloudAiProviderRegistry.require(normalized)
        val isActiveProvider = activeFallback.providerId == normalized
        val fallbackApiKey = if (isActiveProvider) activeFallback.apiKey else ""
        val fallbackBaseUrl = if (isActiveProvider) activeFallback.baseUrl else provider.baseUrl
        val fallbackModel = if (isActiveProvider) activeFallback.model else provider.defaultModel
        return AiSettings(
            providerId = normalized,
            apiKey = this[providerApiKeyKey(normalized)] ?: fallbackApiKey,
            baseUrl = this[providerBaseUrlKey(normalized)] ?: fallbackBaseUrl,
            model = this[providerModelKey(normalized)] ?: fallbackModel,
            aiEnabled = this[providerAiEnabledKey(normalized)]
                ?: activeFallback.aiEnabled.takeIf { isActiveProvider }
                ?: true,
            lastVerifiedAt = this[providerLastVerifiedAtKey(normalized)]
                ?: activeFallback.lastVerifiedAt.takeIf { isActiveProvider }
                ?: 0L,
            lastVerifiedSuccess = this[providerLastVerifiedSuccessKey(normalized)]
                ?: activeFallback.lastVerifiedSuccess.takeIf { isActiveProvider }
                ?: false,
            lastVerificationMessage = this[providerLastVerificationMessageKey(normalized)]
                ?: activeFallback.lastVerificationMessage.takeIf { isActiveProvider }
                ?: "",
            verifiedFingerprint = this[providerVerifiedFingerprintKey(normalized)]
                ?: activeFallback.verifiedFingerprint.takeIf { isActiveProvider }
                ?: "",
            usageDayKey = this[providerUsageDayKey(normalized)]
                ?: activeFallback.usageDayKey.takeIf { isActiveProvider }
                ?: "",
            requestsToday = this[providerRequestsTodayKey(normalized)]
                ?: activeFallback.requestsToday.takeIf { isActiveProvider }
                ?: 0,
            successesToday = this[providerSuccessTodayKey(normalized)]
                ?: activeFallback.successesToday.takeIf { isActiveProvider }
                ?: 0,
            tokensToday = this[providerTokensTodayKey(normalized)]
                ?: activeFallback.tokensToday.takeIf { isActiveProvider }
                ?: 0,
        )
    }

    private fun normalizedProviderId(providerId: String): String =
        CloudAiProviderRegistry.get(providerId)?.id ?: CloudAiProviderRegistry.CUSTOM_ID

    private fun clearActivePreferences(preferences: MutablePreferences) {
        preferences.remove(API_KEY)
        preferences.remove(PROVIDER_ID)
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

    private companion object {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
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

        fun providerApiKeyKey(providerId: String) = stringPreferencesKey("provider_${providerId}_api_key")
        fun providerBaseUrlKey(providerId: String) = stringPreferencesKey("provider_${providerId}_base_url")
        fun providerModelKey(providerId: String) = stringPreferencesKey("provider_${providerId}_model")
        fun providerAiEnabledKey(providerId: String) = booleanPreferencesKey("provider_${providerId}_ai_enabled")
        fun providerLastVerifiedAtKey(providerId: String) = longPreferencesKey("provider_${providerId}_last_verified_at")
        fun providerLastVerifiedSuccessKey(providerId: String) =
            booleanPreferencesKey("provider_${providerId}_last_verified_success")
        fun providerLastVerificationMessageKey(providerId: String) =
            stringPreferencesKey("provider_${providerId}_last_verification_message")
        fun providerVerifiedFingerprintKey(providerId: String) =
            stringPreferencesKey("provider_${providerId}_verified_fingerprint")
        fun providerUsageDayKey(providerId: String) = stringPreferencesKey("provider_${providerId}_usage_day_key")
        fun providerRequestsTodayKey(providerId: String) = intPreferencesKey("provider_${providerId}_requests_today")
        fun providerSuccessTodayKey(providerId: String) = intPreferencesKey("provider_${providerId}_success_today")
        fun providerTokensTodayKey(providerId: String) = intPreferencesKey("provider_${providerId}_tokens_today")
    }
}
