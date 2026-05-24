package com.mindflow.app.data.model

import com.mindflow.app.data.ai.cloud.CloudAiProviderRegistry

enum class AiProviderPreset(
    val providerId: String,
) {
    ZHIPU(CloudAiProviderRegistry.ZHIPU_ID),
    OPENAI(CloudAiProviderRegistry.OPENAI_ID),
    DEEPSEEK(CloudAiProviderRegistry.DEEPSEEK_ID),
    CUSTOM(CloudAiProviderRegistry.CUSTOM_ID);

    val label: String
        get() = CloudAiProviderRegistry.require(providerId).label

    val baseUrl: String
        get() = CloudAiProviderRegistry.require(providerId).baseUrl

    val defaultModel: String
        get() = CloudAiProviderRegistry.require(providerId).defaultModel

    companion object {
        fun fromBaseUrl(baseUrl: String): AiProviderPreset {
            val providerId = CloudAiProviderRegistry.resolveProviderId(baseUrl)
            return fromProviderId(providerId)
        }

        fun fromProviderId(providerId: String): AiProviderPreset {
            val normalized = providerId.trim().lowercase()
            return entries.firstOrNull { it.providerId == normalized } ?: CUSTOM
        }
    }
}
