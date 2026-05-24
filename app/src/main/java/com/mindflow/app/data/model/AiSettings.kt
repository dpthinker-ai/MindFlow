package com.mindflow.app.data.model

import com.mindflow.app.data.ai.cloud.CloudAiProviderRegistry
import java.security.MessageDigest

data class AiSettings(
    val providerId: String = DEFAULT_PROVIDER_ID,
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val aiEnabled: Boolean = true,
    val lastVerifiedAt: Long = 0L,
    val lastVerifiedSuccess: Boolean = false,
    val lastVerificationMessage: String = "",
    val verifiedFingerprint: String = "",
    val usageDayKey: String = "",
    val requestsToday: Int = 0,
    val successesToday: Int = 0,
    val tokensToday: Int = 0,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    val configFingerprint: String
        get() = fingerprint(
            providerId = providerId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
        )

    val verificationMatchesCurrentConfig: Boolean
        get() = verifiedFingerprint.isNotBlank() && verifiedFingerprint == configFingerprint

    companion object {
        const val DEFAULT_PROVIDER_ID = CloudAiProviderRegistry.ZHIPU_ID
        val DEFAULT_BASE_URL = AiProviderPreset.ZHIPU.baseUrl
        val DEFAULT_MODEL = AiProviderPreset.ZHIPU.defaultModel

        fun fingerprint(
            providerId: String = DEFAULT_PROVIDER_ID,
            apiKey: String,
            baseUrl: String,
            model: String,
        ): String {
            val raw = listOf(
                providerId.trim(),
                apiKey.trim(),
                baseUrl.trim().trimEnd('/'),
                model.trim(),
            ).joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
