package com.mindflow.app.data.today

import com.mindflow.app.data.brief.DailyBriefSource

data class TodayKnowledgeCompressionState(
    val mainline: String = "",
    val whyNow: String = "",
    val mainlineSource: DailyBriefSource = DailyBriefSource.RULE,
    val settledLine: String = "",
    val settledSupport: String = "",
    val settledSource: DailyBriefSource = DailyBriefSource.RULE,
    val gapLine: String = "",
    val gapSupport: String = "",
    val gapSource: DailyBriefSource = DailyBriefSource.RULE,
)

class TodayKnowledgeCompressionPlanner {
    fun summarize(
        mainlineKey: String,
        settledKey: String,
        gapKey: String,
        mainlineContextSummary: String,
        settledContextSummary: String,
        gapContextSummary: String,
        fallback: TodayKnowledgeCompressionState,
    ): TodayKnowledgeCompressionState {
        return fallback
    }
}
