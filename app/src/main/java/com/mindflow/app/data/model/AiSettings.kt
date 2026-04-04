package com.mindflow.app.data.model

import java.security.MessageDigest

data class AiSettings(
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
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
        )

    val verificationMatchesCurrentConfig: Boolean
        get() = verifiedFingerprint.isNotBlank() && verifiedFingerprint == configFingerprint

    companion object {
        val DEFAULT_BASE_URL = AiProviderPreset.ZHIPU.baseUrl
        val DEFAULT_MODEL = AiProviderPreset.ZHIPU.defaultModel

        fun fingerprint(
            apiKey: String,
            baseUrl: String,
            model: String,
        ): String {
            val raw = listOf(
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
