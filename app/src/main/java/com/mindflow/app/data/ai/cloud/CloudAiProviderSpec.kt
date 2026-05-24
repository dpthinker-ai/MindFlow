package com.mindflow.app.data.ai.cloud

enum class CloudAiProtocol {
    OPENAI_CHAT_COMPLETIONS,
}

enum class CloudAiAuthScheme {
    BEARER_ONLY,
    RAW_KEY_ONLY,
    RAW_KEY_THEN_BEARER,
    BEARER_THEN_RAW_KEY,
}

data class CloudAiModelSpec(
    val id: String,
    val label: String,
    val defaultThinking: Boolean = false,
)

data class CloudAiRequestCapabilities(
    val supportsThinking: Boolean = false,
    val supportsReasoningEffort: Boolean = false,
    val supportsJsonObject: Boolean = false,
    val supportsToolCalls: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
)

data class CloudAiProviderSpec(
    val id: String,
    val label: String,
    val protocol: CloudAiProtocol,
    val baseUrl: String,
    val chatPath: String,
    val authScheme: CloudAiAuthScheme,
    val defaultModel: String,
    val selectableModels: List<CloudAiModelSpec>,
    val requestCapabilities: CloudAiRequestCapabilities,
    val deprecatedModelAliases: Map<String, String> = emptyMap(),
)
