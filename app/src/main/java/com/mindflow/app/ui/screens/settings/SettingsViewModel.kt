package com.mindflow.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.model.CloudBackupSettings
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.topic.AiServiceClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = AiSettings.DEFAULT_BASE_URL,
    val model: String = AiSettings.DEFAULT_MODEL,
    val aiEnabled: Boolean = true,
    val isConfigured: Boolean = false,
    val aiLastVerifiedAt: Long = 0L,
    val aiLastVerifiedSuccess: Boolean = false,
    val aiLastVerificationMessage: String = "",
    val aiVerifiedFingerprint: String = "",
    val aiRequestsToday: Int = 0,
    val aiSuccessesToday: Int = 0,
    val aiTokensToday: Int = 0,
    val cloudBaseUrl: String = CloudBackupSettings.DEFAULT_BASE_URL,
    val cloudUsername: String = "",
    val cloudPassword: String = "",
    val cloudRemoteDir: String = CloudBackupSettings.DEFAULT_REMOTE_DIR,
    val cloudAutoBackupEnabled: Boolean = false,
    val cloudIsConfigured: Boolean = false,
    val cloudLastBackupAt: Long = 0L,
    val cloudLastBackupError: String = "",
    val isSavingAi: Boolean = false,
    val isSavingCloud: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isTestingAi: Boolean = false,
    val isBackingUpCloud: Boolean = false,
    val isRestoringCloud: Boolean = false,
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class ExportReady(val payload: ExportPayload) : SettingsEvent
}

class SettingsViewModel(
    private val noteRepository: NoteRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    private val cloudBackupCoordinator: CloudBackupCoordinator,
    private val aiServiceClient: AiServiceClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            aiSettingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        apiKey = settings.apiKey,
                        baseUrl = settings.baseUrl,
                        model = settings.model,
                        aiEnabled = settings.aiEnabled,
                        isConfigured = settings.isConfigured,
                        aiLastVerifiedAt = settings.lastVerifiedAt,
                        aiLastVerifiedSuccess = settings.lastVerifiedSuccess,
                        aiLastVerificationMessage = settings.lastVerificationMessage,
                        aiVerifiedFingerprint = settings.verifiedFingerprint,
                        aiRequestsToday = settings.requestsToday,
                        aiSuccessesToday = settings.successesToday,
                        aiTokensToday = settings.tokensToday,
                    )
                }
            }
        }
        viewModelScope.launch {
            cloudBackupSettingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        cloudBaseUrl = settings.baseUrl,
                        cloudUsername = settings.username,
                        cloudPassword = settings.password,
                        cloudRemoteDir = settings.remoteDir,
                        cloudAutoBackupEnabled = settings.autoBackupEnabled,
                        cloudIsConfigured = settings.isConfigured,
                        cloudLastBackupAt = settings.lastBackupAt,
                        cloudLastBackupError = settings.lastBackupError,
                    )
                }
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value) }
    }

    fun onAiEnabledChange(value: Boolean) {
        _uiState.update { it.copy(aiEnabled = value) }
    }

    fun onCloudBaseUrlChange(value: String) {
        _uiState.update { it.copy(cloudBaseUrl = value) }
    }

    fun onCloudUsernameChange(value: String) {
        _uiState.update { it.copy(cloudUsername = value) }
    }

    fun onCloudPasswordChange(value: String) {
        _uiState.update { it.copy(cloudPassword = value) }
    }

    fun onCloudRemoteDirChange(value: String) {
        _uiState.update { it.copy(cloudRemoteDir = value) }
    }

    fun onCloudAutoBackupChange(value: Boolean) {
        _uiState.update { it.copy(cloudAutoBackupEnabled = value) }
    }

    fun saveAi() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAi = true) }
            aiSettingsRepository.save(
                AiSettings(
                    apiKey = state.apiKey,
                    baseUrl = state.baseUrl,
                    model = state.model,
                    aiEnabled = state.aiEnabled,
                )
            )
            _uiState.update { it.copy(isSavingAi = false) }
            _events.emit(
                SettingsEvent.Message(
                    if (_uiState.value.isConfigured) "AI 配置已保存" else "已保存，当前仍会回退本地规则"
                )
            )
        }
    }

    fun testAiConnection() {
        val state = _uiState.value
        val testSettings = AiSettings(
            apiKey = state.apiKey,
            baseUrl = state.baseUrl,
            model = state.model,
            aiEnabled = state.aiEnabled,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAi = true) }
            val result = aiServiceClient.testConnection(testSettings)
            aiSettingsRepository.updateVerificationStatus(
                fingerprint = testSettings.configFingerprint,
                success = result.isConfiguredCorrectly,
                message = result.message,
            )
            _events.emit(SettingsEvent.Message(result.message))
            _uiState.update { it.copy(isTestingAi = false) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            aiSettingsRepository.clear()
            _events.emit(SettingsEvent.Message("AI 配置已清空"))
        }
    }

    fun saveCloud() {
        val state = _uiState.value
        val nextSettings = CloudBackupSettings(
            baseUrl = state.cloudBaseUrl,
            username = state.cloudUsername,
            password = state.cloudPassword,
            remoteDir = state.cloudRemoteDir,
            autoBackupEnabled = state.cloudAutoBackupEnabled,
            lastBackupAt = state.cloudLastBackupAt,
            lastBackupError = state.cloudLastBackupError,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingCloud = true) }
            cloudBackupSettingsRepository.save(nextSettings)
            _uiState.update { it.copy(isSavingCloud = false) }
            _events.emit(
                SettingsEvent.Message(
                    if (nextSettings.isConfigured) {
                        if (nextSettings.autoBackupEnabled) "云备份配置已保存，自动同步已改为每天最多一次" else "云备份配置已保存"
                    } else {
                        "已保存，补全用户名和应用密码后即可备份"
                    }
                )
            )
        }
    }

    fun clearCloud() {
        viewModelScope.launch {
            cloudBackupSettingsRepository.clear()
            _events.emit(SettingsEvent.Message("云备份配置已清空"))
        }
    }

    fun backupToCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUpCloud = true) }
            runCatching { cloudBackupCoordinator.backupNow() }
                .onSuccess { _events.emit(SettingsEvent.Message("云备份完成")) }
                .onFailure { _events.emit(SettingsEvent.Message(it.message ?: "云备份失败")) }
            _uiState.update { it.copy(isBackingUpCloud = false) }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoringCloud = true) }
            runCatching { cloudBackupCoordinator.restoreLatest() }
                .onSuccess {
                    _events.emit(SettingsEvent.Message("已从云端恢复 ${it.noteCount} 条记录，恢复 ${it.historyCount} 条状态历史"))
                }
                .onFailure { _events.emit(SettingsEvent.Message(it.message ?: "云端恢复失败")) }
            _uiState.update { it.copy(isRestoringCloud = false) }
        }
    }

    fun importMarkdown(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching { noteRepository.importNotes(content) }
                .onSuccess {
                    _events.emit(SettingsEvent.Message("已导入 ${it.noteCount} 条记录，恢复 ${it.historyCount} 条状态历史"))
                }
                .onFailure {
                    _events.emit(SettingsEvent.Message(it.message ?: "导入失败"))
                }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun exportMarkdown() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching { noteRepository.exportAllNotes() }
                .onSuccess { _events.emit(SettingsEvent.ExportReady(it)) }
                .onFailure { _events.emit(SettingsEvent.Message("导出失败")) }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            aiSettingsRepository: AiSettingsRepository,
            cloudBackupSettingsRepository: CloudBackupSettingsRepository,
            cloudBackupCoordinator: CloudBackupCoordinator,
            aiServiceClient: AiServiceClient,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    aiServiceClient = aiServiceClient,
                )
            }
        }
    }
}
