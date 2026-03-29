package com.mindflow.app.data.model

data class MindFolder(
    val key: String,
    val name: String,
    val colorHex: String,
)

object MindFolderCatalog {
    val all: List<MindFolder> = listOf(
        MindFolder(key = "work", name = "工作", colorHex = "#4C7EFF"),
        MindFolder(key = "life", name = "生活", colorHex = "#3CBF7C"),
        MindFolder(key = "project", name = "项目", colorHex = "#8B6CFF"),
        MindFolder(key = "health", name = "健康", colorHex = "#22B8C7"),
    )

    private val byKey = all.associateBy(MindFolder::key)
    private val byName = all.associateBy(MindFolder::name)
    private val keyAliases = mapOf(
        "fitness" to "health",
    )
    private val nameAliases = mapOf(
        "健身" to "健康",
    )

    fun fromKey(key: String?): MindFolder? = normalizedKey(key)?.let(byKey::get)

    fun fromName(name: String?): MindFolder? =
        name?.trim()?.let { raw ->
            val normalizedName = nameAliases[raw] ?: raw
            byName[normalizedName]
        }

    fun normalizedKey(key: String?): String? {
        val normalized = key?.trim()?.lowercase()?.let { raw ->
            keyAliases[raw] ?: raw
        }
        return normalized?.let(byKey::get)?.key
    }
}

data class FolderSuggestion(
    val folderKey: String? = null,
    val source: FolderSource = FolderSource.RULE,
)

data class FolderExtractionResult(
    val suggestion: FolderSuggestion,
    val notice: String? = null,
)

data class FolderRefreshResult(
    val suggestion: FolderSuggestion? = null,
    val notice: String? = null,
)
