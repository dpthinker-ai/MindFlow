package com.mindflow.app.data.model

enum class AiProviderPreset(
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
) {
    OPENAI(
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5.4",
    ),
    ZHIPU(
        label = "智谱",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4.7",
    ),
    CUSTOM(
        label = "自定义",
        baseUrl = "",
        defaultModel = "",
    );

    companion object {
        fun fromBaseUrl(baseUrl: String): AiProviderPreset {
            val normalized = baseUrl.trim().trimEnd('/')
            return entries.firstOrNull { preset ->
                preset != CUSTOM && preset.baseUrl.trimEnd('/') == normalized
            } ?: CUSTOM
        }
    }
}
