package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.util.TimeFormatter

@Composable
fun ReviewChatEntryCard(
    latestSavedSummary: SavedReviewChatSessionSummary?,
    savedSummaries: List<SavedReviewChatSessionSummary>,
    onOpenChat: () -> Unit,
    onOpenSaved: (Long) -> Unit,
    onDeleteSaved: (List<Long>) -> Unit,
) {
    var showManager by remember { mutableStateOf(false) }
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "和历史聊聊",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                text = "基于你的历史记录和沉淀内容继续聊。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ActionButton(
            text = "进入聊天",
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.ChatBubbleOutline,
        )
        GhostActionButton(
            text = if (savedSummaries.isEmpty()) "暂无聊天记录" else "管理聊天记录",
            onClick = { showManager = true },
            enabled = savedSummaries.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )

        latestSavedSummary?.let { summary ->
            InsightBlock {
                Text(
                    text = "最近一次保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeFormatter.compact(summary.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
                GhostActionButton(
                    text = "继续上次保存",
                    onClick = { onOpenSaved(summary.sessionId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showManager) {
        ReviewChatHistoryManagerDialog(
            savedSummaries = savedSummaries,
            onDismiss = { showManager = false },
            onOpenSaved = { sessionId ->
                showManager = false
                onOpenSaved(sessionId)
            },
            onDeleteSaved = onDeleteSaved,
        )
    }
}

@Composable
private fun ReviewChatHistoryManagerDialog(
    savedSummaries: List<SavedReviewChatSessionSummary>,
    onDismiss: () -> Unit,
    onOpenSaved: (Long) -> Unit,
    onDeleteSaved: (List<Long>) -> Unit,
) {
    var selectedIds by remember(savedSummaries) { mutableStateOf(emptySet<Long>()) }
    val validIds = savedSummaries.mapTo(mutableSetOf()) { it.sessionId }
    val visibleSelectedIds = selectedIds.intersect(validIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "管理聊天记录",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "勾选后可批量删除，也可以在单条记录右侧直接删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(savedSummaries, key = { it.sessionId }) { summary ->
                        ReviewChatHistoryManagerRow(
                            summary = summary,
                            selected = summary.sessionId in visibleSelectedIds,
                            onToggleSelected = {
                                selectedIds = if (summary.sessionId in visibleSelectedIds) {
                                    visibleSelectedIds - summary.sessionId
                                } else {
                                    visibleSelectedIds + summary.sessionId
                                }
                            },
                            onOpen = { onOpenSaved(summary.sessionId) },
                            onDelete = {
                                selectedIds = selectedIds - summary.sessionId
                                onDeleteSaved(listOf(summary.sessionId))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            ActionButton(
                text = "删除选中",
                enabled = visibleSelectedIds.isNotEmpty(),
                onClick = {
                    onDeleteSaved(visibleSelectedIds.toList())
                    selectedIds = emptySet()
                },
            )
        },
        dismissButton = {
            GhostActionButton(
                text = "关闭",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ReviewChatHistoryManagerRow(
    summary: SavedReviewChatSessionSummary,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    InsightBlock {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSelected),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${TimeFormatter.compact(summary.updatedAt)} · ${summary.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                summary.latestExcerpt.takeIf { it.isNotBlank() }?.let { excerpt ->
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onOpen) {
                Text("打开")
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
