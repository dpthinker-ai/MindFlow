package com.mindflow.app.data.connect

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.fusionSuggestionDataStore by preferencesDataStore(name = "mindflow_fusion_suggestions")

data class FusionSuggestionState(
    val lines: List<String> = emptyList(),
    val source: DailyBriefSource = DailyBriefSource.RULE,
    val generatedAt: Long = 0L,
)

class FusionSuggestionPlanner(
    private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    val state: Flow<FusionSuggestionState> = context.fusionSuggestionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            FusionSuggestionState(
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
        val activeNotes = notes.filter { !it.isArchived }
        val signature = buildSignature(activeNotes)
        val cached = context.fusionSuggestionDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        if (cached[SIGNATURE].orEmpty() == signature && cached[LINES].orEmpty().isNotBlank()) {
            return
        }

        refreshNow(notes)
    }

    suspend fun refreshNow(notes: List<NoteEntity>): FusionSuggestionState {
        val activeNotes = notes.filter { !it.isArchived }
        val threads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes)
        val signature = buildSignature(activeNotes)
        val fallback = NoteConnectionAnalyzer.buildRuleFusionSuggestions(activeNotes, threads)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        if (settings.aiEnabled && settings.isConfigured && activeNotes.isNotEmpty()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (val result = aiServiceClient.generateFusionSuggestions(settings, buildAiContext(activeNotes, threads))) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        val state = FusionSuggestionState(
                            lines = lines,
                            source = DailyBriefSource.AI,
                            generatedAt = System.currentTimeMillis(),
                        )
                        persist(state, signature)
                        return state
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule suggestions.
                }
            }
        }

        val fallbackState = FusionSuggestionState(
            lines = fallback,
            source = DailyBriefSource.RULE,
            generatedAt = System.currentTimeMillis(),
        )
        persist(fallbackState, signature)
        return fallbackState
    }

    private suspend fun persist(
        state: FusionSuggestionState,
        signature: String,
    ) {
        context.fusionSuggestionDataStore.edit { preferences ->
            preferences[LINES] = state.lines.joinToString("\n")
            preferences[SOURCE] = state.source.name
            preferences[GENERATED_AT] = state.generatedAt
            preferences[SIGNATURE] = signature
        }
    }

    private fun buildAiContext(
        notes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): String {
        val folderSummary = MindFolderCatalog.all.joinToString("，") { folder ->
            "${folder.name}${notes.count { MindFolderCatalog.normalizedKey(it.folderKey) == folder.key }}条"
        }
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(8)
        return buildString {
            appendLine("你正在为一个人的灵感系统生成融合建议。")
            appendLine("请输出恰好 2 行中文，每行都是一个值得进一步探索的融合方向。")
            appendLine("要求：")
            appendLine("1. 不是总结")
            appendLine("2. 不是复述旧记录")
            appendLine("3. 要体现把多个方向串起来后的新价值")
            appendLine("整体分布：想法${notes.count { it.status == NoteStatus.IDEA }}条，进行中${notes.count { it.status == NoteStatus.IN_PROGRESS }}条，已实现${notes.count { it.status == NoteStatus.DONE }}条。")
            appendLine("文件夹分布：$folderSummary")
            if (threads.isNotEmpty()) {
                appendLine("当前主题线程：")
                threads.forEach { thread ->
                    appendLine("${thread.title}｜${thread.noteCount}条｜${thread.summary}")
                }
            }
            appendLine("最近记录：")
            latestNotes.forEach { note ->
                appendLine("${note.topic}｜${note.tags.take(3).joinToString("、").ifBlank { "无标签" }}｜${note.content.replace('\n', ' ').take(100)}")
            }
        }
    }

    private fun parseAiLines(raw: String): List<String> =
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

    private fun buildSignature(notes: List<NoteEntity>): String =
        "${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private companion object {
        val LINES = stringPreferencesKey("lines")
        val SOURCE = stringPreferencesKey("source")
        val GENERATED_AT = longPreferencesKey("generated_at")
        val SIGNATURE = stringPreferencesKey("signature")
    }
}
