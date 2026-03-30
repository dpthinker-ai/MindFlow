package com.mindflow.app.data.followup

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

private val Context.staleReconnectDataStore by preferencesDataStore(name = "mindflow_stale_reconnect")

data class StaleReconnectState(
    val noteId: Long? = null,
    val noteUpdatedAt: Long = 0L,
    val continueNoteId: Long? = null,
    val bridge: String = "",
    val nextStep: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
    val generatedAt: Long = 0L,
)

class StaleReconnectPlanner(
    private val context: Context,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    val state: Flow<StaleReconnectState> = context.staleReconnectDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            StaleReconnectState(
                noteId = preferences[NOTE_ID]?.takeIf { it > 0L },
                noteUpdatedAt = preferences[NOTE_UPDATED_AT] ?: 0L,
                continueNoteId = preferences[CONTINUE_NOTE_ID]?.takeIf { it > 0L },
                bridge = preferences[BRIDGE].orEmpty(),
                nextStep = preferences[NEXT_STEP].orEmpty(),
                source = preferences[SOURCE]
                    ?.let { raw -> DailyBriefSource.entries.firstOrNull { it.name == raw } }
                    ?: DailyBriefSource.RULE,
                generatedAt = preferences[GENERATED_AT] ?: 0L,
            )
        }

    suspend fun refreshIfNeeded(
        staleNote: NoteEntity?,
        continueNote: NoteEntity?,
        notes: List<NoteEntity>,
    ) {
        if (staleNote == null) return

        val cached = state.first()
        val sameNote = cached.noteId == staleNote.id && cached.noteUpdatedAt == staleNote.updatedAt
        val sameContinue = cached.continueNoteId == continueNote?.id
        val sameDay = cached.generatedAt > 0L && LocalDate.now().toString() == preferencesDayKey()
        if (sameNote && sameContinue && sameDay && cached.bridge.isNotBlank() && cached.nextStep.isNotBlank()) {
            return
        }

        refreshNow(staleNote, continueNote, notes)
    }

    suspend fun refreshNow(
        staleNote: NoteEntity,
        continueNote: NoteEntity?,
        notes: List<NoteEntity>,
    ): StaleReconnectState {
        val fallback = buildRuleReconnect(staleNote, continueNote, notes)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateReconnectGuidance(
                    settings = settings,
                    contextSummary = buildAiContext(staleNote, continueNote, notes),
                )
            ) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.size >= 2) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        val aiState = StaleReconnectState(
                            noteId = staleNote.id,
                            noteUpdatedAt = staleNote.updatedAt,
                            continueNoteId = continueNote?.id,
                            bridge = lines[0],
                            nextStep = lines[1],
                            source = DailyBriefSource.AI,
                            generatedAt = System.currentTimeMillis(),
                        )
                        persist(dayKey, aiState)
                        return aiState
                    }
                }

                is AiChatResult.Failure -> {
                    // Fall back to rules.
                }
            }
        }

        val fallbackState = StaleReconnectState(
            noteId = staleNote.id,
            noteUpdatedAt = staleNote.updatedAt,
            continueNoteId = continueNote?.id,
            bridge = fallback.bridge,
            nextStep = fallback.nextStep,
            source = DailyBriefSource.RULE,
            generatedAt = System.currentTimeMillis(),
        )
        persist(dayKey, fallbackState)
        return fallbackState
    }

    private suspend fun persist(dayKey: String, state: StaleReconnectState) {
        context.staleReconnectDataStore.edit { preferences ->
            preferences[DAY_KEY] = dayKey
            preferences[NOTE_ID] = state.noteId ?: 0L
            preferences[NOTE_UPDATED_AT] = state.noteUpdatedAt
            preferences[CONTINUE_NOTE_ID] = state.continueNoteId ?: 0L
            preferences[BRIDGE] = state.bridge
            preferences[NEXT_STEP] = state.nextStep
            preferences[SOURCE] = state.source.name
            preferences[GENERATED_AT] = state.generatedAt
        }
    }

    private suspend fun preferencesDayKey(): String =
        context.staleReconnectDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()[DAY_KEY].orEmpty()

    private fun buildAiContext(
        staleNote: NoteEntity,
        continueNote: NoteEntity?,
        notes: List<NoteEntity>,
    ): String {
        val staleFolder = MindFolderCatalog.fromKey(staleNote.folderKey)?.name ?: "未分类"
        val staleTags = staleNote.tags.take(3).joinToString("、").ifBlank { "无标签" }
        val repeatedTags = staleNote.tags
            .filter { it.isNotBlank() }
            .filter { candidate -> notes.count { !it.isArchived && candidate in it.tags } >= 2 }
            .distinct()
            .joinToString("、")
        return buildString {
            appendLine("你正在帮助一个人重新接上一条沉下去的记录。")
            appendLine("要求：")
            appendLine("1. 输出两行中文")
            appendLine("2. 第一行说明为什么它现在值得接上，要和这个人当前在推进的方向产生连接")
            appendLine("3. 第二行给出一条最小、今天就能做的接上动作")
            appendLine("4. 不要重复原文，不要空话")
            appendLine("沉下去的记录：")
            appendLine("主题:${staleNote.topic.ifBlank { "未命名记录" }}")
            appendLine("状态:${staleNote.status.label}")
            appendLine("文件夹:$staleFolder")
            appendLine("标签:$staleTags")
            appendLine("内容:${staleNote.content.compactPreview(240)}")
            if (repeatedTags.isNotBlank()) {
                appendLine("这条记录关联的持续主题：$repeatedTags")
            }
            continueNote?.let {
                appendLine("当前正在推进的记录：${it.topic.ifBlank { "未命名记录" }}｜${it.content.compactPreview(120)}")
            }
        }
    }

    private fun buildRuleReconnect(
        staleNote: NoteEntity,
        continueNote: NoteEntity?,
        notes: List<NoteEntity>,
    ): RuleReconnect = RuleReconnect(
        bridge = staleBridgeFor(staleNote, continueNote, notes),
        nextStep = staleNextStepFor(staleNote),
    )

    private fun parseAiLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map(String::trim)
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

    private fun String.compactPreview(maxLength: Int): String =
        replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    private data class RuleReconnect(
        val bridge: String,
        val nextStep: String,
    )

    private companion object {
        val DAY_KEY = stringPreferencesKey("day_key")
        val NOTE_ID = longPreferencesKey("note_id")
        val NOTE_UPDATED_AT = longPreferencesKey("note_updated_at")
        val CONTINUE_NOTE_ID = longPreferencesKey("continue_note_id")
        val BRIDGE = stringPreferencesKey("bridge")
        val NEXT_STEP = stringPreferencesKey("next_step")
        val SOURCE = stringPreferencesKey("source")
        val GENERATED_AT = longPreferencesKey("generated_at")
    }
}

private fun staleBridgeFor(
    note: NoteEntity,
    continueNote: NoteEntity?,
    notes: List<NoteEntity>,
): String {
    val sharedTag = continueNote
        ?.tags
        ?.firstOrNull { candidate -> candidate.isNotBlank() && candidate in note.tags }
    if (sharedTag != null) {
        return "它和你正在推进的内容都围绕「$sharedTag」，现在接回来最容易并到同一条主线。"
    }

    val noteFolder = note.folderKey
    val continueFolder = continueNote?.folderKey
    if (!noteFolder.isNullOrBlank() && noteFolder == continueFolder) {
        val folderName = note.folderName()
        return if (folderName != null) {
            "它和你最近在推的内容都在「$folderName」里，说明这不是一次性念头。"
        } else {
            "它和你最近推进的内容在同一类问题里，值得重新接上。"
        }
    }

    val repeatedTag = note.tags
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                notes.count { active -> !active.isArchived && candidate in active.tags } >= 2
        }
    if (repeatedTag != null) {
        return "它属于「#$repeatedTag」这条持续出现的方向，可以重新补一笔，把线索接回来。"
    }

    return note.folderName()?.let { folderName ->
        "它还落在「$folderName」这类问题里，说明这个方向仍然值得保留。"
    }.orEmpty()
}

private fun staleNextStepFor(note: NoteEntity): String =
    when {
        note.status == NoteStatus.IN_PROGRESS -> "先补一句最新进展，再把它往前拱一小步。"
        note.folderName() == "工作" -> "先把它压成一个最想验证的问题，再补一条更具体的记录。"
        note.folderName() == "项目" -> "先写下一个最小可验证版本，别直接摊开完整方案。"
        note.folderName() == "健康" -> "先把它变成今天就能执行的一次小动作，再看后续反馈。"
        else -> "先补一条更具体的记录，把这个想法重新压回到可推进的状态。"
    }

private fun NoteEntity.folderName(): String? =
    MindFolderCatalog.fromKey(folderKey)?.name
