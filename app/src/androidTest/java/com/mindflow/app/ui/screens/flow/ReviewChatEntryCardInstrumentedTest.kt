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
    fun entryCard_submitsQuestionAndShowsLatestSavedShortcut() {
        var submittedQuestion: String? = null
        var openLatest = false
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
                    onSubmitQuestion = { submittedQuestion = it },
                    onOpenLatestSaved = { openLatest = true },
                )
            }
        }

        composeRule.onNodeWithText("和历史聊聊").assertIsDisplayed()
        composeRule.onNodeWithText("继续上次保存").assertIsDisplayed().performClick()
        assertThat(openLatest).isTrue()

        composeRule.onNode(hasSetTextAction())
            .performTextInput("把最近两周的矛盾串一下")
        composeRule.onNodeWithText("开始聊").performClick()

        assertThat(submittedQuestion).isEqualTo("把最近两周的矛盾串一下")
    }

    @Test
    fun entryCard_hidesLatestShortcutWhenNothingSaved() {
        composeRule.setContent {
            MindFlowTheme {
                ReviewChatEntryCard(
                    latestSavedSummary = null,
                    onSubmitQuestion = {},
                    onOpenLatestSaved = {},
                )
            }
        }

        composeRule.onNodeWithText("继续上次保存").assertDoesNotExist()
    }
}
