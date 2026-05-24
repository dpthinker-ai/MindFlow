package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.ReviewChatAnswerTrace
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatReferencedNote
import com.mindflow.app.ui.navigation.CaptureMode
import org.junit.Test

class ReviewChatQuickActionsTest {
    @Test
    fun assetActionCreatesEditableTextRecordSeedWithProvenance() {
        val seed = reviewChatAnswerToSummaryCaptureSeed(sampleAnswer())

        assertThat(seed.mode).isEqualTo(CaptureMode.TEXT)
        assertThat(seed.initialTopic).isEqualTo("回看资产")
        assertThat(seed.initialTags).containsAtLeast("回看", "资产")
        assertThat(seed.initialContent).contains("你最近在增长和定位之间反复摇摆")
        assertThat(seed.initialContent).contains("来源：回看对话")
        assertThat(seed.initialContent).contains("取材：最近30天 · 命中 2 条 · 云侧回答")
        assertThat(seed.initialContent).contains("来源记录：2026-05-24｜产品定位")
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
            provider = ReviewChatProvider.CLOUD,
            createdAt = 1_000L,
            referencedNotes = listOf(
                ReviewChatReferencedNote(
                    noteId = 42L,
                    title = "产品定位",
                    dateLabel = "2026-05-24",
                )
            ),
            answerTrace = ReviewChatAnswerTrace(
                displayLine = "最近30天 · 命中 2 条 · 云侧回答",
            ),
        )
}
