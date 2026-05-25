package com.mindflow.app.ui.screens.today

import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary

data class TodayReviewPreview(
    val title: String,
    val description: String,
    val savedSessionId: Long?,
) {
    companion object {
        val Empty = TodayReviewPreview(
            title = "今日回看",
            description = "打开回看历史，手动选择一个问题再继续",
            savedSessionId = null,
        )
    }
}

fun SavedReviewChatSessionSummary.toTodayReviewPreview(): TodayReviewPreview {
    val titleText = title.cleanTodayVisibleText()
    val excerptText = latestExcerpt.cleanTodayVisibleText()
    val combined = when {
        titleText.isBlank() -> excerptText
        excerptText.isBlank() || excerptText == titleText -> titleText
        else -> "$titleText：$excerptText"
    }.asTodayDesignPreview(72)
    return TodayReviewPreview(
        title = "今日回看",
        description = combined.ifBlank { TodayReviewPreview.Empty.description },
        savedSessionId = sessionId,
    )
}
