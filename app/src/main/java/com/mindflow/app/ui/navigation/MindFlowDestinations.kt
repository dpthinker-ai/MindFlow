package com.mindflow.app.ui.navigation

import android.net.Uri
import com.mindflow.app.data.model.NoteStatus

object MindFlowDestinations {
    const val FEED = "feed"
    const val FLOW = "flow"
    const val THREAD = "flow/thread/{threadKey}"
    const val THREAD_ARG = "threadKey"
    const val SEARCH_BASE = "search"
    const val SEARCH = "search?status={status}&archivedOnly={archivedOnly}"
    const val SEARCH_STATUS_ARG = "status"
    const val SEARCH_ARCHIVED_ONLY_ARG = "archivedOnly"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture"
    const val FOLDER = "folder/{folderKey}"
    const val FOLDER_ARG = "folderKey"
    const val UNCATEGORIZED_FOLDER = "__uncategorized__"
    const val DETAIL = "detail/{noteId}"
    const val DETAIL_ARG = "noteId"

    fun folderRoute(folderKey: String): String = "folder/$folderKey"
    fun detailRoute(noteId: Long): String = "detail/$noteId"
    fun threadRoute(threadKey: String): String = "flow/thread/${Uri.encode(threadKey)}"
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
