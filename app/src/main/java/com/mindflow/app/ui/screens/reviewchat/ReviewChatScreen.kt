package com.mindflow.app.ui.screens.reviewchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.ui.navigation.ReviewChatSeed

@Composable
fun ReviewChatRoute(
    seed: ReviewChatSeed,
    planner: ReviewChatPlanner,
    savedConversationRepository: ReviewChatSavedConversationRepository,
    onBack: () -> Unit,
) {
    val viewModel: ReviewChatViewModel = viewModel(
        key = "review-chat-${seed.requestId}",
        factory = ReviewChatViewModel.factory(
            seed = seed,
            answerTurn = planner::answer,
            savedConversationRepository = savedConversationRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewChatScreen(
        uiState = uiState,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::sendDraft,
        onRetry = viewModel::retry,
        onSave = viewModel::saveConversation,
    )
}

@Composable
private fun ReviewChatScreen(
    uiState: ReviewChatUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        val targetIndex = when {
            uiState.isSending -> uiState.messages.size
            uiState.messages.isNotEmpty() -> uiState.messages.lastIndex
            else -> null
        }
        targetIndex?.let { listState.animateScrollToItem(it) }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
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
                        text = uiState.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (uiState.isReadOnly) "这是一段已保存的回看对话。" else "基于你的历史记录和沉淀内容继续聊。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GhostActionButton(
                    text = if (uiState.isReadOnly) "已保存" else "保存",
                    onClick = onSave,
                    enabled = uiState.canSave,
                    icon = Icons.Outlined.Save,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 4.dp,
                    end = ScreenHorizontalPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.messages) { index, message ->
                    ReviewChatMessageBubble(
                        message = message,
                        providerLine = if (
                            message.role == ReviewChatMessageRole.ASSISTANT &&
                            index == uiState.messages.lastIndex
                        ) {
                            uiState.providerLine
                        } else {
                            ""
                        },
                    )
                }
                if (uiState.isSending) {
                    item("thinking") {
                        PanelCard {
                            Text(
                                text = "正在整理这次回答…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMain,
                            )
                        }
                    }
                }
                uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    item("error") {
                        InsightBlock {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMain,
                            )
                            if (!uiState.isReadOnly) {
                                GhostActionButton(
                                    text = "重试",
                                    onClick = onRetry,
                                )
                            }
                        }
                    }
                }
            }

            if (!uiState.isReadOnly) {
                Surface(
                    color = WhiteGlass.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, BorderSoft),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                            placeholder = {
                                Text(
                                    text = "继续追问，或者换个角度聊。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSoft,
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue.copy(alpha = 0.6f),
                                unfocusedBorderColor = BorderSoft,
                            ),
                        )
                        ActionButton(
                            text = if (uiState.isSending) "生成中" else "发送",
                            onClick = onSend,
                            enabled = uiState.canSend,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewChatMessageBubble(
    message: ReviewChatMessage,
    providerLine: String,
) {
    val isUser = message.role == ReviewChatMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Surface(
                color = AccentBlue.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.18f)),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                )
            }
        } else {
            PanelCard(
                modifier = Modifier.widthIn(max = 360.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                )
                val providerLabel = when {
                    providerLine.isNotBlank() -> providerLine
                    message.provider == ReviewChatProvider.CLOUD -> "云侧回答"
                    message.provider == ReviewChatProvider.ON_DEVICE -> "端侧回答"
                    else -> ""
                }
                if (providerLabel.isNotBlank()) {
                    InsightChip(text = providerLabel)
                }
            }
        }
    }
}
