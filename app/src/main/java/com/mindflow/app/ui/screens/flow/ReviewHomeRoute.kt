package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import androidx.compose.ui.unit.dp

internal fun reviewHomeRouteDependencyLabels(): List<String> =
    listOf("saved-review-chat-summary", "review-navigation")

@Composable
fun ReviewHomeRoute(
    reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository,
    onOpenReviewChat: (String) -> Unit,
    onOpenLatestSavedReviewChat: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    val latestSavedConversationSummary by reviewChatSavedConversationRepository
        .observeLatestSavedSessionSummary()
        .collectAsStateWithLifecycle(initialValue = null)

    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                ReviewChatEntryCard(
                    latestSavedSummary = latestSavedConversationSummary,
                    onOpenChat = { onOpenReviewChat("") },
                    onOpenPrompt = onOpenReviewChat,
                    onOpenHistory = onOpenReviewChatHistory,
                    onOpenSaved = onOpenLatestSavedReviewChat,
                    modifier = Modifier.fillParentMaxHeight(0.78f),
                )
            }
        }
    }
}
