package com.mindflow.app.data.today

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TodayKnowledgeCompressionPlannerTest {
    @Test
    fun summarize_returnsFallbackWithoutAiDependencies() {
        val fallback = TodayKnowledgeCompressionState(
            mainline = "反复出现的暗线",
            whyNow = "近期记录明显增多",
            settledLine = "已经沉淀的方法",
            settledSupport = "来自本地维护",
            gapLine = "还缺一次验证",
            gapSupport = "来自开放问题",
        )

        val result = TodayKnowledgeCompressionPlanner().summarize(
            mainlineKey = "today:mainline",
            settledKey = "today:settled",
            gapKey = "today:gap",
            mainlineContextSummary = "ignored",
            settledContextSummary = "ignored",
            gapContextSummary = "ignored",
            fallback = fallback,
        )

        assertThat(result).isEqualTo(fallback)
    }
}
