package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.screens.review.reviewHomeReferenceSectionKeys
import com.mindflow.app.ui.screens.review.reviewHomeReferenceQuestionPrompts
import com.mindflow.app.ui.screens.review.reviewHomeLatestExcerpt
import org.junit.Test

class ReviewChatReferenceDesignModelTest {
    @Test
    fun reviewHomeKeepsReferenceConversationEntryOrder() {
        assertThat(reviewHomeReferenceSectionKeys())
            .containsExactly("search", "recent", "questions", "composer")
            .inOrder()
        assertThat(reviewHomeReferenceQuestionPrompts())
            .containsExactly(
                "我最近在关注什么？",
                "我之前关于 MindFlow 说过什么？",
                "最近一个月有哪些未推进的想法？",
            )
            .inOrder()
    }

    @Test
    fun reviewAnswerCompanionMatchesReferenceLimits() {
        assertThat(ReviewChatReferenceSourceLimit).isEqualTo(3)
        assertThat(reviewChatReferenceQuickActionLabels())
            .containsExactly("加入今天", "转成任务", "继续追问", "沉淀资产")
            .inOrder()
        assertThat(reviewChatReferenceComposerPlaceholder())
            .isEqualTo("继续回看或提问...")
        assertThat(reviewHistoryReferenceFilterLabels())
            .containsExactly("全部", "时间", "主题", "任务", "今天", "本周", "本月", "去年今日")
            .inOrder()
    }

    @Test
    fun recentConversationExcerptDoesNotRepeatTitle() {
        val summary = SavedReviewChatSessionSummary(
            sessionId = 1L,
            title = "我最近在关注什么？",
            updatedAt = 0L,
            messageCount = 2,
            latestExcerpt = "我最近在关注什么？",
        )

        assertThat(summary.reviewHomeLatestExcerpt())
            .isEqualTo("继续这段历史记忆对话。")
    }
}
