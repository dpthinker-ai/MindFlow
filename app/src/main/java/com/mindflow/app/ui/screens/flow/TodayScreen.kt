package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.navigation.CaptureSeed

internal val TodayDiscoveryTitleSlotHeight = 32.dp
internal const val TodayFocusReasonDetailMaxLines = 2
internal val TodayFocusReasonIconSize = 30.dp

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenTodayDiscovery: () -> Unit,
    onOpenTodayTask: (String) -> Unit,
    onOpenLatestSavedReviewChat: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestSavedConversationSummary by reviewChatSavedConversationRepository
        .observeLatestSavedSessionSummary()
        .collectAsStateWithLifecycle(initialValue = null)
    TodayScreen(
        uiState = uiState,
        latestSavedConversationSummary = latestSavedConversationSummary,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
        onCreateCapture = onCreateCapture,
        onOpenTodayDiscovery = onOpenTodayDiscovery,
        onOpenTodayTask = onOpenTodayTask,
        onOpenLatestSavedReviewChat = onOpenLatestSavedReviewChat,
        onOpenReviewChatHistory = onOpenReviewChatHistory,
    )
}

@Composable
private fun TodayScreen(
    uiState: TodayUiState,
    latestSavedConversationSummary: SavedReviewChatSessionSummary?,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenTodayDiscovery: () -> Unit,
    onOpenTodayTask: (String) -> Unit,
    onOpenLatestSavedReviewChat: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    val maintainerSnapshot = uiState.localMaintainerSnapshot
    val surface = remember(uiState) { uiState.toIncubationSurfaceState() }
    val todayModel = remember(uiState, latestSavedConversationSummary, surface) {
        uiState.toTodayDesignModel(
            latestSavedConversationSummary = latestSavedConversationSummary,
            surface = surface,
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                item {
                    TodayPageHeader(
                        title = "今天",
                        subtitle = "你的智能行动中枢",
                        showSpark = true,
                    )
                }
                item {
                    TodayHeroCard(model = todayModel)
                }
                item {
                    TodayFocusCard(
                        focus = todayModel.focus,
                        reason = todayModel.reason,
                        onOpenFocus = {
                            openTodayTarget(
                                focusNoteId = todayModel.focus.focusNoteId,
                                noteId = todayModel.focus.noteId,
                                threadKey = todayModel.focus.threadKey,
                                onOpenNote = onOpenNote,
                                onOpenThread = onOpenThread,
                                onFallback = { onCreateCapture(CaptureSeed()) },
                            )
                        },
                    )
                }
                item {
                    TodayDiscoverySection(
                        cards = todayModel.discoveryCards,
                        actionLabel = todayModel.discoveryActionLabel,
                        onOpenDiscovery = onOpenTodayDiscovery,
                    )
                }
                item {
                    TodayTrackingSection(
                        rows = todayModel.trackingRows,
                        actionLabel = todayModel.trackingActionLabel,
                        onOpenTodayTask = onOpenTodayTask,
                        onCreateCapture = onCreateCapture,
                    )
                }
                item {
                    TodayReviewHintCard(
                        review = todayModel.review,
                        onOpenSaved = onOpenLatestSavedReviewChat,
                        onOpenReviewChatHistory = onOpenReviewChatHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPageHeader(
    title: String,
    subtitle: String,
    showSpark: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            action != null -> action()
            showSpark -> {
                TodayIconBubble(
                    icon = Icons.Outlined.AutoAwesome,
                    tint = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    size = 38.dp,
                    iconSize = 19.dp,
                )
            }
        }
    }
}

@Composable
private fun TodayHeroCard(
    model: TodayDesignModel,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.heroTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.heroSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TodayOrbitMark()
        }
    }
}

@Composable
private fun TodayOrbitMark() {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 18.dp)
                .background(
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                    RoundedCornerShape(999.dp),
                ),
        )
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 9.dp)
                .background(
                    Color(0xFFFFC34D).copy(alpha = 0.22f),
                    RoundedCornerShape(999.dp),
                ),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 7.dp, bottom = 7.dp)
                .size(7.dp)
                .background(Color(0xFFFFC34D).copy(alpha = 0.86f), CircleShape),
        )
    }
}

@Composable
private fun TodayFocusCard(
    focus: TodayFocusModel,
    reason: TodayReasonModel,
    onOpenFocus: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFocus),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TodaySectionTitle(
                title = "今日焦点",
                action = if (focus.hasTarget) "打开焦点" else "新建记录",
                onAction = onOpenFocus,
            )
            Text(
                text = focus.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = focus.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayProgressLine(
                    progress = focus.progress,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = focus.progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TodayReasonBox(
                reason = reason,
            )
        }
    }
}

@Composable
private fun TodayReasonBox(
    reason: TodayReasonModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
    ) {
        val explanation = "${reason.sourceLine.removePrefix("推荐来源：")}，${reason.actionLine.removePrefix("建议动作：")}"
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = reason.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = TodayFocusReasonDetailMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            TodayIconBubble(
                icon = Icons.Outlined.Psychology,
                tint = MaterialTheme.colorScheme.secondary,
                background = MaterialTheme.colorScheme.surface,
                size = TodayFocusReasonIconSize,
                iconSize = 15.dp,
            )
        }
    }
}

@Composable
private fun TodayDiscoverySection(
    cards: List<TodayDiscoveryCardModel>,
    actionLabel: String,
    onOpenDiscovery: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TodaySectionTitle(
            title = "自动发现",
            action = actionLabel,
            onAction = onOpenDiscovery,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.take(3).forEach { card ->
                TodayDiscoveryMiniCard(
                    card = card,
                    onClick = onOpenDiscovery,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TodayDiscoveryMiniCard(
    card: TodayDiscoveryCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(124.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            TodayIconBubble(
                icon = Icons.AutoMirrored.Outlined.Article,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                size = 24.dp,
                iconSize = 13.dp,
            )
            Text(
                text = card.title,
                modifier = Modifier.height(TodayDiscoveryTitleSlotHeight),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.confidence,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayTrackingSection(
    rows: List<TodayTrackingRowModel>,
    actionLabel: String,
    onOpenTodayTask: (String) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TodaySectionTitle(
            title = "正在跟踪",
            action = actionLabel,
            onAction = null,
        )
        rows.take(2).forEach { row ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        row.threadKey
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onOpenTodayTask)
                            ?: onCreateCapture(CaptureSeed())
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = CardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = row.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = row.progressLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Text(
                            text = row.destinationLabel,
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
}

@Composable
private fun TodayReviewHintCard(
    review: TodayReviewModel,
    onOpenSaved: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                review.savedSessionId?.let(onOpenSaved)
                    ?: onOpenReviewChatHistory()
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TodayIconBubble(
                icon = Icons.Outlined.History,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                size = 32.dp,
                iconSize = 16.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = review.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodaySectionTitle(
    title: String,
    action: String,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = if (onAction == null) action else "$action ›",
            style = MaterialTheme.typography.labelLarge,
            color = if (onAction == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = if (onAction == null) Modifier else Modifier.clickable(onClick = onAction),
        )
    }
}

@Composable
private fun TodayProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f), RoundedCornerShape(999.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun TodayIconBubble(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun openTodayTarget(
    focusNoteId: Long?,
    noteId: Long?,
    threadKey: String?,
    onOpenNote: (Long) -> Unit,
    onOpenThread: (String) -> Unit,
    onFallback: () -> Unit,
) {
    when {
        focusNoteId != null -> onOpenNote(focusNoteId)
        noteId != null -> onOpenNote(noteId)
        !threadKey.isNullOrBlank() -> onOpenThread(threadKey)
        else -> onFallback()
    }
}
