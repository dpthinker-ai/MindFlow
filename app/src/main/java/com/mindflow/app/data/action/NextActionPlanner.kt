package com.mindflow.app.data.action

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nextActionDataStore by preferencesDataStore(name = "mindflow_next_action")

data class NextActionState(
    val noteId: Long? = null,
    val noteUpdatedAt: Long = 0L,
    val text: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
    val generatedAt: Long = 0L,
)

class NextActionPlanner(
    private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    val state: Flow<NextActionState> = context.nextActionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            NextActionState(
                noteId = preferences[NOTE_ID]?.takeIf { it > 0L },
                noteUpdatedAt = preferences[NOTE_UPDATED_AT] ?: 0L,
                text = preferences[TEXT].orEmpty(),
                source = preferences[SOURCE]
                    ?.let { raw -> DailyBriefSource.entries.firstOrNull { it.name == raw } }
                    ?: DailyBriefSource.RULE,
                generatedAt = preferences[GENERATED_AT] ?: 0L,
            )
        }

    suspend fun refreshIfNeeded(note: NoteEntity?) {
        if (note == null) {
            return
        }

        val cached = state.first()
        if (cached.noteId == note.id && cached.noteUpdatedAt == note.updatedAt && cached.text.isNotBlank()) {
            return
        }

        refreshNow(note)
    }

    suspend fun refreshNow(note: NoteEntity): NextActionState {
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()
        val fallbackText = buildRuleAction(note)

        if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (val result = aiServiceClient.generateNextAction(settings, buildAiContext(note))) {
                is AiChatResult.Success -> {
                    val actionText = normalize(result.content)
                    if (actionText.isNotBlank()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        val aiState = NextActionState(
                            noteId = note.id,
                            noteUpdatedAt = note.updatedAt,
                            text = actionText,
                            source = DailyBriefSource.AI,
                            generatedAt = System.currentTimeMillis(),
                        )
                        persist(aiState)
                        return aiState
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rule-based action.
                }
            }
        }

        val fallbackState = NextActionState(
            noteId = note.id,
            noteUpdatedAt = note.updatedAt,
            text = fallbackText,
            source = DailyBriefSource.RULE,
            generatedAt = System.currentTimeMillis(),
        )
        persist(fallbackState)
        return fallbackState
    }

    private suspend fun persist(state: NextActionState) {
        context.nextActionDataStore.edit { preferences ->
            preferences[NOTE_ID] = state.noteId ?: 0L
            preferences[NOTE_UPDATED_AT] = state.noteUpdatedAt
            preferences[TEXT] = state.text
            preferences[SOURCE] = state.source.name
            preferences[GENERATED_AT] = state.generatedAt
        }
    }

    private fun buildAiContext(note: NoteEntity): String {
        val folderName = MindFolderCatalog.fromKey(note.folderKey)?.name ?: "未分类"
        val tags = note.tags.take(3).joinToString("、").ifBlank { "无标签" }
        return buildString {
            appendLine("请基于这条记录，给出一个真正可以执行的下一步动作。")
            appendLine("要求：")
            appendLine("1. 只给一个动作")
            appendLine("2. 动作必须具体、可执行、可验证")
            appendLine("3. 不要复述原文")
            appendLine("4. 尽量控制在一行中文里")
            appendLine("记录信息：")
            appendLine("主题:${note.topic}")
            appendLine("状态:${note.status.label}")
            appendLine("文件夹:$folderName")
            appendLine("标签:$tags")
            appendLine("内容:${note.content.compactPreview(1800)}")
        }
    }

    private fun buildRuleAction(note: NoteEntity): String {
        val folder = MindFolderCatalog.fromKey(note.folderKey)?.name
        return when {
            note.status.name == "IN_PROGRESS" -> "把这条记录拆成一个今天能完成的小动作，并补上验证结果。"
            folder == "工作" -> "先把这条记录整理成一个可验证的问题定义，再决定下一步方案。"
            folder == "项目" -> "先做一个最小可运行或可验证的版本，快速确认方向值不值得继续。"
            folder == "健康" -> "先把这条想法变成今天就能执行的一次微小健康动作。"
            else -> "先把这条想法提炼成一个今天能推进的最小动作。"
        }
    }

    private fun normalize(raw: String): String =
        raw
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .removePrefix("-")
            .removePrefix("•")
            .removePrefix("1.")
            .trim()

    private fun String.compactPreview(maxLength: Int): String =
        replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    private companion object {
        val NOTE_ID = longPreferencesKey("note_id")
        val NOTE_UPDATED_AT = longPreferencesKey("note_updated_at")
        val TEXT = stringPreferencesKey("text")
        val SOURCE = stringPreferencesKey("source")
        val GENERATED_AT = longPreferencesKey("generated_at")
    }
}
