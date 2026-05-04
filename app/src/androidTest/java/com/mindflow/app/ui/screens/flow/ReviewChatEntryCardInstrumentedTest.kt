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

        composeRule.onNodeWithText("回看").assertIsDisplayed()
        composeRule.onNodeWithText("与你的记忆对话，回顾与进步").assertIsDisplayed()
        composeRule.onNodeWithText("搜索回看记录或内容...").assertIsDisplayed()
        composeRule.onNodeWithText("近期回看").assertIsDisplayed()
        composeRule.onNodeWithText("推荐问题").assertIsDisplayed()
        composeRule.onNodeWithText("问问你的记忆，比如：").assertIsDisplayed()
        composeRule.onNodeWithText("开始新对话").assertIsDisplayed().performClick()
        assertThat(openChat).isTrue()
        composeRule.onNodeWithText("查看全部 ›").assertIsDisplayed().performClick()
        assertThat(openHistory).isTrue()
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
