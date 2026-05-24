package com.mindflow.app.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.model.AiProviderPreset
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
    fun cloudAiSwitchDescriptionMentionsLowFrequencyBackgroundNotice() {
        assertThat(SettingsUiState(aiEnabled = true, isConfigured = true).cloudAiSwitchDescription())
            .contains("低频通知")
    }
}
