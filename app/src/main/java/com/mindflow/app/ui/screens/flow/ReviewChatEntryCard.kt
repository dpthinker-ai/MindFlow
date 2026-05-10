package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.util.TimeFormatter

internal fun reviewHomeReferenceSectionKeys(): List<String> =
    listOf("search", "recent", "questions", "composer")

internal fun reviewHomeReferenceQuestionPrompts(): List<String> =
    listOf(
        "我最近在关注什么？",
        "我之前关于 MindFlow 说过什么？",
        "最近一个月有哪些未推进的想法？",
    )

internal fun SavedReviewChatSessionSummary.reviewHomeLatestExcerpt(): String {
    val titleText = title.trim()
    val excerptText = latestExcerpt.trim()
    return when {
        excerptText.isBlank() -> "继续这段历史记忆对话。"
        titleText.equals(excerptText, ignoreCase = true) -> "继续这段历史记忆对话。"
        else -> excerptText
    }
}

@Composable
fun ReviewChatEntryCard(
    latestSavedSummary: SavedReviewChatSessionSummary?,
    onOpenChat: () -> Unit,
    onOpenPrompt: (String) -> Unit = { onOpenChat() },
    onOpenHistory: () -> Unit,
    onOpenSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        ReviewHomeHeader(
            onOpenHistory = onOpenHistory,
            onOpenChat = onOpenChat,
        )
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            ReviewSearchPrompt(onClick = onOpenHistory)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewSectionTitle(
                    title = "近期回看",
                    action = "查看全部 ›",
                    onAction = onOpenHistory,
                )
                ReviewRecentConversationCard(
                    latestSavedSummary = latestSavedSummary,
                    onOpenChat = onOpenChat,
                    onOpenSaved = onOpenSaved,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewSectionTitle(title = "推荐问题")
                reviewHomeReferenceQuestionPrompts().forEach { question ->
                    ReviewQuestionRow(question = question, onClick = { onOpenPrompt(question) })
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        ReviewBottomAskBox(onClick = onOpenChat)
    }
}

@Composable
private fun ReviewHomeHeader(
    onOpenHistory: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "回看",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "与你的记忆对话，回顾与进步",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewHeaderIconButton(
                icon = Icons.Outlined.History,
                contentDescription = "查看聊天历史",
                onClick = onOpenHistory,
            )
            ReviewHeaderIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "开始新对话",
                onClick = onOpenChat,
            )
        }
    }
}

@Composable
private fun ReviewHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun ReviewSearchPrompt(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "搜索回看记录或内容...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReviewSectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewRecentConversationCard(
    latestSavedSummary: SavedReviewChatSessionSummary?,
    onOpenChat: () -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    if (latestSavedSummary == null) {
        PanelCard {
            ReviewRecentConversationContent(
                title = "还没有近期回看",
                excerpt = "先问一个问题，MindFlow 会把回答和来源自动保存到这里。",
                timeLabel = "现在",
                onClick = onOpenChat,
            )
        }
        return
    }

    Surface(
        onClick = { onOpenSaved(latestSavedSummary.sessionId) },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = CardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReviewRecentConversationContent(
            title = latestSavedSummary.title.ifBlank { "未命名回看" },
            excerpt = latestSavedSummary.reviewHomeLatestExcerpt(),
            timeLabel = TimeFormatter.compact(latestSavedSummary.updatedAt),
            onClick = { onOpenSaved(latestSavedSummary.sessionId) },
        )
    }
}

@Composable
private fun ReviewRecentConversationContent(
    title: String,
    excerpt: String,
    timeLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReviewRoundIcon(
            icon = Icons.Outlined.ChatBubbleOutline,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            tint = MaterialTheme.colorScheme.primary,
            size = 34,
            iconSize = 17,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewQuestionRow(
    question: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = CardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReviewRoundIcon(
                icon = Icons.Outlined.ChatBubbleOutline,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                tint = MaterialTheme.colorScheme.primary,
                size = 28,
                iconSize = 15,
            )
            Text(
                text = question,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewBottomAskBox(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReviewRoundIcon(
                icon = Icons.Outlined.AutoAwesome,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                tint = MaterialTheme.colorScheme.primary,
                size = 34,
                iconSize = 18,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "问问你的记忆，比如：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "我上周有哪些灵感复盘？",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = CircleShape,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↗",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    tint: Color,
    size: Int = 36,
    iconSize: Int = 18,
) {
    Surface(
        color = background,
        shape = CircleShape,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}
