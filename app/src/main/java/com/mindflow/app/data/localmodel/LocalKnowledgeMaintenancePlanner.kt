package com.mindflow.app.data.localmodel

import android.content.Context
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalKnowledgeMaintenancePlanner(
    context: Context,
    private val noteRepository: NoteRepository,
    private val directionWikiCoordinator: DirectionWikiCoordinator,
    private val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    private val onDeviceAiClient: OnDeviceAiClient,
    private val applicationScope: CoroutineScope,
) {
    private val rootDir = File(context.filesDir, "knowledge-layer/wiki/local-maintainer")
    private val refreshIntervalMs = 18L * 60L * 60L * 1000L

    fun maintainInBackgroundIfNeeded() {
        val latest = File(rootDir, "latest.md")
        val now = System.currentTimeMillis()
        if (latest.exists() && now - latest.lastModified() < refreshIntervalMs) return
        applicationScope.launch {
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow(): Result<Unit> = runCatching {
        val settings = onDeviceModelSettingsRepository.getCurrent()
        if (!settings.preferOnDevice || !settings.isReady) return@runCatching

        val notes = noteRepository.observeAllNotes().first().filter { !it.isArchived }
        val snapshot = directionWikiCoordinator.snapshot.value

        val recentNotes = notes.sortedByDescending { it.updatedAt }.take(6)
        val topDirections = snapshot.directions.values.sortedByDescending { it.updatedAt }.take(3)
        if (recentNotes.isEmpty() && topDirections.isEmpty()) return@runCatching

        val mainlineContext = buildString {
            appendLine("最近新材料：")
            recentNotes.forEachIndexed { index, note ->
                appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
            }
            appendLine("已有方向：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.conclusionLine.ifBlank { direction.assetSummary }.ifBlank { direction.healthLine }}")
            }
        }
        val settledContext = buildString {
            appendLine("已成立方向结果：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.conclusionLine.ifBlank { direction.validatedPoints.firstOrNull().orEmpty() }.ifBlank { direction.verifiedPoints.firstOrNull().orEmpty() }.ifBlank { direction.assetSummary }}")
            }
            appendLine("最近稳定材料：")
            recentNotes.filter { it.knowledgeTrust.name == "VALIDATED" || it.knowledgeTrust.name == "VERIFIED" }
                .take(4)
                .forEachIndexed { index, note ->
                    appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
                }
        }
        val gapContext = buildString {
            appendLine("当前张力：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.openQuestions.firstOrNull().orEmpty().ifBlank { direction.maintenanceLine }.ifBlank { direction.healthLine }}")
            }
            appendLine("最近还没压实的新材料：")
            recentNotes.take(4).forEachIndexed { index, note ->
                appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
            }
        }

        val mainline = onDeviceAiClient.generateFlowMainline(settings, mainlineContext)
        val settled = onDeviceAiClient.generateFlowSettledKnowledge(settings, settledContext)
        val gap = onDeviceAiClient.generateFlowBreakthroughGap(settings, gapContext)

        val date = LocalDate.now().toString()
        val latestFile = File(rootDir, "latest.md")
        val dailyFile = File(rootDir, "$date.md")
        withContext(Dispatchers.IO) {
            rootDir.mkdirs()
            val content = buildMarkdown(
                date = date,
                mainline = mainline,
                settled = settled,
                gap = gap,
            )
            latestFile.writeText(content)
            dailyFile.writeText(content)
        }
    }

    private fun buildMarkdown(
        date: String,
        mainline: AiChatResult,
        settled: AiChatResult,
        gap: AiChatResult,
    ): String = buildString {
        appendLine("# Local maintainer")
        appendLine()
        appendLine("- date: $date")
        appendLine("- model: Gemma 4 E4B")
        appendLine()
        appendSection("当前综合判断", mainline)
        appendSection("最近吸收", settled)
        appendSection("待厘清", gap)
    }

    private fun StringBuilder.appendSection(
        title: String,
        result: AiChatResult,
    ) {
        appendLine("## $title")
        when (result) {
            is AiChatResult.Success -> appendLine(result.content.trim())
            is AiChatResult.Failure -> appendLine(result.message)
        }
        appendLine()
    }
}
