package com.mindflow.app.ui.screens.reviewchat

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatReferencedNote
import com.mindflow.app.data.reviewchat.ReviewChatStructuredAnswer
import com.mindflow.app.data.reviewchat.ReviewChatSkillWebView
import com.mindflow.app.data.reviewchat.normalizeReviewChatAnswerForDisplay
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.renderReviewChatStructuredAnswerAsMarkdown
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.ComposerTextField
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.IconPillButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.MarkdownText
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.ui.navigation.ReviewChatSeed
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

@Composable
fun ReviewChatRoute(
    seed: ReviewChatSeed,
    planner: ReviewChatPlanner,
    savedConversationRepository: ReviewChatSavedConversationRepository,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val viewModel: ReviewChatViewModel = viewModel(
        key = "review-chat-${seed.requestId}",
        factory = ReviewChatViewModel.factory(
            seed = seed,
            answerTurnStream = planner::answerStream,
            savedConversationRepository = savedConversationRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val routeScope = rememberCoroutineScope()
    var isLeaving by remember { mutableStateOf(false) }
    val flushAndBack = {
        if (!isLeaving) {
            isLeaving = true
            routeScope.launch {
                viewModel.flushWorkingSession()
                onBack()
            }
        }
    }
    BackHandler(onBack = flushAndBack)
    ReviewChatScreen(
        uiState = uiState,
        onBack = flushAndBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::sendDraft,
        onRetry = viewModel::retry,
        onSave = viewModel::saveConversation,
        onOpenRecord = onOpenRecord,
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
    onOpenRecord: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    var copySheet by remember { mutableStateOf<ReviewChatCopySheetState?>(null) }
    val bottomAnchorIndex = uiState.messages.size +
        (if (uiState.isSending) 1 else 0) +
        (if (!uiState.errorMessage.isNullOrBlank()) 1 else 0)

    LaunchedEffect(uiState.messages.size, uiState.isSending, uiState.errorMessage) {
        if (bottomAnchorIndex > 0) {
            listState.animateScrollToItem(bottomAnchorIndex)
        }
    }

    LaunchedEffect(uiState.draft.length, uiState.streamingAnswer, bottomAnchorIndex) {
        if (bottomAnchorIndex > 0) {
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                        onOpenRecord = onOpenRecord,
                        onRequestCopy = { payload ->
                            copySheet = ReviewChatCopySheetState(
                                message = payload,
                                mode = ReviewChatCopySheetMode.Menu,
                            )
                        },
                    )
                }
                if (uiState.isSending) {
                    item("thinking") {
                        if (uiState.streamingAnswer.isNotBlank()) {
                            ReviewChatMessageBubble(
                                message = ReviewChatMessage(
                                    role = ReviewChatMessageRole.ASSISTANT,
                                    content = uiState.streamingAnswer,
                                    provider = uiState.streamingProvider,
                                    createdAt = System.currentTimeMillis(),
                                ),
                                providerLine = uiState.providerLine,
                                onOpenRecord = onOpenRecord,
                                onRequestCopy = { payload ->
                                    copySheet = ReviewChatCopySheetState(
                                        message = payload,
                                        mode = ReviewChatCopySheetMode.Menu,
                                    )
                                },
                            )
                        } else {
                            PanelCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = uiState.generationStatus.ifBlank { "正在整理这次回答…" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMain,
                                    )
                                    if (uiState.providerLine.isNotBlank()) {
                                        InsightChip(text = uiState.providerLine)
                                    }
                                }
                            }
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
                item("bottom-anchor") {
                    Spacer(modifier = Modifier.height(1.dp))
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            ComposerTextField(
                                value = uiState.draft,
                                onValueChange = onDraftChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp, max = 148.dp),
                                minLines = 1,
                                maxLines = 4,
                                imeAction = ImeAction.Default,
                                placeholder = "继续追问，或换个角度聊。",
                            )
                            ActionButton(
                                text = if (uiState.isSending) "生成中" else "发送",
                                onClick = onSend,
                                enabled = uiState.canSend,
                                modifier = Modifier.widthIn(min = 76.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    copySheet?.let { sheet ->
        when (sheet.mode) {
            ReviewChatCopySheetMode.Menu -> {
                ReviewChatCopyMenuDialog(
                    message = sheet.message,
                    onDismiss = { copySheet = null },
                    onCopyAll = {
                        copySheet = ReviewChatCopySheetState(
                            message = sheet.message,
                            mode = ReviewChatCopySheetMode.Copied,
                        )
                    },
                    onSelectText = {
                        copySheet = ReviewChatCopySheetState(
                            message = sheet.message,
                            mode = ReviewChatCopySheetMode.SelectText,
                        )
                    },
                )
            }

            ReviewChatCopySheetMode.SelectText -> {
                ReviewChatSelectTextDialog(
                    message = sheet.message,
                    onDismiss = { copySheet = null },
                )
            }

            ReviewChatCopySheetMode.Copied -> {
                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current
                LaunchedEffect(sheet.message.content) {
                    clipboardManager.setText(AnnotatedString(sheet.message.content))
                    Toast.makeText(context, "已复制全文", Toast.LENGTH_SHORT).show()
                    copySheet = null
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
private fun ReviewChatMessageBubble(
    message: ReviewChatMessage,
    providerLine: String,
    onOpenRecord: (Long) -> Unit,
    onRequestCopy: (ReviewChatCopyMessage) -> Unit,
) {
    val isUser = message.role == ReviewChatMessageRole.USER
    val renderedMarkdown = message.structuredAnswer?.let(::renderReviewChatStructuredAnswerAsMarkdown)
    val normalizedContent = if (isUser) {
        message.content.trim()
    } else {
        renderedMarkdown ?: normalizeReviewChatAnswerForDisplay(message.content)
    }
    val copyPayload = ReviewChatCopyMessage(
        title = if (isUser) "复制你的消息" else "复制这条回复",
        content = normalizedContent,
        renderAsMarkdown = !isUser,
        structuredAnswer = message.structuredAnswer,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) {
            Surface(
                color = AccentBlue.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.18f)),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onRequestCopy(copyPayload) },
                    ),
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
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onRequestCopy(copyPayload) },
                    ),
            ) {
                if (message.structuredAnswer != null) {
                    ReviewChatStructuredAnswerContent(message.structuredAnswer)
                } else {
                    MarkdownText(
                        markdown = normalizedContent,
                    )
                }
                message.skillWebView?.let { skillWebView ->
                    ReviewChatSkillWebViewCard(skillWebView)
                }
                val providerLabel = when {
                    providerLine.isNotBlank() -> providerLine
                    message.provider == ReviewChatProvider.SYSTEM -> "系统提示"
                    message.provider == ReviewChatProvider.CLOUD -> "云侧回答"
                    message.provider == ReviewChatProvider.ON_DEVICE -> "端侧回答"
                    else -> ""
                }
                if (providerLabel.isNotBlank()) {
                    InsightChip(text = providerLabel)
                }
                val referencedNotes = when {
                    message.referencedNotes.isNotEmpty() -> message.referencedNotes
                    message.referencedNoteId != null -> listOf(
                        ReviewChatReferencedNote(
                            noteId = message.referencedNoteId,
                            title = "原记录",
                            dateLabel = "",
                        )
                    )
                    else -> emptyList()
                }
                if (referencedNotes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        referencedNotes.forEach { referencedNote ->
                            GhostActionButton(
                                text = buildReferencedNoteLabel(referencedNote),
                                onClick = { onOpenRecord(referencedNote.noteId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReviewChatSkillWebViewCard(
    skillWebView: ReviewChatSkillWebView,
) {
    val safeAspectRatio = skillWebView.aspectRatio.takeIf { it in 0.5f..2.5f } ?: 1.333f
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BorderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 172.dp, max = 280.dp)
                .aspectRatio(safeAspectRatio),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    setBackgroundColor(Color.TRANSPARENT)
                    webViewClient = WebViewClient()
                    loadUrl(skillWebView.url)
                }
            },
            update = { view ->
                if (view.url != skillWebView.url) {
                    view.loadUrl(skillWebView.url)
                }
            },
        )
    }
}

@Composable
private fun ReviewChatStructuredAnswerContent(
    answer: ReviewChatStructuredAnswer,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        answer.sections.forEach { section ->
            if (section.title == "答复") {
                section.body
                    .joinToString("\n")
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let { MarkdownText(markdown = it) }
                section.items.forEach { item ->
                    ReviewChatStructuredBulletRow(item)
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${section.title}：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain,
                    )
                    section.body.forEach { paragraph ->
                        MarkdownText(markdown = paragraph)
                    }
                    section.items.forEach { item ->
                        ReviewChatStructuredBulletRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewChatStructuredBulletRow(
    item: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
        )
        Box(
            modifier = Modifier.weight(1f),
        ) {
            MarkdownText(markdown = item)
        }
    }
}

private fun buildReferencedNoteLabel(note: ReviewChatReferencedNote): String {
    val title = note.title.ifBlank { "打开记录" }
    return if (note.dateLabel.isBlank()) {
        title
    } else {
        "${note.dateLabel}｜$title"
    }
}

@Composable
private fun ReviewChatCopyMenuDialog(
    message: ReviewChatCopyMessage,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onSelectText: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = message.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
        },
        text = {
            Text(
                text = "长内容默认不露出复制按钮。长按消息后，在这里选“复制全文”或“选择文本”。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
        },
        confirmButton = {
            ActionButton(
                text = "复制全文",
                onClick = onCopyAll,
                icon = Icons.Outlined.ContentCopy,
            )
        },
        dismissButton = {
            GhostActionButton(
                text = "选择文本",
                onClick = onSelectText,
            )
        },
    )
}

@Composable
private fun ReviewChatSelectTextDialog(
    message: ReviewChatCopyMessage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择文本",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
        },
        text = {
            SelectionContainer {
                if (message.structuredAnswer != null) {
                    ReviewChatStructuredAnswerContent(message.structuredAnswer)
                } else if (message.renderAsMarkdown) {
                    MarkdownText(markdown = message.content)
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
                    )
                }
            }
        },
        confirmButton = {
            GhostActionButton(
                text = "关闭",
                onClick = onDismiss,
            )
        },
    )
}

private data class ReviewChatCopyMessage(
    val title: String,
    val content: String,
    val renderAsMarkdown: Boolean,
    val structuredAnswer: ReviewChatStructuredAnswer? = null,
)

private data class ReviewChatCopySheetState(
    val message: ReviewChatCopyMessage,
    val mode: ReviewChatCopySheetMode,
)

private enum class ReviewChatCopySheetMode {
    Menu,
    SelectText,
    Copied,
}
