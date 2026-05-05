package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.InsightLine
import com.mindflow.app.ui.components.NeonProgress
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.AccentTeal
import com.mindflow.app.ui.theme.Panel

@Composable
fun TodayDiscoveryRoute(
    viewModel: FlowViewModel,
    onBack: () -> Unit,
    onOpenTaskDetail: (String) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surface = remember(uiState) { uiState.toIncubationSurfaceState() }
    val model = remember(uiState, surface) {
        uiState.toTodayDesignModel(
            latestSavedConversationSummary = null,
            surface = surface,
        )
    }
    var selectedFilter by remember { mutableStateOf(TodayDiscoveryFilter.ALL) }
    var ignoredKeys by remember { mutableStateOf(emptySet<String>()) }
    var snoozedKeys by remember { mutableStateOf(emptySet<String>()) }
    val cards = model.discoveryCards.filter { card ->
        val key = card.stableKey()
        when (selectedFilter) {
            TodayDiscoveryFilter.ALL -> true
            TodayDiscoveryFilter.HIGH_CONFIDENCE -> card.confidencePercent() >= 85
            TodayDiscoveryFilter.PENDING -> key !in ignoredKeys && key !in snoozedKeys
            TodayDiscoveryFilter.IGNORED -> key in ignoredKeys
        }
    }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                top = 8.dp,
                end = ScreenHorizontalPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TodaySecondaryHeader(
                    title = "自动发现",
                    subtitle = "系统从你的记录中识别出可推进的方向",
                    onBack = onBack,
                    trailingIcon = Icons.Outlined.Tune,
                    trailingLabel = "筛选",
                )
            }
            item {
                TodayDiscoveryFilters(
                    selected = selectedFilter,
                    pendingCount = model.discoveryCards.count { it.stableKey() !in ignoredKeys && it.stableKey() !in snoozedKeys },
                    ignoredCount = ignoredKeys.size,
                    onSelect = { selectedFilter = it },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.forEach { card ->
                        TodayDiscoveryCandidateCard(
                            card = card,
                            isIgnored = card.stableKey() in ignoredKeys,
                            isSnoozed = card.stableKey() in snoozedKeys,
                            onSnooze = {
                                val key = card.stableKey()
                                snoozedKeys = snoozedKeys + key
                                ignoredKeys = ignoredKeys - key
                            },
                            onIgnore = {
                                val key = card.stableKey()
                                ignoredKeys = ignoredKeys + key
                                snoozedKeys = snoozedKeys - key
                            },
                            onJoinToday = {
                                card.threadKey
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(onOpenTaskDetail)
                                    ?: onCreateCapture(
                                        CaptureSeed(
                                            initialTopic = card.title,
                                            initialContent = "围绕「${card.title}」补一条今天可以推进的记录：",
                                        ),
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodayTaskDetailRoute(
    viewModel: FlowViewModel,
    threadKey: String,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onAskReview: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surface = remember(uiState) { uiState.toIncubationSurfaceState() }
    val todayModel = remember(uiState, surface) {
        uiState.toTodayDesignModel(
            latestSavedConversationSummary = null,
            surface = surface,
        )
    }
    val detail = todayModel.taskDetailFor(threadKey)
    var showSplitSteps by remember(threadKey) { mutableStateOf(false) }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TodaySecondaryHeader(
                        title = "任务详情",
                        subtitle = "全面了解任务状态与下一步建议",
                        onBack = onBack,
                        trailingIcon = Icons.Outlined.MoreHoriz,
                        trailingLabel = "更多",
                    )
                }
                if (detail == null) {
                    item {
                        PanelCard {
                            Text(
                                text = "没有找到这条任务",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "返回今天页后重新选择一个正在跟踪的方向。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    todayTaskDetailSectionKeys(showSplitSteps).forEach { sectionKey ->
                        item(key = sectionKey) {
                            when (sectionKey) {
                                "hero" -> TodayTaskHero(detail = detail)
                                "related_summary" -> TodayTaskRelatedSummary(detail)
                                "materials" -> TodayTaskMaterials(detail.materials)
                                "next_suggestion" -> TodayTaskNextSuggestion(detail.nextSuggestion)
                                "progress" -> TodayTaskProgressCard(detail)
                                "split_preview" -> TodayTaskSplitPreview(detail)
                            }
                        }
                    }
                }
            }
            if (detail != null) {
                TodayTaskBottomActions(
                    detail = detail,
                    onStart = { onOpenThread(detail.threadKey) },
                    onAskReview = {
                        onAskReview("围绕「${detail.title}」，我过去记录过哪些关键线索？下一步该怎么推进？")
                    },
                    onSplit = { showSplitSteps = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private enum class TodayDiscoveryFilter(
    val label: String,
) {
    ALL("全部"),
    HIGH_CONFIDENCE("高置信度"),
    PENDING("待处理"),
    IGNORED("已忽略"),
}

internal fun todayTaskDetailSectionKeys(showSplitSteps: Boolean): List<String> =
    buildList {
        add("hero")
        add("related_summary")
        add("materials")
        add("next_suggestion")
        add("progress")
        if (showSplitSteps) add("split_preview")
    }

internal fun todayTimelineConnectorSegments(stepCount: Int): Int =
    (stepCount - 1).coerceAtLeast(0)

@Composable
private fun TodaySecondaryHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconPillButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TodayDiscoveryFilters(
    selected: TodayDiscoveryFilter,
    pendingCount: Int,
    ignoredCount: Int,
    onSelect: (TodayDiscoveryFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TodayDiscoveryFilter.entries.forEach { filter ->
            val count = when (filter) {
                TodayDiscoveryFilter.ALL -> 3
                TodayDiscoveryFilter.HIGH_CONFIDENCE -> 2
                TodayDiscoveryFilter.PENDING -> pendingCount
                TodayDiscoveryFilter.IGNORED -> ignoredCount
            }
            TodayFilterPill(
                text = "${filter.label} $count",
                selected = selected == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TodayFilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TodayDiscoveryCandidateCard(
    card: TodayDiscoveryCardModel,
    isIgnored: Boolean,
    isSnoozed: Boolean,
    onSnooze: () -> Unit,
    onIgnore: () -> Unit,
    onJoinToday: () -> Unit,
) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        isIgnored -> "已忽略"
                        isSnoozed -> "稍后处理"
                        else -> "待处理"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            TodayConfidenceBadge(card.confidence)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TodayInfoCell(
                label = "来源",
                value = card.source,
                modifier = Modifier.weight(1f),
            )
            TodayInfoCell(
                label = "置信度",
                value = card.confidence,
                modifier = Modifier.weight(1f),
            )
        }
        InsightLine(
            label = "识别原因",
            text = card.reason,
            emphasize = true,
            maxLines = 3,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = "加入今天",
                onClick = onJoinToday,
                modifier = Modifier.weight(1f),
                enabled = !isIgnored,
            )
            GhostActionButton(
                text = "稍后",
                onClick = onSnooze,
                modifier = Modifier.weight(1f),
            )
            GhostActionButton(
                text = "忽略",
                onClick = onIgnore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TodayConfidenceBadge(confidence: String) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = confidence,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f), RoundedCornerShape(999.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence.confidenceFraction())
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun TodayInfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TodayTaskHero(detail: TodayTaskDetailModel) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail.createdLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TodayStatusBubble(detail.statusLabel)
        }
        TodayTimelineRow(detail.timeline)
    }
}

@Composable
private fun TodayStatusBubble(text: String) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.54f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun TodayTimelineRow(timeline: List<TodayTaskTimelineStep>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(0.5f))
                repeat(todayTimelineConnectorSegments(timeline.size)) { segmentIndex ->
                    val nextStep = timeline[segmentIndex + 1]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                when {
                                    nextStep.active -> MaterialTheme.colorScheme.primary
                                    nextStep.completed -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                    )
                }
                Spacer(Modifier.weight(0.5f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                timeline.forEach { step ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(
                                    when {
                                        step.active -> 24.dp
                                        step.completed -> 18.dp
                                        else -> 16.dp
                                    }
                                )
                                .background(
                                    when {
                                        step.active -> MaterialTheme.colorScheme.primary
                                        step.completed -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            timeline.forEach { step ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
}

@Composable
private fun TodayTaskRelatedSummary(detail: TodayTaskDetailModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TodayInfoCell(
            label = "相关记录",
            value = "${detail.relatedRecordCount} 条",
            modifier = Modifier.weight(1f),
        )
        TodayInfoCell(
            label = "关联文章",
            value = "${detail.relatedArticleCount} 篇",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TodayTaskMaterials(materials: List<TodayTaskMaterialModel>) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "关联材料",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "查看全部 ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        materials.forEach { material ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = material.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = material.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TodayTaskNextSuggestion(nextSuggestion: String) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "下一步建议",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = nextSuggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodayTaskProgressCard(detail: TodayTaskDetailModel) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "推进进度",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "整体进度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = detail.progressLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        NeonProgress(progress = detail.progress)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("已完成 13", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("进行中 5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("待开始 2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayTaskSplitPreview(detail: TodayTaskDetailModel) {
    PanelCard {
        Text(
            text = "拆分建议",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(
            "补一句最新进展，明确当前事实",
            "把下一步压成一个今天能完成的小动作",
            "完成后记录验证结果，再决定是否继续推进",
        ).forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (index == 1) "围绕「${detail.title}」$step" else step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TodayTaskBottomActions(
    detail: TodayTaskDetailModel,
    onStart: () -> Unit,
    onAskReview: () -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = detail.primaryActionLabel,
                onClick = onStart,
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.weight(1f),
            )
            GhostActionButton(
                text = detail.secondaryActionLabel,
                onClick = onAskReview,
                icon = Icons.Outlined.History,
                modifier = Modifier.weight(1f),
            )
            GhostActionButton(
                text = detail.tertiaryActionLabel,
                onClick = onSplit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun TodayDiscoveryCardModel.stableKey(): String =
    threadKey?.takeIf { it.isNotBlank() } ?: title

private fun TodayDiscoveryCardModel.confidencePercent(): Int =
    confidence.filter(Char::isDigit).toIntOrNull() ?: 0

private fun String.confidenceFraction(): Float =
    (filter(Char::isDigit).toIntOrNull() ?: 0).coerceIn(0, 100) / 100f
