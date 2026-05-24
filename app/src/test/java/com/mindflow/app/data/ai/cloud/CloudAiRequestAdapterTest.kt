package com.mindflow.app.data.ai.cloud

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.model.AiSettings
import org.junit.Test

class CloudAiRequestAdapterTest {
    @Test
    fun deepSeekBuildsChatCompletionWithBearerAuthAndThinking() {
        val request = CloudAiRequestAdapter.build(
            settings = AiSettings(
                providerId = "deepseek",
                apiKey = "sk-test",
                baseUrl = "https://api.deepseek.com",
                model = "deepseek-v4-pro",
            ),
            systemPrompt = "system",
            userPrompt = "user",
            maxTokens = 64,
            temperature = 0.2,
            thinkingEnabled = true,
        )

        assertThat(request.provider.id).isEqualTo("deepseek")
        assertThat(request.url).isEqualTo("https://api.deepseek.com/chat/completions")
        assertThat(request.authHeaders).containsExactly("Bearer sk-test")
        assertThat(request.body).contains("\"model\":\"deepseek-v4-pro\"")
        assertThat(request.body).contains("\"thinking\":{\"type\":\"enabled\"}")
    }

    @Test
    fun openAiDoesNotSendDeepSeekOnlyThinkingField() {
        val request = CloudAiRequestAdapter.build(
            settings = AiSettings(
                providerId = "openai",
                apiKey = "Bearer sk-test",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-5.4",
            ),
            systemPrompt = "system",
            userPrompt = "user",
            maxTokens = 64,
            temperature = 0.2,
            thinkingEnabled = true,
        )

        assertThat(request.url).isEqualTo("https://api.openai.com/v1/chat/completions")
        assertThat(request.authHeaders).containsExactly("Bearer sk-test")
        assertThat(request.body).doesNotContain("\"thinking\"")
    }

    @Test
    fun customProviderUsesRawKeyThenBearerAndOmitsThinking() {
        val request = CloudAiRequestAdapter.build(
            settings = AiSettings(
                providerId = "custom",
                apiKey = "custom-key",
                baseUrl = "https://example.com/v1/",
                model = "custom-model",
            ),
            systemPrompt = "system",
            userPrompt = "user",
            maxTokens = 64,
            temperature = 0.2,
            thinkingEnabled = true,
        )

        assertThat(request.url).isEqualTo("https://example.com/v1/chat/completions")
        assertThat(request.authHeaders).containsExactly("custom-key", "Bearer custom-key").inOrder()
        assertThat(request.body).doesNotContain("\"thinking\"")
    }

    @Test
    fun deprecatedDeepSeekModelAliasesMoveToCurrentDefault() {
        val request = CloudAiRequestAdapter.build(
            settings = AiSettings(
                providerId = "deepseek",
                apiKey = "sk-test",
                baseUrl = "https://api.deepseek.com",
                model = "deepseek-chat",
            ),
            systemPrompt = "system",
            userPrompt = "user",
            maxTokens = 64,
            temperature = 0.2,
            thinkingEnabled = false,
        )

        assertThat(request.body).contains("\"model\":\"deepseek-v4-flash\"")
    }
}
