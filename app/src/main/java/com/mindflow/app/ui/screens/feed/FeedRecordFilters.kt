package com.mindflow.app.ui.screens.feed

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.ui.components.RecordKind
import com.mindflow.app.ui.components.inferRecordKind

enum class FeedQuickFilter(val label: String) {
    ALL("全部"),
    IDEA("想法"),
    LINK("链接"),
    TASK("任务"),
    VOICE("语音"),
}

internal fun filterFeedNotes(
    notes: List<NoteEntity>,
    query: String,
    filter: FeedQuickFilter,
): List<NoteEntity> = notes
    .filter { it.matchesFeedSearch(query) }
    .filter { it.matchesFeedQuickFilter(filter) }

internal fun NoteEntity.matchesFeedSearch(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true
    return topic.lowercase().contains(normalizedQuery) ||
        content.lowercase().contains(normalizedQuery) ||
        tags.any { it.lowercase().contains(normalizedQuery) }
}

internal fun NoteEntity.matchesFeedQuickFilter(filter: FeedQuickFilter): Boolean = when (filter) {
    FeedQuickFilter.ALL -> true
    FeedQuickFilter.IDEA -> status == NoteStatus.IDEA && inferRecordKind() == RecordKind.TEXT
    FeedQuickFilter.LINK -> inferRecordKind() == RecordKind.LINK
    FeedQuickFilter.TASK -> status == NoteStatus.IN_PROGRESS || status == NoteStatus.DONE
    FeedQuickFilter.VOICE -> inferRecordKind() == RecordKind.VOICE
}
