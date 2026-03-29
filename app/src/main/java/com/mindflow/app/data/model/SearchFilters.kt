package com.mindflow.app.data.model

data class SearchFilters(
    val query: String = "",
    val tag: String? = null,
    val folderKey: String? = null,
    val status: NoteStatus? = null,
    val timeRange: TimeRange = TimeRange.ALL,
    val includeArchived: Boolean = false,
    val archivedOnly: Boolean = false,
)

enum class TimeRange(val label: String) {
    ALL("全部"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天"),
    LAST_YEAR("近 1 年");

    fun startFrom(now: Long = System.currentTimeMillis()): Long? {
        val dayMillis = 24L * 60L * 60L * 1000L
        return when (this) {
            ALL -> null
            LAST_7_DAYS -> now - 7 * dayMillis
            LAST_30_DAYS -> now - 30 * dayMillis
            LAST_YEAR -> now - 365 * dayMillis
        }
    }
}
