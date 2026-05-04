package com.mindflow.app.ui.screens.reviewchat

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.InsightTone
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.PanelBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun ReviewChatHistoryRoute(
    savedConversationRepository: ReviewChatSavedConversationRepository,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartNewChat: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val summaries by savedConversationRepository
        .observeSavedSessionSummaries(query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    ReviewChatHistoryScreen(
        query = query,
        summaries = summaries,
        onQueryChange = { query = it },
        onBack = onBack,
        onOpenSession = onOpenSession,
        onStartNewChat = onStartNewChat,
        onDeleteSession = { sessionId ->
            scope.launch {
                savedConversationRepository.deleteSessions(listOf(sessionId))
            }
        },
    )
}

internal fun reviewHistoryReferenceFilterLabels(): List<String> =
    ReviewHistoryFilterTab.entries.map { it.label } +
        listOf(
            ReviewHistoryTimeScope.TODAY,
            ReviewHistoryTimeScope.WEEK,
            ReviewHistoryTimeScope.MONTH,
            ReviewHistoryTimeScope.LAST_YEAR_TODAY,
        ).map { it.label }

@Composable
private fun ReviewChatHistoryScreen(
    query: String,
    summaries: List<SavedReviewChatSessionSummary>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartNewChat: () -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedReviewChatSessionSummary?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(ReviewHistoryFilterTab.ALL) }
    var selectedTimeScope by rememberSaveable { mutableStateOf(ReviewHistoryTimeScope.ALL) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val filteredSummaries = remember(summaries, selectedTab, selectedTimeScope, today, zone) {
        filterReviewHistorySummaries(
            summaries = summaries,
            selectedTab = selectedTab,
            selectedTimeScope = selectedTimeScope,
            today = today,
            zone = zone,
        )
    }
    val groupedSummaries = remember(filteredSummaries) {
        filteredSummaries.groupBy { summary -> historyGroupLabel(summary.updatedAt) }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconPillButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "回看",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextMain,
                    )
                    Text(
                        text = "按时间、主题和任务重新找回你的记忆。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 2.dp,
                    end = ScreenHorizontalPadding,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReviewHistoryFilterTabs(
                            selectedTab = selectedTab,
                            onSelectTab = { tab ->
                                selectedTab = tab
                                if (tab == ReviewHistoryFilterTab.ALL) {
                                    selectedTimeScope = ReviewHistoryTimeScope.ALL
                                }
                            },
                        )
                        ReviewHistoryTimeChips(
                            selectedTimeScope = selectedTimeScope,
                            onSelectTimeScope = { scope ->
                                selectedTimeScope = scope
                                if (selectedTab == ReviewHistoryFilterTab.ALL) {
                                    selectedTab = ReviewHistoryFilterTab.TIME
                                }
                            },
                        )
                        ReviewChatHistorySearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                        )
                        ReviewHistoryTopicSummary(
                            summaries = filteredSummaries,
                            onStartNewChat = onStartNewChat,
                        )
                    }
                }

                if (filteredSummaries.isEmpty()) {
                    item("empty") {
                        ReviewChatHistoryEmptyState(query = query)
                    }
                } else {
                    historyGroupOrder.forEach { group ->
                        val items = groupedSummaries[group].orEmpty()
                        if (items.isNotEmpty()) {
                            item("group-$group") {
                                ReviewHistoryResultHeader(group = group)
                            }
                            items(items, key = { it.sessionId }) { summary ->
                                ReviewChatHistoryRow(
                                    summary = summary,
                                    onOpen = { onOpenSession(summary.sessionId) },
                                    onDelete = { pendingDelete = summary },
                                )
                            }
                        }
                    }
                }

                item("bottom") {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = "删除这段聊天？",
                    color = TextMain,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "删除后无法从聊天历史中恢复：${summary.title}",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(summary.sessionId)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ReviewHistoryFilterTabs(
    selectedTab: ReviewHistoryFilterTab,
    onSelectTab: (ReviewHistoryFilterTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReviewHistoryFilterTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(tab) },
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) AccentBlue else TextSoft,
                )
                Surface(
                    color = if (selected) AccentBlue else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .width(42.dp)
                        .height(2.dp),
                    content = {},
                )
            }
        }
    }
}

@Composable
private fun ReviewHistoryTimeChips(
    selectedTimeScope: ReviewHistoryTimeScope,
    onSelectTimeScope: (ReviewHistoryTimeScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            ReviewHistoryTimeScope.TODAY,
            ReviewHistoryTimeScope.WEEK,
            ReviewHistoryTimeScope.MONTH,
            ReviewHistoryTimeScope.LAST_YEAR_TODAY,
        ).forEach { scope ->
            InsightChip(
                text = scope.label,
                tone = if (scope == selectedTimeScope) InsightTone.Primary else InsightTone.Neutral,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onSelectTimeScope(
                            if (scope == selectedTimeScope) ReviewHistoryTimeScope.ALL else scope
                        )
                    },
            )
        }
    }
}

@Composable
private fun ReviewHistoryTopicSummary(
    summaries: List<SavedReviewChatSessionSummary>,
    onStartNewChat: () -> Unit,
) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = PanelBlue,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AddComment,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "主题回看",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                )
                Text(
                    text = "相关记录 ${summaries.sumOf { it.messageCount }.coerceAtLeast(summaries.size)} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = TextSoft,
            )
        }
        Text(
            text = summaries.firstOrNull()?.latestExcerpt?.ifBlank { null }
                ?: "本月的回看会按主题聚合，帮助你找回旧问题、旧判断和未推进的行动。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ActionButton(
            text = "开始新对话",
            onClick = onStartNewChat,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.AddComment,
        )
    }
}

@Composable
private fun ReviewHistoryResultHeader(group: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "回看结果",
            style = MaterialTheme.typography.titleSmall,
            color = TextMain,
        )
        Text(
            text = group,
            style = MaterialTheme.typography.labelLarge,
            color = TextSoft,
        )
    }
}

@Composable
private fun ReviewChatHistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = "搜索标题、摘要或聊天内容",
                color = TextSoft,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = TextSoft,
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconPillButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "清空搜索",
                    onClick = { onQueryChange("") },
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WhiteGlass.copy(alpha = 0.92f),
            unfocusedContainerColor = WhiteGlass.copy(alpha = 0.92f),
            focusedBorderColor = BorderSoft,
            unfocusedBorderColor = BorderSoft,
        ),
    )
}

@Composable
private fun ReviewChatHistoryRow(
    summary: SavedReviewChatSessionSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Surface(
                color = AccentBlue,
                shape = CircleShape,
                modifier = Modifier.size(8.dp),
                content = {},
            )
            Surface(
                color = AccentBlue.copy(alpha = 0.22f),
                modifier = Modifier
                    .width(2.dp)
                    .height(72.dp),
                content = {},
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            color = WhiteGlass.copy(alpha = 0.94f),
            shape = CardShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = summary.title.ifBlank { "未命名回看" },
                            style = MaterialTheme.typography.titleSmall,
                            color = TextMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "已总结为 ${summary.messageCount.coerceAtLeast(1)} 个要点",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSoft,
                    )
                }
                summary.latestExcerpt.takeIf { it.isNotBlank() }?.let { excerpt ->
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = TimeFormatter.compact(summary.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewChatHistoryEmptyState(query: String) {
    InsightBlock {
        Text(
            text = if (query.isBlank()) "还没有聊天历史" else "没有匹配的聊天",
            style = MaterialTheme.typography.titleSmall,
            color = TextMain,
        )
        Text(
            text = if (query.isBlank()) {
                "开始一次聊天后，这里会自动保存。"
            } else {
                "换一个关键词试试，搜索会匹配标题、摘要和消息正文。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSoft,
        )
    }
}

private val historyGroupOrder = listOf("今天", "昨天", "本周", "更早")

internal enum class ReviewHistoryFilterTab(val label: String) {
    ALL("全部"),
    TIME("时间"),
    TOPIC("主题"),
    TASK("任务"),
}

internal enum class ReviewHistoryTimeScope(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
    LAST_YEAR_TODAY("去年今日"),
}

internal fun filterReviewHistorySummaries(
    summaries: List<SavedReviewChatSessionSummary>,
    selectedTab: ReviewHistoryFilterTab,
    selectedTimeScope: ReviewHistoryTimeScope,
    today: LocalDate,
    zone: ZoneId,
): List<SavedReviewChatSessionSummary> =
    summaries.filter { summary ->
        summary.matchesReviewHistoryTab(selectedTab) &&
            summary.matchesReviewHistoryTimeScope(selectedTimeScope, today, zone)
    }

private fun SavedReviewChatSessionSummary.matchesReviewHistoryTab(tab: ReviewHistoryFilterTab): Boolean =
    when (tab) {
        ReviewHistoryFilterTab.ALL,
        ReviewHistoryFilterTab.TIME -> true
        ReviewHistoryFilterTab.TOPIC -> !isTaskLikeReviewHistorySession()
        ReviewHistoryFilterTab.TASK -> isTaskLikeReviewHistorySession()
    }

private fun SavedReviewChatSessionSummary.matchesReviewHistoryTimeScope(
    scope: ReviewHistoryTimeScope,
    today: LocalDate,
    zone: ZoneId,
): Boolean {
    val date = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    return when (scope) {
        ReviewHistoryTimeScope.ALL -> true
        ReviewHistoryTimeScope.TODAY -> date == today
        ReviewHistoryTimeScope.WEEK -> !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
        ReviewHistoryTimeScope.MONTH -> date.year == today.year && date.month == today.month
        ReviewHistoryTimeScope.LAST_YEAR_TODAY -> date == today.minusYears(1)
    }
}

private fun SavedReviewChatSessionSummary.isTaskLikeReviewHistorySession(): Boolean {
    val text = "$title $latestExcerpt".lowercase()
    return listOf("任务", "行动", "待办", "推进", "计划", "完成", "todo", "next").any { keyword ->
        text.contains(keyword)
    }
}

private fun historyGroupLabel(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.isAfter(today.minusDays(7)) -> "本周"
        else -> "更早"
    }
}
