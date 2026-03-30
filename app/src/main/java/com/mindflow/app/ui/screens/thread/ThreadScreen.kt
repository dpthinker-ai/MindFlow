package com.mindflow.app.ui.screens.thread

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.MetricTile
import com.mindflow.app.ui.components.NeonProgress
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.TextSoft
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadRoute(
    noteRepository: NoteRepository,
    aiSettingsRepository: AiSettingsRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    aiServiceClient: AiServiceClient,
    threadKey: String,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateThreadNote: (CaptureSeed) -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel(
        key = "thread-$threadKey",
        factory = ThreadViewModel.factory(
            noteRepository = noteRepository,
            aiSettingsRepository = aiSettingsRepository,
            threadPreferencesRepository = threadPreferencesRepository,
            aiServiceClient = aiServiceClient,
            threadKey = threadKey,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shareCardGenerator = remember(context) { NoteShareCardGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingShareNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingDeletedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ThreadEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    pendingShareNote?.let { note ->
        ShareStyleDialog(
            onDismiss = { pendingShareNote = null },
            onLight = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.LIGHT) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
            onDark = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.DARK) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
        )
    }

    ThreadScreen(
        uiState = uiState,
        hiddenNoteIds = pendingDeletedIds,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onToggleFollow = viewModel::toggleFollow,
        onPromoteFocus = viewModel::promoteFocusNote,
        onOpenResearchQuery = { query ->
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            uriHandler.openUri("https://www.baidu.com/s?wd=$encoded")
        },
        onCreateThreadNote = {
            val topic = uiState.focusNote?.topic?.takeIf { it.isNotBlank() }
                ?: uiState.title.removePrefix("#").trim()
            val seedContent = buildString {
                append("围绕「${uiState.title}」继续补一条记录：")
                uiState.focusNote?.topic?.takeIf { it.isNotBlank() }?.let { focusTopic ->
                    append("\n- 接着看：$focusTopic")
                }
                append("\n- 这次新增的观察 / 判断 / 动作：")
            }
            onCreateThreadNote(
                CaptureSeed(
                    initialTopic = topic,
                    initialContent = seedContent,
                    initialFolderKey = threadKey
                        .takeIf { it.startsWith("folder:") }
                        ?.removePrefix("folder:")
                        ?.trim()
                        ?.ifBlank { null },
                    initialTags = threadKey
                        .takeIf { it.startsWith("tag:") }
                        ?.removePrefix("tag:")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::listOf)
                        .orEmpty(),
                ),
            )
        },
        onCaptureResearchNote = {
            val topic = "${uiState.title.removePrefix("#").trim()} · 研究收获"
            val seedContent = buildString {
                appendLine("围绕「${uiState.title}」记一条研究收获：")
                uiState.researchOutsideAngle.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 外部线索：$it")
                }
                uiState.researchGap.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 机会缺口：$it")
                }
                if (uiState.researchQueries.isNotEmpty()) {
                    appendLine("- 继续查：")
                    uiState.researchQueries.forEach { query ->
                        appendLine("  - $query")
                    }
                }
                appendLine("- 我查到的内容：")
                appendLine("- 这对当前方向的判断：")
                appendLine("- 下一步验证：")
            }
            onCreateThreadNote(
                CaptureSeed(
                    initialTopic = topic,
                    initialContent = seedContent,
                    initialFolderKey = threadKey
                        .takeIf { it.startsWith("folder:") }
                        ?.removePrefix("folder:")
                        ?.trim()
                        ?.ifBlank { null },
                    initialTags = threadKey
                        .takeIf { it.startsWith("tag:") }
                        ?.removePrefix("tag:")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::listOf)
                        .orEmpty(),
                ),
            )
        },
        onCaptureWeeklyReviewNote = {
            val topic = "${uiState.title.removePrefix("#").trim()} · 本周推进"
            val seedContent = buildString {
                appendLine("围绕「${uiState.title}」记一条本周推进：")
                uiState.weeklyStatsLine.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 本周概览：$it")
                }
                if (uiState.weeklyLines.isNotEmpty()) {
                    appendLine("- 本周判断：")
                    uiState.weeklyLines.forEach { line ->
                        appendLine("  - $line")
                    }
                }
                appendLine("- 这周实际发生了什么：")
                appendLine("- 关键变化：")
                appendLine("- 下周先做什么：")
            }
            onCreateThreadNote(
                CaptureSeed(
                    initialTopic = topic,
                    initialContent = seedContent,
                    initialFolderKey = threadKey
                        .takeIf { it.startsWith("folder:") }
                        ?.removePrefix("folder:")
                        ?.trim()
                        ?.ifBlank { null },
                    initialTags = threadKey
                        .takeIf { it.startsWith("tag:") }
                        ?.removePrefix("tag:")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::listOf)
                        .orEmpty(),
                ),
            )
        },
        onCaptureInsightNote = {
            val topic = "${uiState.title.removePrefix("#").trim()} · 当前判断"
            val seedContent = buildString {
                appendLine("围绕「${uiState.title}」沉淀一条当前判断：")
                uiState.threadSummary.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 当前判断：$it")
                }
                uiState.threadBlocker.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 当前卡点：$it")
                }
                uiState.threadNextStep.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 下一步：$it")
                }
                appendLine("- 我现在更明确的判断：")
                appendLine("- 为什么这样判断：")
                appendLine("- 接下来要继续验证什么：")
            }
            onCreateThreadNote(
                CaptureSeed(
                    initialTopic = topic,
                    initialContent = seedContent,
                    initialFolderKey = threadKey
                        .takeIf { it.startsWith("folder:") }
                        ?.removePrefix("folder:")
                        ?.trim()
                        ?.ifBlank { null },
                    initialTags = threadKey
                        .takeIf { it.startsWith("tag:") }
                        ?.removePrefix("tag:")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::listOf)
                        .orEmpty(),
                ),
            )
        },
        onArchiveNote = viewModel::archiveNote,
        onDeleteNote = { note ->
            scope.launch {
                pendingDeletedIds = pendingDeletedIds + note.id
                val result = snackbarHostState.showSnackbar(
                    message = "已移除「${note.topic.ifBlank { "未命名想法" }}」",
                    actionLabel = "撤销",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    pendingDeletedIds = pendingDeletedIds - note.id
                } else {
                    viewModel.deleteNote(note.id)
                }
            }
        },
        onShareNote = { pendingShareNote = it },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThreadScreen(
    uiState: ThreadUiState,
    hiddenNoteIds: Set<Long>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onToggleFollow: () -> Unit,
    onPromoteFocus: () -> Unit,
    onOpenResearchQuery: (String) -> Unit,
    onCreateThreadNote: () -> Unit,
    onCaptureResearchNote: () -> Unit,
    onCaptureWeeklyReviewNote: () -> Unit,
    onCaptureInsightNote: () -> Unit,
    onArchiveNote: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    val progress = if (uiState.totalCount == 0) 0f else uiState.doneCount.toFloat() / uiState.totalCount.toFloat()
    val percent = (progress * 100).toInt()
    val visibleNotes = remember(uiState.notes, hiddenNoteIds) {
        uiState.notes.filterNot { it.id in hiddenNoteIds }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconPillButton(
                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                onClick = onBack,
                                accent = AccentBlue,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = uiState.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${uiState.totalCount} 条记录 · 持续推进这个方向",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            com.mindflow.app.ui.components.GhostActionButton(
                                text = if (uiState.isFollowed) "已关注" else "关注方向",
                                onClick = onToggleFollow,
                            )
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "当前进展",
                            headline = "$percent% 已实现",
                        )
                        NeonProgress(
                            progress = progress,
                            startColor = AccentBlue,
                            endColor = AccentBlue,
                        )
                        GridTwo {
                            MetricTile(
                                label = "想法",
                                value = uiState.ideaCount.toString(),
                                modifier = Modifier.weight(1f),
                                accent = noteStatusAccent(com.mindflow.app.data.model.NoteStatus.IDEA),
                            )
                            MetricTile(
                                label = "进行中",
                                value = uiState.inProgressCount.toString(),
                                modifier = Modifier.weight(1f),
                                accent = noteStatusAccent(com.mindflow.app.data.model.NoteStatus.IN_PROGRESS),
                            )
                        }
                        MetricTile(
                            label = "已实现",
                            value = uiState.doneCount.toString(),
                            accent = noteStatusAccent(com.mindflow.app.data.model.NoteStatus.DONE),
                        )
                    }
                }

                if (uiState.weeklyStatsLine.isNotBlank() || uiState.weeklyLines.isNotEmpty()) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "本周在线程里",
                                headline = uiState.weeklyStatsLine.ifBlank { null },
                            )
                            uiState.weeklyLines.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            GhostActionButton(
                                text = "记下本周推进",
                                onClick = onCaptureWeeklyReviewNote,
                            )
                        }
                    }
                }

                if (
                    uiState.researchOutsideAngle.isNotBlank() ||
                    uiState.researchGap.isNotBlank() ||
                    uiState.researchQueries.isNotEmpty()
                ) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "外部研究",
                                headline = if (uiState.researchSource == com.mindflow.app.data.brief.DailyBriefSource.AI) "AI 研究" else "规则整理",
                            )
                            if (uiState.researchOutsideAngle.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "外部线索",
                                    text = uiState.researchOutsideAngle,
                                )
                            }
                            if (uiState.researchGap.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "机会缺口",
                                    text = uiState.researchGap,
                                )
                            }
                            if (uiState.researchQueries.isNotEmpty()) {
                                Text(
                                    text = "可直接去查",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.researchQueries.forEachIndexed { index, query ->
                                        Surface(
                                            shape = com.mindflow.app.ui.components.CardShape,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, com.mindflow.app.ui.theme.BorderSoft),
                                            color = com.mindflow.app.ui.theme.WhiteGlass.copy(alpha = 0.84f),
                                            modifier = Modifier.clickable { onOpenResearchQuery(query) },
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = if (index == 0) "中文检索" else "技术检索",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSoft,
                                                )
                                                Text(
                                                    text = query,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            GhostActionButton(
                                text = "记下研究收获",
                                onClick = onCaptureResearchNote,
                            )
                        }
                    }
                }

                if (uiState.focusNote != null) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "当前焦点",
                                headline = uiState.focusNote.status.label,
                            )
                            Text(
                                text = uiState.focusNote.topic.ifBlank { "未命名记录" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (uiState.focusReason.isNotBlank()) {
                                Text(
                                    text = uiState.focusReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            if (uiState.threadNextStep.isNotBlank()) {
                                Text(
                                    text = "这一条先这样推进",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                Text(
                                    text = uiState.threadNextStep,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GhostActionButton(
                                    text = "打开记录",
                                    onClick = { onOpenNote(uiState.focusNote.id) },
                                    modifier = Modifier.weight(1f),
                                )
                                if (uiState.focusNote.status == com.mindflow.app.data.model.NoteStatus.IDEA) {
                                    ActionButton(
                                        text = "开始推进",
                                        onClick = onPromoteFocus,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "当前判断",
                            headline = if (uiState.insightSourceLabel.isNotBlank()) uiState.insightSourceLabel else null,
                        )
                        Text(
                            text = uiState.threadSummary.ifBlank { "这条方向正在形成，还需要更多真实记录来稳定主线。" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (uiState.threadBlocker.isNotBlank()) {
                            Text(
                                text = "卡点",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = uiState.threadBlocker,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (uiState.threadNextStep.isNotBlank()) {
                            Text(
                                text = "下一步",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = uiState.threadNextStep,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        GhostActionButton(
                            text = "沉淀当前判断",
                            onClick = onCaptureInsightNote,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SectionHeader(
                                title = "记录",
                                headline = "${visibleNotes.size} 条",
                            )
                        }
                        GhostActionButton(
                            text = "补一条记录",
                            onClick = onCreateThreadNote,
                        )
                    }
                }

                if (visibleNotes.isEmpty()) {
                    item {
                        EmptyState(
                            title = "这个方向还没有内容",
                            description = "等更多记录被串进来，这里就会慢慢形成稳定脉络。",
                        )
                    }
                } else {
                    items(visibleNotes, key = { it.id }) { note ->
                        SwipeRevealNoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onToggleArchive = { onArchiveNote(note.id) },
                            onShare = { onShareNote(note) },
                            onDelete = { onDeleteNote(note) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 18.dp),
        )
    }
}

@Composable
private fun ResearchInsightLine(
    label: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSoft,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
