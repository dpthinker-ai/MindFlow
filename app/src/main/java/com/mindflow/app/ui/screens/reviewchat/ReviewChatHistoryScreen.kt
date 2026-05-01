package com.mindflow.app.ui.screens.reviewchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
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
    val groupedSummaries = remember(summaries) {
        summaries.groupBy { summary -> historyGroupLabel(summary.updatedAt) }
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
                        text = "聊天历史",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextMain,
                    )
                    Text(
                        text = "自动保存每次对话，可搜索后继续追问。",
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
                        ActionButton(
                            text = "开始新对话",
                            onClick = onStartNewChat,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.AddComment,
                        )
                        ReviewChatHistorySearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                        )
                    }
                }

                if (summaries.isEmpty()) {
                    item("empty") {
                        ReviewChatHistoryEmptyState(query = query)
                    }
                } else {
                    historyGroupOrder.forEach { group ->
                        val items = groupedSummaries[group].orEmpty()
                        if (items.isNotEmpty()) {
                            item("group-$group") {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSoft,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
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
    PanelCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = summary.title.ifBlank { "未命名聊天" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${TimeFormatter.compact(summary.updatedAt)} · ${summary.messageCount} 条消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
            GhostActionButton(
                text = "继续这段对话",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
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
