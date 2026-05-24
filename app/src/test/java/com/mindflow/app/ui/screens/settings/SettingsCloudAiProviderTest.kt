package com.mindflow.app.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.model.AiProviderPreset
import com.mindflow.app.data.model.AiSettings
import org.junit.Test

class SettingsCloudAiProviderTest {
    @Test
    fun deepSeekPresetSetsProviderBaseUrlAndDefaultModel() {
        val state = SettingsUiState().applyAiProviderPreset(AiProviderPreset.DEEPSEEK)

        assertThat(state.aiProviderPreset).isEqualTo(AiProviderPreset.DEEPSEEK)
        assertThat(state.baseUrl).isEqualTo("https://api.deepseek.com")
        assertThat(state.model).isEqualTo("deepseek-v4-flash")
    }

    @Test
    fun switchingProviderStartsWithEmptyApiKey() {
        val state = SettingsUiState(apiKey = "sk-zhipu")
            .applyAiProviderPreset(AiProviderPreset.DEEPSEEK)

        assertThat(state.apiKey).isEmpty()
    }

    @Test
    fun savedProviderProfileRestoresItsOwnApiKey() {
        val state = SettingsUiState(apiKey = "sk-zhipu")
            .applyAiProviderSettings(
                AiSettings(
                    providerId = AiProviderPreset.DEEPSEEK.providerId,
                    apiKey = "sk-deepseek",
                    baseUrl = "https://api.deepseek.com",
                    model = "deepseek-v4-flash",
                    requestsToday = 3,
                )
            )

        assertThat(state.aiProviderPreset).isEqualTo(AiProviderPreset.DEEPSEEK)
        assertThat(state.apiKey).isEqualTo("sk-deepseek")
        assertThat(state.baseUrl).isEqualTo("https://api.deepseek.com")
        assertThat(state.model).isEqualTo("deepseek-v4-flash")
        assertThat(state.aiRequestsToday).isEqualTo(3)
    }

    @Test
    fun cloudAiSwitchDescriptionMentionsLowFrequencyBackgroundNotice() {
        assertThat(SettingsUiState(aiEnabled = true, isConfigured = true).cloudAiSwitchDescription())
            .contains("低频通知")
    }

    @Test
    fun dirtyCloudAiDraftKeepsDeepSeekWhenSavedZhipuUsageStatsRefresh() {
        val draft = SettingsUiState()
            .applyAiProviderPreset(AiProviderPreset.DEEPSEEK)
            .copy(apiKey = "sk-deepseek")

        val merged = draft.mergeSavedAiSettings(
            settings = AiSettings(
                providerId = AiProviderPreset.ZHIPU.providerId,
                apiKey = "sk-zhipu",
                baseUrl = AiProviderPreset.ZHIPU.baseUrl,
                model = AiProviderPreset.ZHIPU.defaultModel,
                requestsToday = 61,
                successesToday = 11,
                tokensToday = 9_000,
            ),
            preserveDraft = true,
        )

        assertThat(merged.aiProviderPreset).isEqualTo(AiProviderPreset.DEEPSEEK)
        assertThat(merged.apiKey).isEqualTo("sk-deepseek")
        assertThat(merged.baseUrl).isEqualTo("https://api.deepseek.com")
        assertThat(merged.model).isEqualTo("deepseek-v4-flash")
        assertThat(merged.aiRequestsToday).isEqualTo(61)
        assertThat(merged.aiSuccessesToday).isEqualTo(11)
        assertThat(merged.aiTokensToday).isEqualTo(9_000)
    }
}
