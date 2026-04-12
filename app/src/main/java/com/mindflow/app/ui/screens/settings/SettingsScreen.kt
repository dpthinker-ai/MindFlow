package com.mindflow.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.data.model.AiProviderPreset
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.model.OnDeviceModelStatus
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.MetricTile
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.util.TimeFormatter
import kotlinx.coroutines.flow.collectLatest

private enum class SettingsDestination {
    HOME,
    CLOUD,
    AI,
    LOCAL_MODEL,
    REMINDER,
    TIME_BANK,
    DIRECTION_WIKI,
}

@Composable
fun SettingsRoute(
    noteRepository: NoteRepository,
    aiSettingsRepository: AiSettingsRepository,
    cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    reminderSettingsRepository: ReminderSettingsRepository,
    timeBankSettingsRepository: TimeBankSettingsRepository,
    cloudBackupCoordinator: CloudBackupCoordinator,
    onDeviceModelManager: OnDeviceModelManager,
    reminderScheduler: ReminderScheduler,
    directionWikiCoordinator: DirectionWikiCoordinator,
    aiServiceClient: AiServiceClient,
    onDeviceAiClient: OnDeviceAiClient,
    localKnowledgeMaintenancePlanner: LocalKnowledgeMaintenancePlanner,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            noteRepository = noteRepository,
            aiSettingsRepository = aiSettingsRepository,
            cloudBackupSettingsRepository = cloudBackupSettingsRepository,
            onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
            reminderSettingsRepository = reminderSettingsRepository,
            timeBankSettingsRepository = timeBankSettingsRepository,
            cloudBackupCoordinator = cloudBackupCoordinator,
            onDeviceModelManager = onDeviceModelManager,
            reminderScheduler = reminderScheduler,
            directionWikiCoordinator = directionWikiCoordinator,
            aiServiceClient = aiServiceClient,
            onDeviceAiClient = onDeviceAiClient,
            localKnowledgeMaintenancePlanner = localKnowledgeMaintenancePlanner,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingReminderPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { content ->
                viewModel.importMarkdown(content)
            }.onFailure {
                Toast.makeText(context, "读取导入文件失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload.content)
                }
            }.onSuccess {
                Toast.makeText(context, "导出完成", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExport = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingReminderPermission
        pendingReminderPermission = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(context, "需要通知权限才能收到每日提醒", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ExportReady -> {
                    pendingExport = event.payload
                    exportLauncher.launch(event.payload.fileName)
                }
                is SettingsEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        onAiEnabledChange = viewModel::onAiEnabledChange,
        onAiProviderPresetChange = viewModel::onAiProviderPresetChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelChange = viewModel::onModelChange,
        onLocalModelDownloadUrlChange = viewModel::onLocalModelDownloadUrlChange,
        onLocalPreferOnDeviceChange = viewModel::onLocalPreferOnDeviceChange,
        onSaveAi = viewModel::saveAi,
        onSaveLocalModel = viewModel::saveLocalModel,
        onTestAi = viewModel::testAiConnection,
        onTestLocalModel = viewModel::testLocalModel,
        onClearAi = viewModel::clear,
        onDownloadLocalModel = viewModel::downloadLocalModel,
        onDeleteLocalModel = viewModel::deleteLocalModel,
        onRefreshLocalKnowledge = viewModel::refreshLocalKnowledge,
        onCloudBaseUrlChange = viewModel::onCloudBaseUrlChange,
        onCloudUsernameChange = viewModel::onCloudUsernameChange,
        onCloudPasswordChange = viewModel::onCloudPasswordChange,
        onCloudRemoteDirChange = viewModel::onCloudRemoteDirChange,
        onCloudAutoBackupChange = viewModel::onCloudAutoBackupChange,
        onMorningBriefEnabledChange = viewModel::onMorningBriefEnabledChange,
        onEveningReviewEnabledChange = viewModel::onEveningReviewEnabledChange,
        onTimeBankCurrentAgeChange = viewModel::onTimeBankCurrentAgeChange,
        onTimeBankExpectedLifespanChange = viewModel::onTimeBankExpectedLifespanChange,
        onTimeBankActiveDaysPerWeekChange = viewModel::onTimeBankActiveDaysPerWeekChange,
        onSaveCloud = viewModel::saveCloud,
        onSaveReminder = viewModel::saveReminder,
        onSaveTimeBank = viewModel::saveTimeBank,
        onRefreshDirectionWiki = viewModel::refreshDirectionWiki,
        onClearCloud = viewModel::clearCloud,
        onBackupToCloud = viewModel::backupToCloud,
        onRestoreRequest = { showRestoreDialog = true },
        onRestoreConfirmed = {
            showRestoreDialog = false
            viewModel.restoreFromCloud()
        },
        onRestoreDismissed = { showRestoreDialog = false },
        onExport = viewModel::exportMarkdown,
        onImport = { importLauncher.launch(arrayOf("text/*", "text/markdown", "application/octet-stream")) },
        onRequestNotificationPermission = { afterGranted ->
            val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                afterGranted()
            } else {
                pendingReminderPermission = afterGranted
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        showRestoreDialog = showRestoreDialog,
    )
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onAiEnabledChange: (Boolean) -> Unit,
    onAiProviderPresetChange: (AiProviderPreset) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onLocalModelDownloadUrlChange: (String) -> Unit,
    onLocalPreferOnDeviceChange: (Boolean) -> Unit,
    onSaveAi: () -> Unit,
    onSaveLocalModel: () -> Unit,
    onTestAi: () -> Unit,
    onTestLocalModel: () -> Unit,
    onClearAi: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onDeleteLocalModel: () -> Unit,
    onRefreshLocalKnowledge: () -> Unit,
    onCloudBaseUrlChange: (String) -> Unit,
    onCloudUsernameChange: (String) -> Unit,
    onCloudPasswordChange: (String) -> Unit,
    onCloudRemoteDirChange: (String) -> Unit,
    onCloudAutoBackupChange: (Boolean) -> Unit,
    onMorningBriefEnabledChange: (Boolean) -> Unit,
    onEveningReviewEnabledChange: (Boolean) -> Unit,
    onTimeBankCurrentAgeChange: (String) -> Unit,
    onTimeBankExpectedLifespanChange: (String) -> Unit,
    onTimeBankActiveDaysPerWeekChange: (String) -> Unit,
    onSaveCloud: () -> Unit,
    onSaveReminder: () -> Unit,
    onSaveTimeBank: () -> Unit,
    onRefreshDirectionWiki: () -> Unit,
    onClearCloud: () -> Unit,
    onBackupToCloud: () -> Unit,
    onRestoreRequest: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onRestoreDismissed: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
    showRestoreDialog: Boolean,
) {
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.HOME) }

    BackHandler(enabled = destination != SettingsDestination.HOME) {
        destination = SettingsDestination.HOME
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = onRestoreDismissed,
            title = { Text("从云端恢复") },
            text = { Text("这会覆盖当前本地记录。建议先做一次 Markdown 导出。") },
            confirmButton = {
                TextButton(onClick = onRestoreConfirmed) {
                    Text("继续恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = onRestoreDismissed) {
                    Text("取消")
                }
            },
        )
    }

    ScreenBackground {
        when (destination) {
            SettingsDestination.HOME -> SettingsHomeScreen(
                uiState = uiState,
                onOpenCloud = { destination = SettingsDestination.CLOUD },
                onOpenAi = { destination = SettingsDestination.AI },
                onOpenLocalModel = { destination = SettingsDestination.LOCAL_MODEL },
                onOpenReminder = { destination = SettingsDestination.REMINDER },
                onOpenTimeBank = { destination = SettingsDestination.TIME_BANK },
                onOpenDirectionWiki = { destination = SettingsDestination.DIRECTION_WIKI },
                onExport = onExport,
                onImport = onImport,
            )
            SettingsDestination.CLOUD -> CloudBackupScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onCloudBaseUrlChange = onCloudBaseUrlChange,
                onCloudUsernameChange = onCloudUsernameChange,
                onCloudPasswordChange = onCloudPasswordChange,
                onCloudRemoteDirChange = onCloudRemoteDirChange,
                onCloudAutoBackupChange = onCloudAutoBackupChange,
                onSaveCloud = onSaveCloud,
                onClearCloud = onClearCloud,
                onBackupToCloud = onBackupToCloud,
                onRestoreRequest = onRestoreRequest,
            )
            SettingsDestination.AI -> AiSettingsScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onAiEnabledChange = onAiEnabledChange,
                onAiProviderPresetChange = onAiProviderPresetChange,
                onApiKeyChange = onApiKeyChange,
                onBaseUrlChange = onBaseUrlChange,
                onModelChange = onModelChange,
                onSaveAi = onSaveAi,
                onTestAi = onTestAi,
                onClearAi = onClearAi,
            )
            SettingsDestination.LOCAL_MODEL -> LocalModelSettingsScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onLocalModelDownloadUrlChange = onLocalModelDownloadUrlChange,
                onLocalPreferOnDeviceChange = onLocalPreferOnDeviceChange,
                onSaveLocalModel = onSaveLocalModel,
                onTestLocalModel = onTestLocalModel,
                onDownloadLocalModel = onDownloadLocalModel,
                onDeleteLocalModel = onDeleteLocalModel,
                onRefreshLocalKnowledge = onRefreshLocalKnowledge,
            )
            SettingsDestination.REMINDER -> ReminderSettingsScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onMorningBriefEnabledChange = onMorningBriefEnabledChange,
                onEveningReviewEnabledChange = onEveningReviewEnabledChange,
                onSaveReminder = onSaveReminder,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
            SettingsDestination.TIME_BANK -> TimeBankSettingsScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onTimeBankCurrentAgeChange = onTimeBankCurrentAgeChange,
                onTimeBankExpectedLifespanChange = onTimeBankExpectedLifespanChange,
                onTimeBankActiveDaysPerWeekChange = onTimeBankActiveDaysPerWeekChange,
                onSaveTimeBank = onSaveTimeBank,
            )
            SettingsDestination.DIRECTION_WIKI -> DirectionWikiSettingsScreen(
                uiState = uiState,
                onBack = { destination = SettingsDestination.HOME },
                onRefreshDirectionWiki = onRefreshDirectionWiki,
            )
        }
    }
}

@Composable
private fun SettingsHomeScreen(
    uiState: SettingsUiState,
    onOpenCloud: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenLocalModel: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenTimeBank: () -> Unit,
    onOpenDirectionWiki: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                top = 6.dp,
                end = ScreenHorizontalPadding,
                bottom = BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsEntryCard(
                    title = "云备份",
                    summary = if (uiState.cloudIsConfigured) {
                        if (uiState.cloudLastBackupAt > 0L) "最近备份 ${TimeFormatter.compact(uiState.cloudLastBackupAt)}" else "已连接"
                    } else {
                        "未配置"
                    },
                    headline = when {
                        uiState.cloudIsConfigured && uiState.cloudAutoBackupEnabled -> "自动备份"
                        uiState.cloudIsConfigured -> "已连接"
                        else -> "未配置"
                    },
                    accent = AccentBlue,
                    onClick = onOpenCloud,
                )
            }

            item {
                SettingsEntryCard(
                    title = "AI 能力",
                    summary = if (uiState.isConfigured) {
                        uiState.model.ifBlank { "已配置" }
                    } else {
                        "未配置"
                    },
                    headline = if (uiState.isConfigured) "AI 整理 + 判断" else "本地规则",
                    accent = Accent,
                    onClick = onOpenAi,
                )
            }

            item {
                SettingsEntryCard(
                    title = "本地模型",
                    summary = when (uiState.localModelStatus) {
                        OnDeviceModelStatus.READY -> "${uiState.localModelLabel} · ${formatFileSize(uiState.localModelDownloadedBytes)}"
                        OnDeviceModelStatus.DOWNLOADING -> "正在下载"
                        OnDeviceModelStatus.ERROR -> uiState.localModelLastMessage.ifBlank {
                            if (uiState.localModelDownloadedBytes > 0L) "下载中断，可继续下载" else "下载失败"
                        }
                        OnDeviceModelStatus.NOT_DOWNLOADED -> "未下载"
                    },
                    headline = if (uiState.localModelPreferOnDevice && uiState.localModelStatus == OnDeviceModelStatus.READY) {
                        "本地优先"
                    } else if (uiState.localModelStatus == OnDeviceModelStatus.READY) {
                        "已就绪"
                    } else {
                        "未就绪"
                    },
                    accent = AccentBlue,
                    onClick = onOpenLocalModel,
                )
            }

            item {
                SettingsEntryCard(
                    title = "每日提醒",
                    summary = when {
                        uiState.morningBriefEnabled && uiState.eveningReviewEnabled -> "晨间 + 晚间"
                        uiState.morningBriefEnabled -> "仅晨间"
                        uiState.eveningReviewEnabled -> "仅晚间"
                        else -> "未开启"
                    },
                    headline = if (uiState.morningBriefEnabled || uiState.eveningReviewEnabled) "已开启" else "未开启",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onOpenReminder,
                )
            }

            item {
                SettingsEntryCard(
                    title = "时间银行",
                    summary = "按每周 ${uiState.timeBankPreview.activeDaysPerWeek} 天估算，还可主动投入 ${uiState.timeBankPreview.remainingActiveDays} 天",
                    headline = "可用时间",
                    accent = Accent,
                    onClick = onOpenTimeBank,
                )
            }

            item {
                SettingsEntryCard(
                    title = "知识层",
                    summary = if (uiState.directionWikiLastRefreshedAt > 0L) {
                        "最近更新 ${TimeFormatter.compact(uiState.directionWikiLastRefreshedAt)}"
                    } else {
                        "尚未生成"
                    },
                    headline = if (uiState.directionWikiDirectionCount > 0) {
                        "${uiState.directionWikiDirectionCount} 条方向资产"
                    } else {
                        "未生成"
                    },
                    accent = AccentBlue,
                    onClick = onOpenDirectionWiki,
                )
            }

            item {
                Surface(
                    color = WhiteGlass.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionHeader(title = "本地备份", headline = "Markdown")
                        GridTwo {
                            ActionButton(
                                text = if (uiState.isExporting) "导出中..." else "导出",
                                onClick = onExport,
                                enabled = !uiState.isExporting,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.FileDownload,
                            )
                            GhostActionButton(
                                text = if (uiState.isImporting) "导入中..." else "导入",
                                onClick = onImport,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isImporting,
                                icon = Icons.Outlined.RestorePage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionWikiSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onRefreshDirectionWiki: () -> Unit,
) {
    DetailScreenFrame(
        title = "知识层",
        subtitle = "长期知识资产层",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(
                    title = "当前状态",
                    headline = if (uiState.directionWikiDirectionCount > 0) "${uiState.directionWikiDirectionCount} 条方向资产" else "尚未生成",
                )
                Text(
                    text = "先把关注方向沉淀成知识层切片，再逐步扩展到概念、问题、方法和实验。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                uiState.directionWikiLastRefreshedAt
                    .takeIf { it > 0L }
                    ?.let { updatedAt ->
                        Text(
                            text = "最近更新 ${TimeFormatter.compact(updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                    }
                uiState.directionWikiRootPath
                    .takeIf { it.isNotBlank() }
                    ?.let { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                ActionButton(
                    text = if (uiState.isRefreshingDirectionWiki) "更新中..." else "更新知识层",
                    onClick = onRefreshDirectionWiki,
                    enabled = !uiState.isRefreshingDirectionWiki,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LocalModelSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onLocalModelDownloadUrlChange: (String) -> Unit,
    onLocalPreferOnDeviceChange: (Boolean) -> Unit,
    onSaveLocalModel: () -> Unit,
    onTestLocalModel: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onDeleteLocalModel: () -> Unit,
    onRefreshLocalKnowledge: () -> Unit,
) {
    val statusHeadline = when (uiState.localModelStatus) {
        OnDeviceModelStatus.READY -> "已就绪"
        OnDeviceModelStatus.DOWNLOADING -> "下载中"
        OnDeviceModelStatus.ERROR -> if (uiState.localModelDownloadedBytes > 0L) "下载中断" else "下载失败"
        OnDeviceModelStatus.NOT_DOWNLOADED -> "未下载"
    }
    DetailScreenFrame(
        title = "本地模型",
        subtitle = "默认用 Gemma 4 E4B LiteRT 包，运行时下载，不打进安装包",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "当前状态", headline = statusHeadline)
                Text(
                    text = when {
                        uiState.localModelStatus == OnDeviceModelStatus.READY -> "模型文件已准备好。本地模型只会在你显式触发测试、编辑召回或手动维护知识层时使用。"
                        uiState.localModelStatus == OnDeviceModelStatus.DOWNLOADING -> "模型正在下载到应用私有目录，进度会自动保存，断开后可以继续下载。"
                        uiState.localModelStatus == OnDeviceModelStatus.ERROR && uiState.localModelDownloadedBytes > 0L -> uiState.localModelLastMessage.ifBlank { "下载已中断，当前进度已保留，可以继续下载。" }
                        uiState.localModelLastMessage.isNotBlank() -> uiState.localModelLastMessage
                        else -> "已经帮你填好默认直链，可以直接下载到本地。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.localModelPath.takeIf { it.isNotBlank() }?.let { path ->
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val effectiveTargetBytes = uiState.localModelDownloadTargetBytes
                    .takeIf { it > 0L }
                    ?: OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES
                if (uiState.localModelDownloadedBytes > 0L) {
                    Text(
                        text = "已下载 ${formatFileSize(uiState.localModelDownloadedBytes)} / ${formatFileSize(effectiveTargetBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                }
                if (
                    effectiveTargetBytes > 0L &&
                    (uiState.localModelStatus == OnDeviceModelStatus.DOWNLOADING || uiState.localModelDownloadedBytes > 0L) &&
                    uiState.localModelStatus != OnDeviceModelStatus.READY
                ) {
                    val progress = (uiState.localModelDownloadedBytes.toFloat() / effectiveTargetBytes.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Text(
                        text = "${formatPercentage(progress)} · 下载中断后可继续下载",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = "模型约 ${formatFileSize(OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES)}，建议至少预留 5GB 可用空间。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                )
                if (uiState.localModelStatus == OnDeviceModelStatus.ERROR) {
                    Text(
                        text = "当前实际下载地址：${OnDeviceModelSettings.normalizeDownloadUrl(uiState.localModelDownloadUrl)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "模型配置",
                description = "端侧模型只负责本地主编和知识召回。默认直链指向 Gemma 4 E4B 的官方 LiteRT 包，不会被打进 APK；填仓库页或 repo id 也会自动纠正成可下载直链。",
            ) {
                SettingsField(
                    value = uiState.localModelDownloadUrl,
                    onValueChange = onLocalModelDownloadUrlChange,
                    label = "模型仓库或下载链接",
                    secret = false,
                )
                Text(
                    text = "官方来源：${OnDeviceModelSettings.DEFAULT_MODEL_SOURCE_URL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                )
                Surface(
                    color = WhiteGlass.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("本地优先", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "打开后，显式的本地模型能力会优先走端侧；首页和 Flow 导航不会再静默拉起本地模型。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.localModelPreferOnDevice,
                            onCheckedChange = onLocalPreferOnDeviceChange,
                        )
                    }
                }
                GhostActionButton(
                    text = when {
                        uiState.isDownloadingLocalModel -> "下载中..."
                        uiState.localModelDownloadedBytes > 0L && uiState.localModelStatus != OnDeviceModelStatus.READY -> "继续下载"
                        else -> "下载模型"
                    },
                    onClick = onDownloadLocalModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isDownloadingLocalModel,
                    icon = Icons.Outlined.FileDownload,
                )
                GhostActionButton(
                    text = if (uiState.isTestingLocalModel) "测试中..." else "测试本地模型",
                    onClick = onTestLocalModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isTestingLocalModel && uiState.localModelStatus == OnDeviceModelStatus.READY,
                )
                val hasMaintenanceProgress = uiState.isRefreshingLocalKnowledge ||
                    uiState.localMaintenanceProgress > 0f ||
                    uiState.localMaintenanceStep.isNotBlank()
                if (hasMaintenanceProgress) {
                    LinearProgressIndicator(
                        progress = {
                            uiState.localMaintenanceProgress.takeIf { it > 0f } ?: 0.06f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                    Text(
                        text = uiState.localMaintenanceStep.ifBlank {
                            "正在用本地模型维护本地知识层，会读取最近材料、方向页、结论页和日志。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                }
                uiState.localMaintenanceLastStartedAt
                    .takeIf { it > 0L }
                    ?.let { startedAt ->
                        Text(
                            text = "最近开始维护 ${TimeFormatter.compact(startedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                    }
                uiState.localMaintenanceLastSucceededAt
                    .takeIf { it > 0L }
                    ?.let { succeededAt ->
                        Text(
                            text = "最近成功维护 ${TimeFormatter.compact(succeededAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                    }
                uiState.localMaintenanceLastError
                    .takeIf { it.isNotBlank() }
                    ?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                uiState.localMaintenanceLastTracePath
                    .takeIf { it.isNotBlank() }
                    ?.let { path ->
                        Text(
                            text = "维护日志导出位置：$path",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                Text(
                    text = "崩溃和维护日志目录：${uiState.localMaintenanceLogDir.ifBlank { "Android/data/com.mindflow.app/files/mindflow-logs/" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "如果本地整理闪退，请把这个目录里的 latest-crash.md 或 latest-local-maintainer-error.md 发给我。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                )
                GhostActionButton(
                    text = if (uiState.isRefreshingLocalKnowledge) "维护中..." else "立即维护知识层",
                    onClick = onRefreshLocalKnowledge,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRefreshingLocalKnowledge && uiState.localModelStatus == OnDeviceModelStatus.READY,
                    icon = Icons.Outlined.Timelapse,
                )
                ActionButton(
                    text = if (uiState.isSavingLocalModel) "保存中..." else "保存本地模型设置",
                    onClick = onSaveLocalModel,
                    enabled = !uiState.isSavingLocalModel,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )
                GhostActionButton(
                    text = if (uiState.isDeletingLocalModel) "删除中..." else "删除本地模型",
                    onClick = onDeleteLocalModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isDeletingLocalModel && uiState.localModelStatus == OnDeviceModelStatus.READY,
                )
            }
        }
    }
}

@Composable
private fun TimeBankSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onTimeBankCurrentAgeChange: (String) -> Unit,
    onTimeBankExpectedLifespanChange: (String) -> Unit,
    onTimeBankActiveDaysPerWeekChange: (String) -> Unit,
    onSaveTimeBank: () -> Unit,
) {
    val preview = uiState.timeBankPreview
    DetailScreenFrame(
        title = "时间银行",
        subtitle = "按真正能主动投入的时间来估算",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(
                    title = "当前估算",
                    headline = "可用 ${preview.remainingActiveDays} 天",
                )
                Text(
                    text = "当前 ${preview.currentAge} 岁，预期 ${preview.expectedLifespan} 岁；如果按每周 ${preview.activeDaysPerWeek} 天主动投入来算，还能认真用上的时间大约还有 ${preview.remainingActiveDays} 天。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item {
            SettingsSection(
                title = "参数设置",
                description = "把时间银行调成你的真实状态，首页会优先按可主动投入的时间来显示。",
            ) {
                SettingsField(
                    value = uiState.timeBankCurrentAge,
                    onValueChange = onTimeBankCurrentAgeChange,
                    label = "当前年龄",
                    secret = false,
                )
                SettingsField(
                    value = uiState.timeBankExpectedLifespan,
                    onValueChange = onTimeBankExpectedLifespanChange,
                    label = "预期寿命",
                    secret = false,
                )
                SettingsField(
                    value = uiState.timeBankActiveDaysPerWeek,
                    onValueChange = onTimeBankActiveDaysPerWeekChange,
                    label = "每周主动投入天数",
                    secret = false,
                )
                ActionButton(
                    text = if (uiState.isSavingTimeBank) "保存中..." else "保存时间银行",
                    onClick = onSaveTimeBank,
                    enabled = !uiState.isSavingTimeBank,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Timelapse,
                )
            }
        }
    }
}

@Composable
private fun ReminderSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onMorningBriefEnabledChange: (Boolean) -> Unit,
    onEveningReviewEnabledChange: (Boolean) -> Unit,
    onSaveReminder: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    DetailScreenFrame(
        title = "AI 提醒",
        subtitle = "今日聚焦 / 晚间回看",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "提醒节奏", headline = if (uiState.morningBriefEnabled || uiState.eveningReviewEnabled) "已开启" else "未开启")
                Text(
                    text = "晨间在 08:30 帮你进入今日聚焦，晚间在 21:30 帮你做一次轻量回看。先用固定时间，后面再开放自定义。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(
                title = "提醒开关",
                description = "提醒频率保持很低，只在真正值得推进、重连或回看时唤起你。",
            ) {
                ReminderSwitchRow(
                    title = "晨间聚焦",
                    description = "08:30 推送一条今日聚焦、下一步和方向提醒",
                    checked = uiState.morningBriefEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRequestNotificationPermission { onMorningBriefEnabledChange(true) }
                        } else {
                            onMorningBriefEnabledChange(false)
                        }
                    },
                )
                ReminderSwitchRow(
                    title = "晚间回看",
                    description = "21:30 提醒你收拢当天记录并留下一条回看",
                    checked = uiState.eveningReviewEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRequestNotificationPermission { onEveningReviewEnabledChange(true) }
                        } else {
                            onEveningReviewEnabledChange(false)
                        }
                    },
                )
                ActionButton(
                    text = if (uiState.isSavingReminder) "保存中..." else "保存提醒设置",
                    onClick = onSaveReminder,
                    enabled = !uiState.isSavingReminder,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )
            }
        }
    }
}

@Composable
private fun ReminderSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun CloudBackupScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onCloudBaseUrlChange: (String) -> Unit,
    onCloudUsernameChange: (String) -> Unit,
    onCloudPasswordChange: (String) -> Unit,
    onCloudRemoteDirChange: (String) -> Unit,
    onCloudAutoBackupChange: (Boolean) -> Unit,
    onSaveCloud: () -> Unit,
    onClearCloud: () -> Unit,
    onBackupToCloud: () -> Unit,
    onRestoreRequest: () -> Unit,
) {
    DetailScreenFrame(
        title = "云备份",
        subtitle = "坚果云 WebDAV",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "当前状态", headline = if (uiState.cloudIsConfigured) "已连接" else "未配置")
                GridTwo {
                    MetricTile(
                        label = "备份方式",
                        value = "WebDAV",
                        modifier = Modifier.weight(1f),
                        accent = if (uiState.cloudIsConfigured) Accent else MaterialTheme.colorScheme.onSurface,
                    )
                    MetricTile(
                        label = "最近备份",
                        value = if (uiState.cloudLastBackupAt > 0L) TimeFormatter.compact(uiState.cloudLastBackupAt) else "尚未备份",
                        modifier = Modifier.weight(1f),
                        accent = AccentBlue,
                    )
                }
                if (uiState.cloudLastBackupError.isNotBlank()) {
                    Text(
                        text = "上次失败：${uiState.cloudLastBackupError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "WebDAV 配置",
                description = "用户名填坚果云邮箱，密码填应用密码。",
            ) {
                SettingsField(
                    value = uiState.cloudBaseUrl,
                    onValueChange = onCloudBaseUrlChange,
                    label = "WebDAV 地址",
                    secret = false,
                )
                SettingsField(
                    value = uiState.cloudUsername,
                    onValueChange = onCloudUsernameChange,
                    label = "用户名",
                    secret = false,
                )
                SettingsField(
                    value = uiState.cloudPassword,
                    onValueChange = onCloudPasswordChange,
                    label = "应用密码",
                    secret = true,
                )
                SettingsField(
                    value = uiState.cloudRemoteDir,
                    onValueChange = onCloudRemoteDirChange,
                    label = "远端目录",
                    secret = false,
                )
                Surface(
                    color = WhiteGlass.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动备份",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "退到后台时静默同步，每天最多一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.cloudAutoBackupEnabled,
                            onCheckedChange = onCloudAutoBackupChange,
                        )
                    }
                }
                ActionButton(
                    text = if (uiState.isSavingCloud) "保存中..." else "保存云备份配置",
                    onClick = onSaveCloud,
                    enabled = !uiState.isSavingCloud,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )
                GhostActionButton(
                    text = "清空云配置",
                    onClick = onClearCloud,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            PanelCard {
                SectionHeader(title = "云端操作")
                ActionButton(
                    text = if (uiState.isBackingUpCloud) "备份中..." else "立即备份到云",
                    onClick = onBackupToCloud,
                    enabled = !uiState.isBackingUpCloud,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.CloudUpload,
                )
                ActionButton(
                    text = if (uiState.isRestoringCloud) "恢复中..." else "从云端恢复",
                    onClick = onRestoreRequest,
                    enabled = !uiState.isRestoringCloud,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.CloudDone,
                )
            }
        }
    }
}

@Composable
private fun AiSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onAiEnabledChange: (Boolean) -> Unit,
    onAiProviderPresetChange: (AiProviderPreset) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSaveAi: () -> Unit,
    onTestAi: () -> Unit,
    onClearAi: () -> Unit,
) {
    val currentFingerprint = AiSettings.fingerprint(
        apiKey = uiState.apiKey,
        baseUrl = uiState.baseUrl,
        model = uiState.model,
    )
    val isVerifiedForCurrentConfig = uiState.aiVerifiedFingerprint == currentFingerprint
    val verificationHeadline = when {
        !uiState.aiEnabled -> "已关闭"
        !uiState.isConfigured -> "本地规则"
        isVerifiedForCurrentConfig && uiState.aiLastVerifiedSuccess -> "已连通"
        isVerifiedForCurrentConfig && !uiState.aiLastVerifiedSuccess -> "检查失败"
        else -> "待验证"
    }

    DetailScreenFrame(
        title = "AI 能力",
        subtitle = if (uiState.aiEnabled) "云端升级 / 编辑整理 / 方向判断" else "当前只用本地与规则",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "当前状态", headline = verificationHeadline)
                Text(
                    text = if (!uiState.aiEnabled) {
                        "AI 已关闭。"
                    } else if (isVerifiedForCurrentConfig && uiState.aiLastVerifiedSuccess) {
                        "最近验证 ${TimeFormatter.compact(uiState.aiLastVerifiedAt)}"
                    } else if (isVerifiedForCurrentConfig && uiState.aiLastVerificationMessage.isNotBlank()) {
                        uiState.aiLastVerificationMessage
                    } else if (uiState.isConfigured) {
                        "当前配置还没验证。"
                    } else {
                        "补一个 ${uiState.aiProviderPreset.label} API Key 就能用。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(
                title = "模型配置",
                description = "这里的模型只在显式云端升级动作里使用，不会静默替本地维护跑云端；所有结果仍会回写到本地知识层。",
            ) {
                ProviderPresetSelector(
                    selectedPreset = uiState.aiProviderPreset,
                    onSelect = onAiProviderPresetChange,
                )
                Surface(
                    color = WhiteGlass.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用 AI", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (uiState.aiEnabled) "会用于显式的云端升级、编辑整理和方向判断；不会静默上传本地维护任务" else "关闭后只用本地模型和规则整理",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.aiEnabled,
                            onCheckedChange = onAiEnabledChange,
                        )
                    }
                }
                GridTwo {
                    MetricTile(
                        label = "今日请求",
                        value = uiState.aiRequestsToday.toString(),
                        modifier = Modifier.weight(1f),
                        accent = AccentBlue,
                    )
                    MetricTile(
                        label = "今日成功",
                        value = uiState.aiSuccessesToday.toString(),
                        modifier = Modifier.weight(1f),
                        accent = Accent,
                    )
                }
                MetricTile(
                    label = "今日 Tokens",
                    value = uiState.aiTokensToday.toString(),
                    accent = MaterialTheme.colorScheme.onSurface,
                )
                SettingsField(
                    value = uiState.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = "Base URL",
                    secret = false,
                    enabled = uiState.aiProviderPreset == AiProviderPreset.CUSTOM,
                )
                SettingsField(value = uiState.model, onValueChange = onModelChange, label = "Model", secret = false)
                SettingsField(value = uiState.apiKey, onValueChange = onApiKeyChange, label = "API Key", secret = true)
                GhostActionButton(
                    text = if (uiState.isTestingAi) "测试中..." else "测试 AI 连接",
                    onClick = onTestAi,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isTestingAi && uiState.aiEnabled,
                )
                ActionButton(
                    text = if (uiState.isSavingAi) "保存中..." else "保存 AI 设置",
                    onClick = onSaveAi,
                    enabled = !uiState.isSavingAi,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Save,
                )
                GhostActionButton(
                    text = "清空 AI 设置",
                    onClick = onClearAi,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProviderPresetSelector(
    selectedPreset: AiProviderPreset,
    onSelect: (AiProviderPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GridTwo {
            AiProviderPreset.entries.take(2).forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelect(preset) },
                    label = { Text(preset.label) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.16f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
        FilterChip(
            selected = selectedPreset == AiProviderPreset.CUSTOM,
            onClick = { onSelect(AiProviderPreset.CUSTOM) },
            label = { Text(AiProviderPreset.CUSTOM.label) },
            modifier = Modifier.fillMaxWidth(),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentBlue.copy(alpha = 0.16f),
                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
private fun DetailScreenFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconPillButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                top = 2.dp,
                end = ScreenHorizontalPadding,
                bottom = BottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    summary: String,
    headline: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsStatusChip(
                text = headline,
                accent = accent,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

private fun formatPercentage(progress: Float): String =
    String.format("%.0f%%", (progress * 100f).coerceIn(0f, 100f))

@Composable
private fun SettingsStatusChip(
    text: String,
    accent: Color,
) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean,
    enabled: Boolean = true,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhiteGlass.copy(alpha = 0.92f),
                unfocusedContainerColor = WhiteGlass.copy(alpha = 0.92f),
            ),
        )
    }
}
