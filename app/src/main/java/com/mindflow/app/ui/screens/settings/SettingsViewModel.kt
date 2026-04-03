package com.mindflow.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.model.AiProviderPreset
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.model.CloudBackupSettings
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.ReminderSettings
import com.mindflow.app.data.model.TimeBankSettings
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.data.topic.AiServiceClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val aiProviderPreset: AiProviderPreset = AiProviderPreset.OPENAI,
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
    val morningBriefEnabled: Boolean = false,
    val eveningReviewEnabled: Boolean = false,
    val timeBankCurrentAge: String = TimeBankSettings().currentAge.toString(),
    val timeBankExpectedLifespan: String = TimeBankSettings().expectedLifespan.toString(),
    val timeBankActiveDaysPerWeek: String = TimeBankSettings().activeDaysPerWeek.toString(),
    val isSavingAi: Boolean = false,
    val isSavingCloud: Boolean = false,
    val isSavingReminder: Boolean = false,
    val isSavingTimeBank: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isTestingAi: Boolean = false,
    val isBackingUpCloud: Boolean = false,
    val isRestoringCloud: Boolean = false,
) {
    val timeBankPreview: TimeBankSettings
        get() = TimeBankSettings(
            currentAge = timeBankCurrentAge.toIntOrNull() ?: TimeBankSettings().currentAge,
            expectedLifespan = timeBankExpectedLifespan.toIntOrNull() ?: TimeBankSettings().expectedLifespan,
            activeDaysPerWeek = (timeBankActiveDaysPerWeek.toIntOrNull() ?: TimeBankSettings().activeDaysPerWeek).coerceIn(1, 7),
        )
}

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class ExportReady(val payload: ExportPayload) : SettingsEvent
}

class SettingsViewModel(
    private val noteRepository: NoteRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val timeBankSettingsRepository: TimeBankSettingsRepository,
    private val cloudBackupCoordinator: CloudBackupCoordinator,
    private val reminderScheduler: ReminderScheduler,
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
                        aiProviderPreset = AiProviderPreset.fromBaseUrl(settings.baseUrl),
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
        viewModelScope.launch {
            reminderSettingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        morningBriefEnabled = settings.morningBriefEnabled,
                        eveningReviewEnabled = settings.eveningReviewEnabled,
                    )
                }
            }
        }
        viewModelScope.launch {
            timeBankSettingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        timeBankCurrentAge = settings.currentAge.toString(),
                        timeBankExpectedLifespan = settings.expectedLifespan.toString(),
                        timeBankActiveDaysPerWeek = settings.activeDaysPerWeek.toString(),
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

    fun onAiProviderPresetChange(value: AiProviderPreset) {
        _uiState.update { state ->
            when (value) {
                AiProviderPreset.OPENAI,
                AiProviderPreset.ZHIPU,
                -> state.copy(
                    aiProviderPreset = value,
                    baseUrl = value.baseUrl,
                    model = value.defaultModel,
                )

                AiProviderPreset.CUSTOM -> state.copy(
                    aiProviderPreset = value,
                )
            }
        }
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

    fun onMorningBriefEnabledChange(value: Boolean) {
        _uiState.update { it.copy(morningBriefEnabled = value) }
    }

    fun onEveningReviewEnabledChange(value: Boolean) {
        _uiState.update { it.copy(eveningReviewEnabled = value) }
    }

    fun onTimeBankCurrentAgeChange(value: String) {
        _uiState.update { it.copy(timeBankCurrentAge = value.filter(Char::isDigit).take(3)) }
    }

    fun onTimeBankExpectedLifespanChange(value: String) {
        _uiState.update { it.copy(timeBankExpectedLifespan = value.filter(Char::isDigit).take(3)) }
    }

    fun onTimeBankActiveDaysPerWeekChange(value: String) {
        _uiState.update { it.copy(timeBankActiveDaysPerWeek = value.filter(Char::isDigit).take(1)) }
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

    fun saveReminder() {
        val state = _uiState.value
        val settings = ReminderSettings(
            morningBriefEnabled = state.morningBriefEnabled,
            eveningReviewEnabled = state.eveningReviewEnabled,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingReminder = true) }
            reminderSettingsRepository.save(settings)
            reminderScheduler.syncNow(settings)
            _uiState.update { it.copy(isSavingReminder = false) }
            _events.emit(
                SettingsEvent.Message(
                    if (settings.hasAnyEnabled) "提醒已保存，晨间和晚间会按时提示你" else "已关闭每日提醒"
                )
            )
        }
    }

    fun saveTimeBank() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTimeBank = true) }
            timeBankSettingsRepository.save(state.timeBankPreview)
            _uiState.update { it.copy(isSavingTimeBank = false) }
            _events.emit(SettingsEvent.Message("时间银行已更新"))
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
            reminderSettingsRepository: ReminderSettingsRepository,
            timeBankSettingsRepository: TimeBankSettingsRepository,
            cloudBackupCoordinator: CloudBackupCoordinator,
            reminderScheduler: ReminderScheduler,
            aiServiceClient: AiServiceClient,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    reminderSettingsRepository = reminderSettingsRepository,
                    timeBankSettingsRepository = timeBankSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    reminderScheduler = reminderScheduler,
                    aiServiceClient = aiServiceClient,
                )
            }
        }
    }
}
