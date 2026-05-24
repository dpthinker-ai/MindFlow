package com.mindflow.app.ui.screens.feed

import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.CaptureMode
import com.mindflow.app.ui.navigation.MindFlowEntryIntents

internal enum class FeedCaptureAction {
    TEXT,
    VOICE,
    IMAGE,
    LINK,
}

internal fun FeedCaptureAction.toCaptureSeed(): CaptureSeed = when (this) {
    FeedCaptureAction.TEXT -> CaptureSeed()
    FeedCaptureAction.VOICE -> MindFlowEntryIntents.defaultVoiceCaptureSeed()
    FeedCaptureAction.IMAGE -> CaptureSeed(
        mode = CaptureMode.IMAGE,
        initialTopic = "图片记录",
        initialContent = "图片：\n补充说明：",
        initialTags = listOf("图片"),
    )
    FeedCaptureAction.LINK -> CaptureSeed(
        mode = CaptureMode.ARTICLE,
        initialTopic = "文章收藏",
        initialContent = "链接：\n补充说明：",
        initialTags = listOf("文章"),
    )
}
