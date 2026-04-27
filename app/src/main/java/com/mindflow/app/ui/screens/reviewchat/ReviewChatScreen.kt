package com.mindflow.app.ui.screens.reviewchat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatReferencedNote
import com.mindflow.app.data.reviewchat.ReviewChatStructuredAnswer
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
import com.mindflow.app.ui.components.SkillWebViewCardHost
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.ui.navigation.ReviewChatSeed
import kotlinx.coroutines.delay
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
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    var copySheet by remember { mutableStateOf<ReviewChatCopySheetState?>(null) }
    val completedProgressInsertionIndex = if (
        !uiState.isSending &&
        uiState.progressSteps.isNotEmpty() &&
        uiState.messages.lastOrNull()?.role == ReviewChatMessageRole.ASSISTANT
    ) {
        uiState.messages.dropLast(1).indexOfLast { it.role == ReviewChatMessageRole.USER }
    } else {
        -1
    }
    val bottomAnchorIndex = uiState.messages.size +
        (if (completedProgressInsertionIndex >= 0) 1 else 0) +
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

    LaunchedEffect(isImeVisible, bottomAnchorIndex) {
        if (isImeVisible && bottomAnchorIndex > 0) {
            delay(180)
            listState.animateScrollToItem(bottomAnchorIndex)
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
                    bottom = if (uiState.isReadOnly) 16.dp else 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.messages) { index, message ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                        if (index == completedProgressInsertionIndex) {
                            ReviewChatProgressPanel(
                                steps = uiState.progressSteps,
                                providerLine = uiState.providerLine,
                                initiallyExpanded = false,
                            )
                        }
                    }
                }
                if (uiState.isSending) {
                    item("thinking") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ReviewChatProgressPanel(
                                steps = uiState.progressSteps.ifEmpty {
                                    listOf(
                                        ReviewChatProgressStep(
                                            id = "thinking",
                                            title = uiState.generationStatus.ifBlank { "正在整理这次回答" },
                                        )
                                    )
                                },
                                providerLine = uiState.providerLine,
                                initiallyExpanded = uiState.streamingAnswer.isBlank(),
                            )
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
private fun ReviewChatProgressPanel(
    steps: List<ReviewChatProgressStep>,
    providerLine: String,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val currentStep = steps.lastOrNull { it.state == ReviewChatProgressStepState.RUNNING }
        ?: steps.last()
    val completedCount = steps.count { it.state == ReviewChatProgressStepState.DONE }
    var expanded by remember(steps.firstOrNull()?.id, steps.size) {
        mutableStateOf(initiallyExpanded)
    }

    PanelCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewChatProgressMarker(currentStep.state)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = currentStep.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = currentStep.detail.ifBlank {
                        if (currentStep.state == ReviewChatProgressStepState.RUNNING) {
                            "正在执行第 ${completedCount + 1} / ${steps.size} 步"
                        } else {
                            "执行过程已完成 $completedCount / ${steps.size} 步"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (expanded) "收起" else "详情",
                style = MaterialTheme.typography.labelMedium,
                color = AccentBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (providerLine.isNotBlank()) {
            InsightChip(text = providerLine)
        }

        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { step ->
                    ReviewChatProgressStepRow(step)
                }
            }
        }
    }
}

@Composable
private fun ReviewChatProgressStepRow(
    step: ReviewChatProgressStep,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReviewChatProgressMarker(step.state, compact = true)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
            )
            if (step.detail.isNotBlank()) {
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun ReviewChatProgressMarker(
    state: ReviewChatProgressStepState,
    compact: Boolean = false,
) {
    val markerSize = if (compact) 14.dp else 22.dp
    Box(
        modifier = Modifier.size(markerSize),
        contentAlignment = Alignment.Center,
    ) {
        if (state == ReviewChatProgressStepState.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(markerSize),
                strokeWidth = if (compact) 1.5.dp else 2.dp,
                color = AccentBlue,
            )
        } else {
            val color = when (state) {
                ReviewChatProgressStepState.DONE -> AccentBlue
                ReviewChatProgressStepState.FAILED -> MaterialTheme.colorScheme.error
                ReviewChatProgressStepState.RUNNING -> AccentBlue
            }
            Box(
                modifier = Modifier
                    .size(if (compact) 9.dp else 12.dp)
                    .background(color = color, shape = CircleShape),
            )
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
            Column(
                modifier = Modifier
                    .widthIn(max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                message.skillWebView?.let { skillWebView ->
                    SkillWebViewCardHost(
                        url = skillWebView.url,
                        aspectRatio = skillWebView.aspectRatio,
                    )
                }

                PanelCard(
                    modifier = Modifier
                        .fillMaxWidth()
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
