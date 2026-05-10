package com.mindflow.app.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsExecutionModePersistenceTest {
    @Test
    fun persistAiExecutionModeSelection_savesImmediately() = runTest {
        val repository = FakeOnDeviceModelSettingsRepository()

        val nextState = persistAiExecutionModeSelection(
            currentState = SettingsUiState(aiExecutionMode = AiExecutionMode.AUTOMATIC),
            mode = AiExecutionMode.ON_DEVICE_ONLY,
            repository = repository,
        )

        assertThat(nextState.aiExecutionMode).isEqualTo(AiExecutionMode.ON_DEVICE_ONLY)
        assertThat(repository.savedStates.single().executionMode).isEqualTo(AiExecutionMode.ON_DEVICE_ONLY)
    }

    @Test
    fun cloudAiUsableRequiresEnabledAndConfigured() {
        assertThat(SettingsUiState(aiEnabled = true, isConfigured = false).isCloudAiUsable()).isFalse()
        assertThat(SettingsUiState(aiEnabled = false, isConfigured = true).isCloudAiUsable()).isFalse()
        assertThat(SettingsUiState(aiEnabled = true, isConfigured = true).isCloudAiUsable()).isTrue()
    }

    @Test
    fun cloudAiSwitchDescriptionDoesNotPretendCloudWorksWithoutKey() {
        assertThat(SettingsUiState(aiEnabled = true, isConfigured = false).cloudAiSwitchDescription())
            .isEqualTo("补全 API Key 并保存后，才能启用云端能力")
    }

    private class FakeOnDeviceModelSettingsRepository : OnDeviceModelSettingsRepository {
        private val flow = MutableStateFlow(OnDeviceModelSettings())
        val savedStates = mutableListOf<OnDeviceModelSettings>()

        override val settings: Flow<OnDeviceModelSettings> = flow

        override suspend fun getCurrent(): OnDeviceModelSettings = flow.value

        override suspend fun save(settings: OnDeviceModelSettings) {
            savedStates += settings
            flow.value = settings
        }

        override suspend fun markDownloading(
            downloadUrl: String,
            downloadedBytes: Long,
            targetBytes: Long,
            message: String,
        ) = Unit

        override suspend fun markDownloadProgress(
            downloadedBytes: Long,
            targetBytes: Long,
            message: String,
        ) = Unit

        override suspend fun markReady(
            localModelPath: String,
            downloadedBytes: Long,
            targetBytes: Long,
            message: String,
            downloadedAt: Long,
        ) = Unit

        override suspend fun markError(
            message: String,
            downloadedBytes: Long?,
            targetBytes: Long?,
        ) = Unit

        override suspend fun clearDownloadState() = Unit
    }
}
