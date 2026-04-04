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
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
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
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextSoft
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadRoute(
    noteRepository: NoteRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    threadExecutionPlanner: ThreadExecutionPlanner,
    externalResearchPlanner: ExternalResearchPlanner,
    threadKey: String,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateThreadNote: (CaptureSeed) -> Unit,
) {
    val viewModel: ThreadViewModel = viewModel(
        key = "thread-$threadKey",
        factory = ThreadViewModel.factory(
            noteRepository = noteRepository,
            threadPreferencesRepository = threadPreferencesRepository,
            threadExecutionPlanner = threadExecutionPlanner,
            externalResearchPlanner = externalResearchPlanner,
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
        onCaptureResearchActionNote = {
            val topic = "${uiState.title.removePrefix("#").trim()} · 研究动作"
            val seedContent = buildString {
                appendLine("围绕「${uiState.title}」记下一条研究转行动：")
                uiState.researchExternalHypothesis.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 当前判断：$it")
                }
                uiState.validationStep.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 先验证这一步：$it")
                }
                uiState.postValidationAction.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 如果成立，下一步：$it")
                }
                appendLine("- 我准备怎么验证：")
                appendLine("- 验证结果要看什么：")
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
        onCaptureTopValidationLoopNote = {
            uiState.researchClusters.firstOrNull()?.let { cluster ->
                val topic = "${uiState.title.removePrefix("#").trim()} · 验证循环"
                val seedContent = buildString {
                    appendLine("围绕「${uiState.title}」记下当前最值得验证的一组研究：")
                    appendLine("- 研究主线：${cluster.label}")
                    appendLine("- 当前判断：${cluster.summary}")
                    cluster.followUpReason.takeIf { it.isNotBlank() }?.let {
                        appendLine("- 为什么现在做：$it")
                    }
                    cluster.validationStep.takeIf { it.isNotBlank() }?.let {
                        appendLine("- 先验证：$it")
                    }
                    cluster.executionPrompt.takeIf { it.isNotBlank() }?.let {
                        appendLine("- 如果成立，下一步：$it")
                    }
                    appendLine("- 我准备怎么验证：")
                    appendLine("- 看什么结果算成立：")
                    appendLine("- 这次新的判断：")
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
            }
        },
        onCaptureResearchClusterNote = {
            val topic = "${uiState.title.removePrefix("#").trim()} · 研究脉络"
            val seedContent = buildString {
                appendLine("围绕「${uiState.title}」整理当前研究脉络：")
                if (uiState.researchClusters.isNotEmpty()) {
                    appendLine("- 当前聚合出来的研究主线：")
                    uiState.researchClusters.forEach { cluster ->
                        appendLine("  - ${cluster.label}：${cluster.summary}")
                        cluster.followUpReason
                            .takeIf { it.isNotBlank() }
                            ?.let { reason ->
                                appendLine("    - 为什么现在做：$reason")
                            }
                        cluster.validationStep
                            .takeIf { it.isNotBlank() }
                            ?.let { validation ->
                                appendLine("    - 先验证：$validation")
                            }
                        cluster.executionPrompt
                            .takeIf { it.isNotBlank() }
                            ?.let { execution ->
                                appendLine("    - 如果成立，下一步：$execution")
                            }
                    }
                }
                appendLine("- 我现在更稳定的判断：")
                appendLine("- 这几条研究之间的共同点：")
                appendLine("- 接下来优先验证：")
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
    onCaptureResearchActionNote: () -> Unit,
    onCaptureTopValidationLoopNote: () -> Unit,
    onCaptureResearchClusterNote: () -> Unit,
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

                item {
                    PanelCard {
                        SectionHeader(
                            title = "当前判断",
                            headline = uiState.stage.label,
                        )
                        Text(
                            text = "${uiState.stage.label} · ${uiState.dominantHorizon.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentBlue,
                        )
                        uiState.insightSourceLabel
                            .takeIf { it.isNotBlank() }
                            ?.let { sourceLabel ->
                                Text(
                                    text = threadInsightSourceText(sourceLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sourceLabel == "AI") AccentBlue else TextSoft,
                                )
                            }
                        if (uiState.stageHistory.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.stageHistory.forEach { entry ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                        shape = MaterialTheme.shapes.small,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft.copy(alpha = 0.7f)),
                                    ) {
                                        Text(
                                            text = "${entry.label} · ${entry.stage.label}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSoft,
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.rhythmLine.isNotBlank()) {
                            Text(
                                text = uiState.rhythmLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                            )
                        }
                        Text(
                            text = uiState.threadSummary.ifBlank { "这条方向正在形成，还需要更多真实记录来稳定主线。" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (uiState.stageReason.isNotBlank()) {
                            Text(
                                text = uiState.stageReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                            )
                        }
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
                        if (uiState.weeklyStatsLine.isNotBlank() || uiState.weeklyLines.isNotEmpty()) {
                            Text(
                                text = "本周回看",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.weeklyStatsLine
                                .takeIf { it.isNotBlank() }
                                ?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSoft,
                                    )
                                }
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
                        GhostActionButton(
                            text = "沉淀当前判断",
                            onClick = onCaptureInsightNote,
                        )
                    }
                }

                if (
                    uiState.researchOutsideAngle.isNotBlank() ||
                    uiState.researchGap.isNotBlank() ||
                    uiState.researchContrarianQuestion.isNotBlank() ||
                    uiState.researchExternalHypothesis.isNotBlank() ||
                    uiState.researchQueries.isNotEmpty()
                ) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "研究",
                                headline = researchSourceText(uiState.researchSource),
                            )
                            uiState.researchEvidence.summaryLine
                                .takeIf { it.isNotBlank() }
                                ?.let { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSoft,
                                    )
                                }
                            if (uiState.researchOutsideAngle.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "外部视角",
                                    text = uiState.researchOutsideAngle,
                                )
                            }
                            if (uiState.researchGap.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "机会缺口",
                                    text = uiState.researchGap,
                                )
                            }
                            if (uiState.researchContrarianQuestion.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "值得追问",
                                    text = uiState.researchContrarianQuestion,
                                )
                            }
                            if (uiState.researchExternalHypothesis.isNotBlank()) {
                                ResearchInsightLine(
                                    label = "外部假设",
                                    text = uiState.researchExternalHypothesis,
                                )
                            }
                            if (uiState.researchQueries.isNotEmpty()) {
                                Text(
                                    text = "继续查",
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
                                text = "沉淀研究",
                                onClick = onCaptureResearchNote,
                            )
                        }
                    }
                }

                if (uiState.researchNotes.isNotEmpty()) {
                    item {
                        PanelCard {
                            if (uiState.researchClusters.isNotEmpty()) {
                                SectionHeader(
                                    title = "研究整理",
                                    headline = "${uiState.researchClusters.size} 组",
                                )
                                uiState.researchClusters.forEach { cluster ->
                                    Surface(
                                        shape = com.mindflow.app.ui.components.CardShape,
                                        color = com.mindflow.app.ui.theme.WhiteGlass.copy(alpha = 0.84f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, com.mindflow.app.ui.theme.BorderSoft),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "${cluster.label} · ${cluster.noteCount} 条",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = cluster.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSoft,
                                            )
                                            cluster.validationStep
                                                .takeIf { it.isNotBlank() }
                                                ?.let { validationStep ->
                                                    Text(
                                                        text = "先验证：$validationStep",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = AccentBlue,
                                                    )
                                                }
                                            cluster.executionPrompt
                                                .takeIf { it.isNotBlank() }
                                                ?.let { execution ->
                                                    Text(
                                                        text = "如果成立：$execution",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                        }
                                    }
                                }
                                uiState.researchClusters.firstOrNull()
                                    ?.takeIf { it.validationStep.isNotBlank() }
                                    ?.let { cluster ->
                                        Surface(
                                            shape = com.mindflow.app.ui.components.CardShape,
                                            color = AccentBlue.copy(alpha = 0.08f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = "先验证",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AccentBlue,
                                                )
                                                Text(
                                                    text = "${cluster.label}：${cluster.validationStep}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                cluster.executionPrompt
                                                    .takeIf { it.isNotBlank() }
                                                    ?.let { execution ->
                                                        Text(
                                                            text = "如果成立：$execution",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = TextSoft,
                                                        )
                                                    }
                                                GhostActionButton(
                                                    text = "记下验证",
                                                    onClick = onCaptureTopValidationLoopNote,
                                                )
                                            }
                                        }
                                    }
                                GhostActionButton(
                                    text = "沉淀脉络",
                                    onClick = onCaptureResearchClusterNote,
                                )
                            }
                            SectionHeader(
                                title = "研究记录",
                                headline = "${uiState.researchNotes.size} 条",
                            )
                            uiState.researchNotes.forEach { note ->
                                Surface(
                                    shape = com.mindflow.app.ui.components.CardShape,
                                    color = com.mindflow.app.ui.theme.WhiteGlass.copy(alpha = 0.84f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.mindflow.app.ui.theme.BorderSoft),
                                    modifier = Modifier.clickable { onOpenNote(note.id) },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = note.topic.ifBlank { "未命名研究记录" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSoft,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = com.mindflow.app.util.TimeFormatter.compact(note.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSoft,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.focusNote != null) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "执行",
                                headline = "${uiState.stage.label} · ${uiState.focusNote.status.label}",
                            )
                            Text(
                                text = uiState.focusNote.topic.ifBlank { "未命名记录" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${uiState.focusNote.horizon.label} · ${uiState.rhythmLine}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentBlue,
                            )
                            if (uiState.focusReason.isNotBlank()) {
                                Text(
                                    text = uiState.focusReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            if (uiState.executionWhyNow.isNotBlank()) {
                                Text(
                                    text = "现在推进的原因",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                Text(
                                    text = uiState.executionWhyNow,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (uiState.threadNextStep.isNotBlank()) {
                                Text(
                                    text = "先做这一步",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                Text(
                                    text = uiState.threadNextStep,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (uiState.validationStep.isNotBlank()) {
                                Text(
                                    text = "先验证",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                Text(
                                    text = uiState.validationStep,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (uiState.validationReason.isNotBlank()) {
                                Text(
                                    text = "现在验证的原因",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSoft,
                                )
                                Text(
                                    text = uiState.validationReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                            if (uiState.postValidationAction.isNotBlank()) {
                                Surface(
                                    shape = com.mindflow.app.ui.components.CardShape,
                                    color = AccentBlue.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f)),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "如果成立",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AccentBlue,
                                        )
                                        Text(
                                            text = uiState.postValidationAction,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        GhostActionButton(
                                            text = "记下执行动作",
                                            onClick = onCaptureResearchActionNote,
                                        )
                                    }
                                }
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

                if (uiState.directionAssets.isNotEmpty()) {
                    item {
                        PanelCard {
                            SectionHeader(
                                title = "方向资产",
                                headline = "${uiState.directionAssets.size} 条",
                            )
                            uiState.directionAssets.forEach { asset ->
                                Surface(
                                    shape = com.mindflow.app.ui.components.CardShape,
                                    color = com.mindflow.app.ui.theme.WhiteGlass.copy(alpha = 0.84f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.mindflow.app.ui.theme.BorderSoft),
                                    modifier = Modifier.clickable { onOpenNote(asset.noteId) },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = asset.type.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AccentBlue,
                                        )
                                        Text(
                                            text = asset.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = com.mindflow.app.util.TimeFormatter.compact(asset.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSoft,
                                        )
                                    }
                                }
                            }
                        }
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

private fun threadInsightSourceText(label: String): String = when (label) {
    "AI" -> "AI 洞察"
    "规则" -> "规则整理"
    else -> label
}

private fun researchSourceText(source: com.mindflow.app.data.brief.DailyBriefSource): String =
    if (source == com.mindflow.app.data.brief.DailyBriefSource.AI) "AI 外部视角" else "规则整理"
