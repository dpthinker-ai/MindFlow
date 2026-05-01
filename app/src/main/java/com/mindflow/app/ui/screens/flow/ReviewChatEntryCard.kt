package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onOpenChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "和历史聊聊",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                text = "每次对话都会自动保存，后续可以搜索和继续追问。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ActionButton(
            text = "开始新对话",
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.ChatBubbleOutline,
        )
        GhostActionButton(
            text = "查看聊天历史",
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.History,
        )

        latestSavedSummary?.let { summary ->
            InsightBlock {
                Text(
                    text = "最近一次对话",
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
                    text = "${TimeFormatter.compact(summary.updatedAt)} · ${summary.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
                GhostActionButton(
                    text = "继续最近对话",
                    onClick = { onOpenSaved(summary.sessionId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
