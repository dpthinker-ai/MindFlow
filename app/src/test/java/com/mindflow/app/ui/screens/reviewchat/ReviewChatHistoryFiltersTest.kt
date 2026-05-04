package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class ReviewChatHistoryFiltersTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 5, 3)

    @Test
    fun taskTabKeepsOnlyActionOrTaskLikeSessions() {
        val result = filterReviewHistorySummaries(
            summaries = listOf(
                summary(id = 1L, title = "任务推进回看", excerpt = "下一步行动是整理发布清单"),
                summary(id = 2L, title = "MindFlow 主题回看", excerpt = "主要关注记录页和今天页的视觉一致性"),
            ),
            selectedTab = ReviewHistoryFilterTab.TASK,
            selectedTimeScope = ReviewHistoryTimeScope.ALL,
            today = today,
            zone = zone,
        )

        assertThat(result.map { it.sessionId }).containsExactly(1L)
    }

    @Test
    fun todayTimeScopeKeepsOnlySessionsUpdatedToday() {
        val result = filterReviewHistorySummaries(
            summaries = listOf(
                summary(id = 1L, title = "今天的回看", updatedDate = today),
                summary(id = 2L, title = "昨天的回看", updatedDate = today.minusDays(1)),
            ),
            selectedTab = ReviewHistoryFilterTab.ALL,
            selectedTimeScope = ReviewHistoryTimeScope.TODAY,
            today = today,
            zone = zone,
        )

        assertThat(result.map { it.sessionId }).containsExactly(1L)
    }

    private fun summary(
        id: Long,
        title: String,
        excerpt: String = "",
        updatedDate: LocalDate = today,
    ): SavedReviewChatSessionSummary =
        SavedReviewChatSessionSummary(
            sessionId = id,
            title = title,
            updatedAt = updatedDate.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
            messageCount = 2,
            latestExcerpt = excerpt,
        )
}
