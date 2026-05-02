package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.InsightLine
import com.mindflow.app.ui.components.InsightTone
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.SectionHeader
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightChip(text = "自动保存", tone = InsightTone.Primary)
            InsightChip(text = "可搜索", tone = InsightTone.Neutral)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "回看聊天",
                headline = latestSavedSummary?.let { "最近聊到：${it.title}" } ?: "把历史记录交给模型重新梳理",
            )
            Text(
                text = "适合做统计、分类、复盘和追问。每次对话都会沉淀到历史里。",
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
            InsightBlock(tone = InsightTone.Neutral) {
                InsightLine(
                    label = "最近一次",
                    text = summary.title,
                    maxLines = 2,
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
