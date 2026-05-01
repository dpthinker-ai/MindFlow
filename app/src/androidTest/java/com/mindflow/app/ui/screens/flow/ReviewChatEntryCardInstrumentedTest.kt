package com.mindflow.app.ui.screens.flow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test

class ReviewChatEntryCardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun entryCard_opensChatDirectlyAndShowsLatestSavedShortcut() {
        var openLatest = false
        var openChat = false
        var openHistory = false
        composeRule.setContent {
            MindFlowTheme {
                ReviewChatEntryCard(
                    latestSavedSummary = SavedReviewChatSessionSummary(
                        sessionId = 7L,
                        title = "上次回看",
                        updatedAt = 1_000L,
                        messageCount = 2,
                        latestExcerpt = "核心结论",
                    ),
                    onOpenChat = { openChat = true },
                    onOpenHistory = { openHistory = true },
                    onOpenSaved = { openLatest = true },
                )
            }
        }

        composeRule.onNodeWithText("和历史聊聊").assertIsDisplayed()
        composeRule.onNodeWithText("开始新对话").assertIsDisplayed().performClick()
        assertThat(openChat).isTrue()
        composeRule.onNodeWithText("查看聊天历史").assertIsDisplayed().performClick()
        assertThat(openHistory).isTrue()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("带着问题进入").assertDoesNotExist()
        composeRule.onNodeWithText("继续最近对话").assertIsDisplayed().performClick()
        assertThat(openLatest).isTrue()
    }

    @Test
    fun entryCard_hidesLatestShortcutWhenNothingSaved() {
        composeRule.setContent {
            MindFlowTheme {
                ReviewChatEntryCard(
                    latestSavedSummary = null,
                    onOpenChat = {},
                    onOpenHistory = {},
                    onOpenSaved = {},
                )
            }
        }

        composeRule.onNodeWithText("继续最近对话").assertDoesNotExist()
    }
}
