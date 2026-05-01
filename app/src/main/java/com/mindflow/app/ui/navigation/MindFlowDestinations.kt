package com.mindflow.app.ui.navigation

import android.net.Uri
import com.mindflow.app.data.model.NoteStatus

object MindFlowDestinations {
    const val FEED = "feed"
    const val FLOW_TODAY = "flow/today"
    const val FLOW_REVIEW = "flow/review"
    const val FLOW_GRAPH = "flow/graph"
    const val THREAD = "flow/thread/{threadKey}"
    const val THREAD_ARG = "threadKey"
    const val SEARCH_BASE = "search"
    const val SEARCH = "search?status={status}&archivedOnly={archivedOnly}"
    const val SEARCH_STATUS_ARG = "status"
    const val SEARCH_ARCHIVED_ONLY_ARG = "archivedOnly"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture/{captureSeedId}"
    const val CAPTURE_ARG = "captureSeedId"
    const val REVIEW_CHAT = "review-chat/{reviewChatSeedId}"
    const val REVIEW_CHAT_ARG = "reviewChatSeedId"
    const val REVIEW_CHAT_HISTORY = "review-chat-history"
    const val FOLDER = "folder/{folderKey}"
    const val FOLDER_ARG = "folderKey"
    const val UNCATEGORIZED_FOLDER = "__uncategorized__"
    const val DETAIL = "detail/{noteId}"
    const val DETAIL_ARG = "noteId"

    fun folderRoute(folderKey: String): String = "folder/$folderKey"
    fun detailRoute(noteId: Long): String = "detail/$noteId"
    fun threadRoute(threadKey: String): String = "flow/thread/${Uri.encode(threadKey)}"
    fun graphRoute(): String = FLOW_GRAPH
    fun captureRoute(seedId: Long): String = "capture/$seedId"
    fun reviewChatRoute(seedId: Long): String = "review-chat/$seedId"
    fun flowRoute(focus: FlowFocus? = null): String =
        when (focus) {
            FlowFocus.REVIEW,
            FlowFocus.RECONNECT -> FLOW_REVIEW
            FlowFocus.MAINLINE,
            FlowFocus.DIRECTION -> FLOW_TODAY
            FlowFocus.TODAY,
            null -> FLOW_TODAY
        }
    fun searchRoute(
        status: NoteStatus? = null,
        archivedOnly: Boolean = false,
    ): String {
        val params = buildList {
            status?.let { add("$SEARCH_STATUS_ARG=${it.name}") }
            if (archivedOnly) add("$SEARCH_ARCHIVED_ONLY_ARG=true")
        }
        return if (params.isEmpty()) SEARCH_BASE else "$SEARCH_BASE?${params.joinToString("&")}"
    }
}
