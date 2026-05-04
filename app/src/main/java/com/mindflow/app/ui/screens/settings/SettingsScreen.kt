package com.mindflow.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.ai.AiExecutionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.data.model.AiProviderPreset
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.model.CloudBackupSettings
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
import com.mindflow.app.ui.theme.AccentDanger
import com.mindflow.app.ui.theme.AccentLavender
import com.mindflow.app.ui.theme.AccentSuccess
import com.mindflow.app.ui.theme.AccentTeal
import com.mindflow.app.ui.theme.AccentWarn
import com.mindflow.app.ui.theme.PanelBlue
import com.mindflow.app.ui.theme.PanelSoft
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
    APPEARANCE,
    ABOUT,
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
        onAiExecutionModeChange = viewModel::onAiExecutionModeChange,
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
    onAiExecutionModeChange: (AiExecutionMode) -> Unit,
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
                onOpenAppearance = { destination = SettingsDestination.APPEARANCE },
                onOpenAbout = { destination = SettingsDestination.ABOUT },
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
                onAiExecutionModeChange = onAiExecutionModeChange,
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
            SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
                onBack = { destination = SettingsDestination.HOME },
            )
            SettingsDestination.ABOUT -> AboutSettingsScreen(
                onBack = { destination = SettingsDestination.HOME },
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
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "个性化配置，掌控你的 AI 思维系统",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
            }

            item {
                SettingsProfileCard()
            }

            item {
                SettingsMenuGroup {
                    SettingsMenuRow(
                        icon = Icons.Outlined.AutoAwesome,
                        iconColor = AccentBlue,
                        title = "AI 模型与自动化",
                        summary = buildLocalModelSummary(uiState),
                        status = buildLocalModelHeadline(uiState),
                        onClick = onOpenLocalModel,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.SettingsSuggest,
                        iconColor = AccentLavender,
                        title = "云端 AI",
                        summary = if (uiState.isConfigured) uiState.model.ifBlank { "已配置" } else "未配置 API Key",
                        status = if (uiState.isConfigured && uiState.aiEnabled) "可用" else "未启用",
                        onClick = onOpenAi,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Bolt,
                        iconColor = AccentSuccess,
                        title = "自动化",
                        summary = when {
                            uiState.morningBriefEnabled && uiState.eveningReviewEnabled -> "晨间聚焦、晚间回看已开启"
                            uiState.morningBriefEnabled || uiState.eveningReviewEnabled -> "部分提醒已开启"
                            else -> "任务识别、提醒与自动处理规则"
                        },
                        status = if (uiState.morningBriefEnabled || uiState.eveningReviewEnabled) "已开启" else null,
                        onClick = onOpenReminder,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Shield,
                        iconColor = AccentTeal,
                        title = "隐私与数据",
                        summary = buildPrivacyDataSummary(uiState),
                        status = if (uiState.cloudAutoBackupEnabled) "已开启" else null,
                        onClick = onOpenCloud,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.NotificationsNone,
                        iconColor = AccentWarn,
                        title = "通知",
                        summary = "提醒方式与通知偏好",
                        status = null,
                        onClick = onOpenReminder,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Hub,
                        iconColor = AccentLavender,
                        title = "图谱",
                        summary = if (uiState.directionWikiDirectionCount > 0) {
                            "${uiState.directionWikiDirectionCount} 个方向正在沉淀"
                        } else {
                            "图谱视图、关系与显示设置"
                        },
                        status = if (uiState.directionWikiLastRefreshedAt > 0L) TimeFormatter.compact(uiState.directionWikiLastRefreshedAt) else null,
                        onClick = onOpenDirectionWiki,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Timelapse,
                        iconColor = Accent,
                        title = "时间银行",
                        summary = "还可主动投入 ${uiState.timeBankPreview.remainingActiveDays} 天",
                        status = "可用时间",
                        onClick = onOpenTimeBank,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Palette,
                        iconColor = AccentBlue,
                        title = "外观",
                        summary = "主题、颜色与显示偏好",
                        status = "浅色",
                        onClick = onOpenAppearance,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        icon = Icons.Outlined.Info,
                        iconColor = TextSoft,
                        title = "关于",
                        summary = "版本信息、帮助与反馈",
                        status = null,
                        onClick = onOpenAbout,
                    )
                }
            }

            item {
                SettingsExportImportCard(
                    uiState = uiState,
                    onExport = onExport,
                    onImport = onImport,
                )
            }
        }
    }
}

@Composable
private fun SettingsProfileCard() {
    Surface(
        color = WhiteGlass.copy(alpha = 0.98f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PanelBlue),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PanelBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "MindFlow 用户",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "专注思考，持续进化",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.98f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PanelBlue.copy(alpha = 0.72f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(PanelSoft),
    )
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    summary: String,
    status: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!status.isNullOrBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = iconColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TextSoft.copy(alpha = 0.68f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsDataRow(
    icon: ImageVector,
    title: String,
    summary: String,
    status: String?,
    accent: Color,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PanelBlue.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingsExportImportCard(
    uiState: SettingsUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.95f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PanelBlue.copy(alpha = 0.72f)),
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

private fun buildLocalModelSummary(uiState: SettingsUiState): String =
    when (uiState.localModelStatus) {
        OnDeviceModelStatus.READY -> "${uiState.localModelLabel} · ${formatFileSize(uiState.localModelDownloadedBytes)}"
        OnDeviceModelStatus.DOWNLOADING -> "模型下载中"
        OnDeviceModelStatus.ERROR -> uiState.localModelLastMessage.ifBlank {
            if (uiState.localModelDownloadedBytes > 0L) "下载中断，可继续" else "模型准备失败"
        }
        OnDeviceModelStatus.NOT_DOWNLOADED -> "模型选择、运行位置与任务建议"
    }

private fun buildLocalModelHeadline(uiState: SettingsUiState): String =
    when {
        uiState.localModelStatus != OnDeviceModelStatus.READY -> "未就绪"
        uiState.aiExecutionMode == AiExecutionMode.AUTOMATIC -> "本地优先"
        uiState.aiExecutionMode == AiExecutionMode.ON_DEVICE_ONLY -> "仅本地"
        else -> "云端"
    }

private fun buildPrivacyDataSummary(uiState: SettingsUiState): String =
    when {
        uiState.cloudIsConfigured && uiState.cloudLastBackupAt > 0L ->
            "最近备份 ${TimeFormatter.compact(uiState.cloudLastBackupAt)}"
        uiState.cloudIsConfigured -> "备份与同步已配置"
        else -> "本地优先、备份与数据管理"
    }

@Composable
private fun DirectionWikiSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onRefreshDirectionWiki: () -> Unit,
) {
    DetailScreenFrame(
        title = "图谱",
        subtitle = "图谱视图、关系与显示设置",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(
                    title = "当前结构",
                    headline = if (uiState.directionWikiDirectionCount > 0) "${uiState.directionWikiDirectionCount} 个方向" else "尚未生成",
                )
                Text(
                    text = "图谱用于展示概念之间的关系、中心节点、孤立节点和近期增长。",
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
                    text = if (uiState.isRefreshingDirectionWiki) "更新中..." else "更新图谱结构",
                    onClick = onRefreshDirectionWiki,
                    enabled = !uiState.isRefreshingDirectionWiki,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsScreen(
    onBack: () -> Unit,
) {
    DetailScreenFrame(
        title = "外观",
        subtitle = "主题、颜色与显示偏好",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "当前主题", headline = "浅色")
                Text(
                    text = "当前先跟随参考设计保持清爽浅色风格。深色模式、紧凑密度和字号偏好后续可以在这里统一管理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsScreen(
    onBack: () -> Unit,
) {
    DetailScreenFrame(
        title = "关于 MindFlow",
        subtitle = "版本信息、帮助与反馈",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "MindFlow", headline = "Idea incubator")
                Text(
                    text = "MindFlow 用来捕捉易碎想法、发现反复出现的线索，并把成熟内容沉淀成可复用资产。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onAiExecutionModeChange: (AiExecutionMode) -> Unit,
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
        title = "AI 与自动化",
        subtitle = "模型、运行位置与自动处理规则",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                SectionHeader(title = "当前模型", headline = statusHeadline)
                Text(
                    text = when {
                        uiState.localModelStatus == OnDeviceModelStatus.READY -> "${uiState.localModelLabel} 已准备好，可用于本地摘要、标题、转写和图谱整理。"
                        uiState.localModelStatus == OnDeviceModelStatus.DOWNLOADING -> "模型正在下载到本地，断开后可以继续。"
                        uiState.localModelStatus == OnDeviceModelStatus.ERROR && uiState.localModelDownloadedBytes > 0L -> uiState.localModelLastMessage.ifBlank { "下载已中断，当前进度已保留，可以继续下载。" }
                        uiState.localModelLastMessage.isNotBlank() -> uiState.localModelLastMessage
                        else -> "下载 Gemma 4 后，优先在设备本地完成 AI 处理。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            }
        }

        item {
            SettingsSection(
                title = "模型运行位置",
                description = "默认本地优先。云端只在你显式选择相关能力时使用。",
            ) {
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
                            Text("运行位置", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "本地优先更安全；仅本地和云端模式用于排查或明确切换。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            GridTwo {
                                FilterChip(
                                    selected = uiState.aiExecutionMode == AiExecutionMode.AUTOMATIC,
                                    onClick = { onAiExecutionModeChange(AiExecutionMode.AUTOMATIC) },
                                    label = { Text("本地优先") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentBlue.copy(alpha = 0.16f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                                FilterChip(
                                    selected = uiState.aiExecutionMode == AiExecutionMode.ON_DEVICE_ONLY,
                                    onClick = { onAiExecutionModeChange(AiExecutionMode.ON_DEVICE_ONLY) },
                                    label = { Text("仅本地") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentBlue.copy(alpha = 0.16f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                            FilterChip(
                                selected = uiState.aiExecutionMode == AiExecutionMode.CLOUD_ONLY,
                                onClick = { onAiExecutionModeChange(AiExecutionMode.CLOUD_ONLY) },
                                label = { Text("云端") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
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
                Text(
                    text = "高级设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
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
        title = "自动化与通知",
        subtitle = "任务识别、提醒与处理节奏",
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
        title = "隐私与数据",
        subtitle = "本地优先、备份与同步",
        onBack = onBack,
    ) {
        item {
            PanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "本地优先，隐私为先",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "数据始终保留在你手中；同步与导出由你明确触发。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
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
                title = "备份与同步",
                description = "自动备份默认低频执行；手动备份和恢复需要你主动确认。",
            ) {
                SettingsDataRow(
                    icon = Icons.Outlined.Sync,
                    title = "本地备份与同步",
                    summary = if (uiState.cloudIsConfigured) "WebDAV 已配置" else "未配置同步目录",
                    status = if (uiState.cloudAutoBackupEnabled) "已开启" else "未开启",
                    accent = AccentTeal,
                )
                SettingsDataRow(
                    icon = Icons.Outlined.FolderOpen,
                    title = "备份目录",
                    summary = uiState.cloudRemoteDir.ifBlank { CloudBackupSettings.DEFAULT_REMOTE_DIR },
                    status = null,
                    accent = AccentBlue,
                )
                SettingsDataRow(
                    icon = Icons.Outlined.Storage,
                    title = "最近备份",
                    summary = if (uiState.cloudLastBackupAt > 0L) TimeFormatter.compact(uiState.cloudLastBackupAt) else "尚未备份",
                    status = null,
                    accent = AccentLavender,
                )
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
        title = "云端 AI",
        subtitle = if (uiState.aiEnabled) "显式云端升级、编辑整理与连接验证" else "当前只用本地模型",
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
