package com.mindflow.app.data.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.weeklyReviewDataStore by preferencesDataStore(name = "mindflow_weekly_review")

data class WeeklyReviewState(
    val lines: List<String> = emptyList(),
    val source: DailyBriefSource = DailyBriefSource.RULE,
    val generatedAt: Long = 0L,
    val weekKey: String = "",
    val updatedCount: Int = 0,
    val progressCount: Int = 0,
    val doneCount: Int = 0,
    val revisitTitle: String = "",
)

data class WeeklyReviewItem(
    val label: String,
    val text: String,
) {
    companion object {
        val labels = listOf("主线", "推进", "重启", "串联")
    }
}

val WeeklyReviewState.items: List<WeeklyReviewItem>
    get() = WeeklyReviewItem.labels.mapIndexedNotNull { index, label ->
        lines.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { WeeklyReviewItem(label, it) }
    }

val WeeklyReviewState.statsLine: String
    get() = buildList {
        add("本周 ${updatedCount} 条")
        if (progressCount > 0) add("推进 ${progressCount} 条")
        if (doneCount > 0) add("完成 ${doneCount} 条")
    }.joinToString(" · ")

class WeeklyReviewPlanner(
    private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    val state: Flow<WeeklyReviewState> = context.weeklyReviewDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            WeeklyReviewState(
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
                weekKey = preferences[WEEK_KEY].orEmpty(),
                updatedCount = preferences[UPDATED_COUNT] ?: 0,
                progressCount = preferences[PROGRESS_COUNT] ?: 0,
                doneCount = preferences[DONE_COUNT] ?: 0,
                revisitTitle = preferences[REVISIT_TITLE].orEmpty(),
            )
        }

    suspend fun refreshIfNeeded(notes: List<NoteEntity>) {
        val weeklyNotes = notes.currentWeekNotes()
        val currentWeekKey = currentWeekKey()
        val currentSignature = buildSignature(weeklyNotes)
        val cached = context.weeklyReviewDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val cachedWeekKey = cached[WEEK_KEY].orEmpty()
        val cachedSignature = cached[SIGNATURE].orEmpty()
        val cachedLines = cached[LINES].orEmpty()
        if (
            cachedWeekKey == currentWeekKey &&
            cachedSignature == currentSignature &&
            cachedLines.isNotBlank()
        ) {
            return
        }

        refreshNow(notes)
    }

    suspend fun refreshNow(notes: List<NoteEntity>): WeeklyReviewState {
        val weeklyNotes = notes.currentWeekNotes()
        val weekKey = currentWeekKey()
        val signature = buildSignature(weeklyNotes)
        val dayKey = LocalDate.now().toString()
        val activeNotes = notes.filter { !it.isArchived }
        val ruleLines = buildRuleReview(weeklyNotes, activeNotes)
        val weeklyStats = buildWeeklyStats(weeklyNotes, activeNotes)
        val settings = aiSettingsRepository.getCurrent()

        if (settings.aiEnabled && settings.isConfigured && weeklyNotes.isNotEmpty()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (val result = aiServiceClient.generateWeeklyReview(settings, buildAiContext(weeklyNotes, notes))) {
                is AiChatResult.Success -> {
                    val aiLines = parseAiLines(result.content)
                    if (aiLines.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        val state = WeeklyReviewState(
                            lines = aiLines,
                            source = DailyBriefSource.AI,
                            generatedAt = System.currentTimeMillis(),
                            weekKey = weekKey,
                            updatedCount = weeklyStats.updatedCount,
                            progressCount = weeklyStats.progressCount,
                            doneCount = weeklyStats.doneCount,
                            revisitTitle = weeklyStats.revisitTitle,
                        )
                        persist(state, signature)
                        return state
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule review.
                }
            }
        }

        val fallback = WeeklyReviewState(
            lines = ruleLines,
            source = DailyBriefSource.RULE,
            generatedAt = System.currentTimeMillis(),
            weekKey = weekKey,
            updatedCount = weeklyStats.updatedCount,
            progressCount = weeklyStats.progressCount,
            doneCount = weeklyStats.doneCount,
            revisitTitle = weeklyStats.revisitTitle,
        )
        persist(fallback, signature)
        return fallback
    }

    private suspend fun persist(
        state: WeeklyReviewState,
        signature: String,
    ) {
        context.weeklyReviewDataStore.edit { preferences ->
            preferences[WEEK_KEY] = state.weekKey
            preferences[LINES] = state.lines.joinToString("\n")
            preferences[SOURCE] = state.source.name
            preferences[GENERATED_AT] = state.generatedAt
            preferences[SIGNATURE] = signature
            preferences[UPDATED_COUNT] = state.updatedCount
            preferences[PROGRESS_COUNT] = state.progressCount
            preferences[DONE_COUNT] = state.doneCount
            preferences[REVISIT_TITLE] = state.revisitTitle
        }
    }

    private fun buildAiContext(
        weeklyNotes: List<NoteEntity>,
        allNotes: List<NoteEntity>,
    ): String {
        val activeNotes = allNotes.filter { !it.isArchived }
        val latestWeekly = weeklyNotes.sortedByDescending { it.updatedAt }.take(10)
        val repeatedTags = weeklyNotes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("、") { "${it.key}(${it.value})" }

        val folderSummary = MindFolderCatalog.all.joinToString("，") { folder ->
            "${folder.name}${weeklyNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == folder.key }}条"
        }

        val weeklySummary = latestWeekly.joinToString("\n") { note ->
            val folderName = MindFolderCatalog.fromKey(note.folderKey)?.name ?: "未分类"
            val tags = note.tags.take(3).joinToString("、").ifBlank { "无标签" }
            "主题:${note.topic}｜状态:${note.status.label}｜文件夹:$folderName｜标签:$tags｜内容:${note.content.compactPreview(120)}"
        }

        return buildString {
            appendLine("你正在为一个人的灵感系统生成本周回看。")
            appendLine("请输出恰好 4 行中文，分别对应：")
            appendLine("1. 本周最明显的主题")
            appendLine("2. 最值得继续推进的方向")
            appendLine("3. 一个值得重启的旧方向或当前最需要补上的断点")
            appendLine("4. 一条有启发性的融合或突破建议")
            appendLine("要求：")
            appendLine("不要机械复述记录原文，不要空话，不要编号。")
            appendLine("整体累计：想法${allNotes.count { it.status == NoteStatus.IDEA }}条，进行中${allNotes.count { it.status == NoteStatus.IN_PROGRESS }}条，已实现${allNotes.count { it.status == NoteStatus.DONE }}条。")
            appendLine("本周分布：$folderSummary")
            if (repeatedTags.isNotBlank()) {
                appendLine("本周高频标签：$repeatedTags")
            }
            val revisitCandidate = buildWeeklyStats(weeklyNotes, activeNotes).revisitTitle
            if (revisitCandidate.isNotBlank()) {
                appendLine("可重启方向：$revisitCandidate")
            }
            appendLine("本周记录：")
            appendLine(weeklySummary)
        }
    }

    private fun buildRuleReview(
        weeklyNotes: List<NoteEntity>,
        activeNotes: List<NoteEntity>,
    ): List<String> {
        if (weeklyNotes.isEmpty()) {
            return listOf(
                "这周还没有形成清晰主线，先记下一条真正想长期推进的事。",
                "从工作、项目、健康里只选一个方向，别同时摊开太多战线。",
                "挑一条停了很久却还值得做的记录，重新写出它为什么重要。",
                "先把工作里的真实问题和项目里的实现思路串起来，形成一条更有突破感的方案。",
            )
        }

        val lines = mutableListOf<String>()
        val dominantFolder = MindFolderCatalog.all
            .maxByOrNull { folder -> weeklyNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == folder.key } }
            ?.takeIf { weeklyNotes.any { note -> MindFolderCatalog.normalizedKey(note.folderKey) == it.key } }
        val repeatedTag = weeklyNotes
            .flatMap { note -> note.tags.distinct().map { tag -> tag to note.updatedAt } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.isNotBlank() }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<Long>>> { it.value.size }
                    .thenByDescending { it.value.maxOrNull() ?: 0L },
            )
            ?.key
        val continueNote = activeNotes
            .filter { it.status == NoteStatus.IN_PROGRESS }
            .maxByOrNull { it.updatedAt }
        val revisitNote = activeNotes
            .filter { it.status != NoteStatus.DONE }
            .filter { it.updatedAt < System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1_000 }
            .minByOrNull { it.updatedAt }
        val ideaHeavy = weeklyNotes.count { it.status == NoteStatus.IDEA } >= weeklyNotes.count { it.status == NoteStatus.IN_PROGRESS } + 2

        if (dominantFolder != null) {
            lines += "本周主线更偏向「${dominantFolder.name}」，可以把分散记录收拢成一个持续推进的主题。"
        }
        if (continueNote != null) {
            lines += "最值得继续推进的是「${continueNote.topic.ifBlank { "未命名记录" }}」，别让它继续停在记录层。"
        }
        if (repeatedTag != null) {
            lines += "你本周反复碰到「$repeatedTag」，可以把它升级成一个更明确的研究问题。"
        } else if (ideaHeavy) {
            lines += "这周新想法明显多于推进动作，下一步应该把一组想法压成一个可验证的小实验。"
        }
        if (revisitNote != null) {
            lines += "「${revisitNote.topic.ifBlank { "未命名记录" }}」已经停了一段时间，值得重启并补一条新的验证动作。"
        }

        if (lines.size < 4) {
            lines += "尝试把工作里的真实问题和项目里的实现思路串起来，形成一条更有突破感的方案。"
        }
        if (lines.size < 4) {
            lines += "如果最近健康和效率记录同时出现，优先找出一个既能改善状态又能提升执行力的微动作。"
        }
        if (lines.size < 4) {
            lines += "别只停留在总结层，至少选一个方向在下周推进到可观察的结果。"
        }
        return lines.distinct().take(4)
    }

    private fun parseAiLines(raw: String): List<String> {
        val normalized = raw
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .removePrefix("3.")
                    .removePrefix("4.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()

        return normalized.take(4)
    }

    private fun buildWeeklyStats(
        weeklyNotes: List<NoteEntity>,
        activeNotes: List<NoteEntity>,
    ): WeeklyStats {
        val revisitTitle = activeNotes
            .filter { it.status != NoteStatus.DONE }
            .filter { it.updatedAt < System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1_000 }
            .minByOrNull { it.updatedAt }
            ?.topic
            .orEmpty()
        return WeeklyStats(
            updatedCount = weeklyNotes.size,
            progressCount = weeklyNotes.count { it.status == NoteStatus.IN_PROGRESS },
            doneCount = weeklyNotes.count { it.status == NoteStatus.DONE },
            revisitTitle = revisitTitle,
        )
    }

    private fun buildSignature(notes: List<NoteEntity>): String =
        "${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private fun currentWeekKey(): String {
        val now = LocalDate.now()
        val weekFields = WeekFields.ISO
        return "${now.get(weekFields.weekBasedYear())}-W${now.get(weekFields.weekOfWeekBasedYear())}"
    }

    private fun List<NoteEntity>.currentWeekNotes(): List<NoteEntity> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val weekStart = today.with(DayOfWeek.MONDAY)
        return filter { note ->
            val noteDate = Instant.ofEpochMilli(note.updatedAt).atZone(zoneId).toLocalDate()
            !note.isArchived && !noteDate.isBefore(weekStart)
        }
    }

    private fun String.compactPreview(maxLength: Int): String =
        replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    private companion object {
        val WEEK_KEY = stringPreferencesKey("week_key")
        val LINES = stringPreferencesKey("lines")
        val SOURCE = stringPreferencesKey("source")
        val GENERATED_AT = longPreferencesKey("generated_at")
        val SIGNATURE = stringPreferencesKey("signature")
        val UPDATED_COUNT = intPreferencesKey("updated_count")
        val PROGRESS_COUNT = intPreferencesKey("progress_count")
        val DONE_COUNT = intPreferencesKey("done_count")
        val REVISIT_TITLE = stringPreferencesKey("revisit_title")
    }

    private data class WeeklyStats(
        val updatedCount: Int,
        val progressCount: Int,
        val doneCount: Int,
        val revisitTitle: String,
    )
}
