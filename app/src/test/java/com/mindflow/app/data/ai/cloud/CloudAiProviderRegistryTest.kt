package com.mindflow.app.data.ai.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudAiProviderRegistryTest {
    @Test
    fun deepSeekProviderUsesCurrentV4Defaults() {
        val spec = CloudAiProviderRegistry.require("deepseek")

        assertThat(spec.label).isEqualTo("DeepSeek")
        assertThat(spec.baseUrl).isEqualTo("https://api.deepseek.com")
        assertThat(spec.chatPath).isEqualTo("/chat/completions")
        assertThat(spec.authScheme).isEqualTo(CloudAiAuthScheme.BEARER_ONLY)
        assertThat(spec.defaultModel).isEqualTo("deepseek-v4-flash")
        assertThat(spec.selectableModels.map { it.id })
            .containsExactly("deepseek-v4-flash", "deepseek-v4-pro")
            .inOrder()
        assertThat(spec.requestCapabilities.supportsThinking).isTrue()
        assertThat(spec.requestCapabilities.supportsReasoningEffort).isTrue()
        assertThat(spec.deprecatedModelAliases["deepseek-chat"]).isEqualTo("deepseek-v4-flash")
        assertThat(spec.deprecatedModelAliases["deepseek-reasoner"]).isEqualTo("deepseek-v4-flash")
    }

    @Test
    fun registryResolvesProviderFromBaseUrl() {
        assertThat(CloudAiProviderRegistry.resolveProviderId("https://api.deepseek.com/"))
            .isEqualTo("deepseek")
        assertThat(CloudAiProviderRegistry.resolveProviderId("https://api.openai.com/v1"))
            .isEqualTo("openai")
        assertThat(CloudAiProviderRegistry.resolveProviderId("https://example.com/openai"))
            .isEqualTo("custom")
    }
}
