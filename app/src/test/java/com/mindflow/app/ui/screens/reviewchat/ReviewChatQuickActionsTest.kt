package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.ui.navigation.CaptureMode
import org.junit.Test

class ReviewChatQuickActionsTest {
    @Test
    fun summaryActionCreatesEditableTextRecordSeed() {
        val seed = reviewChatAnswerToSummaryCaptureSeed(sampleAnswer())

        assertThat(seed.mode).isEqualTo(CaptureMode.TEXT)
        assertThat(seed.initialTopic).isEqualTo("回看总结")
        assertThat(seed.initialTags).contains("回看")
        assertThat(seed.initialContent).contains("你最近在增长和定位之间反复摇摆")
    }

    @Test
    fun taskActionCreatesTaskOrientedSeed() {
        val seed = reviewChatAnswerToTaskCaptureSeed(sampleAnswer())

        assertThat(seed.mode).isEqualTo(CaptureMode.TEXT)
        assertThat(seed.initialTopic).isEqualTo("回看任务")
        assertThat(seed.initialTags).contains("任务")
        assertThat(seed.initialContent).contains("下一步行动")
    }

    @Test
    fun followUpActionCreatesEditableQuestionPrompt() {
        val prompt = reviewChatFollowUpPrompt(sampleAnswer())

        assertThat(prompt).contains("继续展开")
        assertThat(prompt).contains("增长和定位")
    }

    private fun sampleAnswer(): ReviewChatMessage =
        ReviewChatMessage(
            role = ReviewChatMessageRole.ASSISTANT,
            content = "你最近在增长和定位之间反复摇摆。下一步行动是把产品页面验证扎实。",
            createdAt = 1_000L,
        )
}
