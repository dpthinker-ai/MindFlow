package com.mindflow.app.data.brief

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolder
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dailyBriefDataStore by preferencesDataStore(name = "mindflow_daily_brief")

enum class DailyBriefSource {
    LOCAL,
    AI,
    RULE,
}

data class DailyBriefState(
    val lines: List<String> = emptyList(),
    val source: DailyBriefSource = DailyBriefSource.RULE,
    val generatedAt: Long = 0L,
)

class DailyBriefPlanner(
    private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    val state: Flow<DailyBriefState> = context.dailyBriefDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            DailyBriefState(
                lines = preferences[LINES]
                    .orEmpty()
                    .lineSequence()
                    .map(String::trim)
                    .filter { it.isNotBlank() }
                    .toList(),
                source = preferences[SOURCE]
                    ?.let { raw -> DailyBriefSource.entries.firstOrNull { it.name == raw } }
                    ?: DailyBriefSource.RULE,
                generatedAt = preferences[GENERATED_AT] ?: 0L,
            )
        }

    suspend fun refreshIfNeeded(notes: List<NoteEntity>) {
        val todayKey = LocalDate.now().toString()
        val cached = context.dailyBriefDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val cachedDayKey = cached[DAY_KEY].orEmpty()
        val cachedLines = cached[LINES].orEmpty()
        if (cachedDayKey == todayKey && cachedLines.isNotBlank()) {
            return
        }

        refreshNow(notes)
    }

    suspend fun refreshNow(notes: List<NoteEntity>): DailyBriefState {
        val activeNotes = notes.filter { !it.isArchived }
        val ruleLines = buildRuleBrief(activeNotes)
        val todayKey = LocalDate.now().toString()
        val settings = aiSettingsRepository.getCurrent()

        if (settings.aiEnabled && settings.isConfigured && activeNotes.isNotEmpty()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = todayKey,
            )
            when (val result = aiServiceClient.generateDailyBrief(settings, buildAiContext(activeNotes))) {
                is AiChatResult.Success -> {
                    val aiLines = parseAiLines(result.content)
                    if (aiLines.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = todayKey,
                        )
                        val state = DailyBriefState(
                            lines = aiLines,
                            source = DailyBriefSource.AI,
                            generatedAt = System.currentTimeMillis(),
                        )
                        persist(todayKey, state)
                        return state
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule-based prompts.
                }
            }
        }

        val fallback = DailyBriefState(
            lines = ruleLines,
            source = DailyBriefSource.RULE,
            generatedAt = System.currentTimeMillis(),
        )
        persist(todayKey, fallback)
        return fallback
    }

    private suspend fun persist(dayKey: String, state: DailyBriefState) {
        context.dailyBriefDataStore.edit { preferences ->
            preferences[DAY_KEY] = dayKey
            preferences[LINES] = state.lines.joinToString("\n")
            preferences[SOURCE] = state.source.name
            preferences[GENERATED_AT] = state.generatedAt
        }
    }

    private fun buildAiContext(notes: List<NoteEntity>): String {
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(8)
        val repeatedTags = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("、") { "${it.key}(${it.value})" }

        val folderSummary = MindFolderCatalog.all.joinToString("，") { folder ->
            "${folder.name}${notes.count { MindFolderCatalog.normalizedKey(it.folderKey) == folder.key }}条"
        }

        val activeSummary = latestNotes.joinToString("\n") { note ->
            val folderName = MindFolderCatalog.fromKey(note.folderKey)?.name ?: "未分类"
            val tags = note.tags.take(3).joinToString("、").ifBlank { "无标签" }
            "主题:${note.topic}｜状态:${note.status.label}｜文件夹:$folderName｜标签:$tags｜内容:${note.content.compactPreview(120)}"
        }

        return buildString {
            appendLine("你正在分析一个人的长期灵感库，请给出真正有启发性的下一步探索方向。")
            appendLine("整体分布：想法${notes.count { it.status == NoteStatus.IDEA }}条，进行中${notes.count { it.status == NoteStatus.IN_PROGRESS }}条，已实现${notes.count { it.status == NoteStatus.DONE }}条。")
            appendLine("文件夹分布：$folderSummary")
            if (repeatedTags.isNotBlank()) {
                appendLine("高频标签：$repeatedTags")
            }
            appendLine("最近记录：")
            appendLine(activeSummary)
        }
    }

    private fun buildRuleBrief(notes: List<NoteEntity>): List<String> {
        val prompts = mutableListOf<String>()
        val ideaCount = notes.count { it.status == NoteStatus.IDEA }
        val inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS }
        val workCount = notes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "work" }
        val projectCount = notes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "project" }
        val healthCount = notes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "health" }
        val repeatedTag = notes
            .flatMap { note -> note.tags.distinct().map { tag -> tag to note.updatedAt } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.isNotBlank() }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<Long>>> { it.value.size }
                    .thenByDescending { it.value.maxOrNull() ?: 0L },
            )
            ?.takeIf { it.value.size >= 2 }
            ?.key

        if (ideaCount >= (inProgressCount + 2)) {
            prompts += "把最近积累的一组想法压缩成一个 48 小时内能验证的小实验，不要再只停留在记录层。"
        }
        if (workCount >= 2 && projectCount >= 2) {
            prompts += "把工作里的真实问题和项目里的解决思路串起来，尝试沉淀成一条可复用的方法论。"
        }
        if (healthCount >= 2) {
            prompts += "把健康相关记录和 FitEver 联动起来，优先找出一个能直接改善状态的微动作。"
        }
        if (repeatedTag != null) {
            prompts += "你最近反复关注「$repeatedTag」，可以考虑把它升级成一个长期主题，而不是零散点子。"
        }
        val continueFolder: MindFolder? = notes
            .filter { it.status == NoteStatus.IN_PROGRESS }
            .maxByOrNull { it.updatedAt }
            ?.let { MindFolderCatalog.fromKey(it.folderKey) }
        if (continueFolder != null) {
            prompts += "从「${continueFolder.name}」里挑一个正在推进的点，优先把它推到能被验证的程度。"
        }
        if (prompts.isEmpty()) {
            prompts += "先写下今天最值得认真想的一件事，再从里面提炼出一个可行动的下一步。"
        }
        return prompts.distinct().take(2)
    }

    private fun parseAiLines(raw: String): List<String> {
        val normalized = raw
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("-").removePrefix("•").removePrefix("1.").removePrefix("2.").trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (normalized.isNotEmpty()) {
            return normalized.take(2)
        }

        return raw.split("。")
            .map(String::trim)
            .filter { it.isNotBlank() }
            .map { "$it。" }
            .take(2)
    }

    private fun String.compactPreview(maxLength: Int): String =
        replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    private companion object {
        val DAY_KEY = stringPreferencesKey("day_key")
        val LINES = stringPreferencesKey("lines")
        val SOURCE = stringPreferencesKey("source")
        val GENERATED_AT = longPreferencesKey("generated_at")
    }
}
