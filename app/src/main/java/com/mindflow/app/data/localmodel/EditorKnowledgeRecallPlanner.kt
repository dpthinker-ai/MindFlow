package com.mindflow.app.data.localmodel

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.topic.AiChatResult

data class EditorKnowledgeRecallResult(
    val line: String,
    val support: String = "",
    val anchorNoteId: Long? = null,
    val anchorLabel: String = "",
    val usedOnDevice: Boolean = false,
)

class EditorKnowledgeRecallPlanner(
    private val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    private val onDeviceAiClient: OnDeviceAiClient,
) {
    suspend fun summarize(
        draftTopic: String,
        draftContent: String,
        suggestedThreadTitle: String?,
        relatedNotes: List<NoteEntity>,
    ): EditorKnowledgeRecallResult? {
        val trimmedContent = draftContent.trim()
        val trimmedTopic = draftTopic.trim()
        if (trimmedContent.length < 12 && trimmedTopic.length < 4) return null
        if (relatedNotes.isEmpty() && suggestedThreadTitle.isNullOrBlank()) return null

        val anchor = relatedNotes.firstOrNull()
        val fallback = buildFallback(
            suggestedThreadTitle = suggestedThreadTitle,
            anchor = anchor,
        )

        val settings = onDeviceModelSettingsRepository.getCurrent()
        if (!settings.preferOnDevice || !settings.isReady) return fallback

        val contextSummary = buildContextSummary(
            draftTopic = trimmedTopic,
            draftContent = trimmedContent,
            suggestedThreadTitle = suggestedThreadTitle,
            relatedNotes = relatedNotes,
        )

        return when (val result = onDeviceAiClient.generateEditorRecall(settings, contextSummary)) {
            is AiChatResult.Success -> {
                val lines = parseLines(result.content)
                EditorKnowledgeRecallResult(
                    line = lines.getOrElse(0) { fallback.line }.ifBlank { fallback.line },
                    support = lines.getOrElse(1) { fallback.support }.ifBlank { fallback.support },
                    anchorNoteId = anchor?.id,
                    anchorLabel = anchor?.topic?.ifBlank { "相关记录" }.orEmpty(),
                    usedOnDevice = true,
                )
            }
            is AiChatResult.Failure -> fallback
        }
    }

    private fun buildFallback(
        suggestedThreadTitle: String?,
        anchor: NoteEntity?,
    ): EditorKnowledgeRecallResult {
        val line = when {
            !suggestedThreadTitle.isNullOrBlank() && anchor != null ->
                "这条新记录更像是在接上「$suggestedThreadTitle」这条主线，也和「${anchor.topic.ifBlank { "一条旧记录" }}」讲的是同一个问题。"
            !suggestedThreadTitle.isNullOrBlank() ->
                "这条新记录更像是在接上「$suggestedThreadTitle」这条主线，而不是一个完全新的点。"
            anchor != null ->
                "你以前已经在「${anchor.topic.ifBlank { "一条旧记录" }}」里碰过类似问题，这条更像是在继续那条线。"
            else -> "这条记录和你已有积累是连着的，不像一个完全孤立的新点。"
        }
        val support = anchor?.content
            ?.replace("\n", " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(60)
            ?.let { "最接近的一条旧记录：$it" }
            .orEmpty()
        return EditorKnowledgeRecallResult(
            line = line,
            support = support,
            anchorNoteId = anchor?.id,
            anchorLabel = anchor?.topic?.ifBlank { "相关记录" }.orEmpty(),
            usedOnDevice = false,
        )
    }

    private fun buildContextSummary(
        draftTopic: String,
        draftContent: String,
        suggestedThreadTitle: String?,
        relatedNotes: List<NoteEntity>,
    ): String = buildString {
        appendLine("草稿主题：${draftTopic.ifBlank { "未命名" }}")
        appendLine("草稿正文：${draftContent.take(420)}")
        suggestedThreadTitle?.takeIf { it.isNotBlank() }?.let {
            appendLine("候选主线：$it")
        }
        appendLine("相关旧记录：")
        relatedNotes.take(4).forEachIndexed { index, note ->
            appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
        }
    }

    private fun parseLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(2)
            .toList()
}
