package com.mindflow.app.data.ai.cloud

object CloudAiProviderRegistry {
    const val ZHIPU_ID = "zhipu"
    const val OPENAI_ID = "openai"
    const val DEEPSEEK_ID = "deepseek"
    const val CUSTOM_ID = "custom"

    private val zhipu = CloudAiProviderSpec(
        id = ZHIPU_ID,
        label = "智谱",
        protocol = CloudAiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        chatPath = "/chat/completions",
        authScheme = CloudAiAuthScheme.RAW_KEY_THEN_BEARER,
        defaultModel = "glm-4.7",
        selectableModels = listOf(
            CloudAiModelSpec(id = "glm-4.7", label = "GLM 4.7"),
        ),
        requestCapabilities = CloudAiRequestCapabilities(
            supportsJsonObject = true,
            supportsToolCalls = true,
        ),
    )

    private val openAi = CloudAiProviderSpec(
        id = OPENAI_ID,
        label = "OpenAI",
        protocol = CloudAiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.openai.com/v1",
        chatPath = "/chat/completions",
        authScheme = CloudAiAuthScheme.BEARER_ONLY,
        defaultModel = "gpt-5.4",
        selectableModels = listOf(
            CloudAiModelSpec(id = "gpt-5.4", label = "GPT 5.4"),
        ),
        requestCapabilities = CloudAiRequestCapabilities(
            supportsJsonObject = true,
            supportsToolCalls = true,
        ),
    )

    private val deepSeek = CloudAiProviderSpec(
        id = DEEPSEEK_ID,
        label = "DeepSeek",
        protocol = CloudAiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com",
        chatPath = "/chat/completions",
        authScheme = CloudAiAuthScheme.BEARER_ONLY,
        defaultModel = "deepseek-v4-flash",
        selectableModels = listOf(
            CloudAiModelSpec(id = "deepseek-v4-flash", label = "V4 Flash", defaultThinking = false),
            CloudAiModelSpec(id = "deepseek-v4-pro", label = "V4 Pro", defaultThinking = true),
        ),
        requestCapabilities = CloudAiRequestCapabilities(
            supportsThinking = true,
            supportsReasoningEffort = true,
            supportsJsonObject = true,
            supportsToolCalls = true,
        ),
        deprecatedModelAliases = mapOf(
            "deepseek-chat" to "deepseek-v4-flash",
            "deepseek-reasoner" to "deepseek-v4-flash",
        ),
    )

    private val custom = CloudAiProviderSpec(
        id = CUSTOM_ID,
        label = "自定义",
        protocol = CloudAiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "",
        chatPath = "/chat/completions",
        authScheme = CloudAiAuthScheme.RAW_KEY_THEN_BEARER,
        defaultModel = "",
        selectableModels = emptyList(),
        requestCapabilities = CloudAiRequestCapabilities(),
    )

    val builtInProviders: List<CloudAiProviderSpec> = listOf(zhipu, openAi, deepSeek, custom)

    fun get(id: String): CloudAiProviderSpec? {
        val normalized = id.trim().lowercase()
        return builtInProviders.firstOrNull { it.id == normalized }
    }

    fun require(id: String): CloudAiProviderSpec =
        get(id) ?: custom

    fun resolveProviderId(baseUrl: String): String {
        val normalized = baseUrl.normalizedBaseUrl()
        if (normalized.isBlank()) return CUSTOM_ID
        return builtInProviders
            .filter { it.id != CUSTOM_ID }
            .firstOrNull { it.baseUrl.normalizedBaseUrl() == normalized }
            ?.id
            ?: CUSTOM_ID
    }

    fun resolve(providerId: String, baseUrl: String): CloudAiProviderSpec {
        val explicit = get(providerId)
        if (explicit != null && explicit.id != CUSTOM_ID) return explicit
        return get(resolveProviderId(baseUrl)) ?: custom
    }
}

internal fun String.normalizedBaseUrl(): String =
    trim().trimEnd('/')
