package com.mindflow.app.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindflow.app.EntryProxyActivity
import com.mindflow.app.MindFlowApplication
import com.mindflow.app.R
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThreadResearchAnalyzer
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.ui.navigation.FlowFocus
import com.mindflow.app.ui.navigation.MindFlowEntryIntents
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!hasNotificationPermission()) {
            return Result.success()
        }

        val app = applicationContext as? MindFlowApplication ?: return Result.failure()
        val container = app.appContainer
        val kind = inputData.getString(KEY_KIND)
            ?.let { raw -> ReminderKind.entries.firstOrNull { it.name == raw } }
            ?: return Result.failure()

        val notes = container.noteRepository.observeAllNotes().first()
        val activeNotes = notes.filter { !it.isArchived }
        val continueNote = pickContinueNote(activeNotes)
        val staleNote = pickStaleNote(activeNotes, continueNote?.id)
        val researchContext = buildResearchReminderContext(
            note = continueNote ?: staleNote,
            activeNotes = activeNotes,
        )

        createChannel()

        val payload = when (kind) {
            ReminderKind.MORNING -> buildMorningPayload(
                container = container,
                notes = activeNotes,
                continueNote = continueNote,
                staleNote = staleNote,
                researchContext = researchContext,
            )
            ReminderKind.EVENING -> buildEveningPayload(activeNotes)
        }

        val actions = when (kind) {
            ReminderKind.MORNING -> buildMorningActions(
                activeNotes = activeNotes,
                continueNote = continueNote,
                staleNote = staleNote,
                researchContext = researchContext,
            )
            ReminderKind.EVENING -> buildEveningActions(activeNotes)
        }

        notify(kind, payload.title, payload.body, actions, payload.openNoteId, payload.flowFocus)
        return Result.success()
    }

    private suspend fun buildMorningPayload(
        container: com.mindflow.app.di.AppContainer,
        notes: List<NoteEntity>,
        continueNote: NoteEntity?,
        staleNote: NoteEntity?,
        researchContext: ResearchReminderContext?,
    ): NotificationPayload {
        container.dailyBriefPlanner.refreshIfNeeded(notes)
        val brief = container.dailyBriefPlanner.state.first()

        val nextActionText = if (continueNote != null) {
            container.nextActionPlanner.refreshIfNeeded(continueNote)
            val nextActionState = container.nextActionPlanner.state.first()
            if (
                nextActionState.noteId == continueNote.id &&
                nextActionState.noteUpdatedAt == continueNote.updatedAt
            ) {
                nextActionState.text
            } else {
                ""
            }
        } else {
            ""
        }

        val title = continueNote?.topic?.takeIf { it.isNotBlank() }?.let { "今天先推进：$it" }
            ?: "今天先记下一条真正重要的想法"

        val body = buildList {
            if (nextActionText.isNotBlank()) {
                add("下一步：$nextActionText")
            }
            researchContext?.followUpReason
                ?.takeIf { it.isNotBlank() }
                ?.let { add("现在值得验证：$it") }
            researchContext?.validationStep
                ?.takeIf { it.isNotBlank() }
                ?.let { add("研究验证：$it") }
            brief.lines.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add("探索：$it") }
            if (continueNote == null) {
                staleNote?.topic?.takeIf { it.isNotBlank() }?.let { add("重新接上：$it") }
                staleNote?.let { note ->
                    buildReminderNextStep(note).takeIf { it.isNotBlank() }?.let { add("先做：$it") }
                }
            }
        }.ifEmpty {
            listOf("别让念头只来过一次，先抓住今天最值得写下的一件事。")
        }.joinToString("\n")

        val openNoteId = continueNote?.id ?: staleNote?.id
        val hasTargetNote = openNoteId != null && openNoteId > 0L
        val flowFocus = when {
            continueNote != null -> FlowFocus.TODAY
            staleNote != null -> FlowFocus.RECONNECT
            else -> FlowFocus.TODAY
        }
        return NotificationPayload(
            title = title,
            body = body,
            openNoteId = openNoteId,
            flowFocus = if (hasTargetNote) null else flowFocus,
        )
    }

    private fun buildEveningPayload(
        notes: List<NoteEntity>,
    ): NotificationPayload {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val todayNotes = notes.filter { it.createdAt.toLocalDate(zoneId) == today }
        val latestToday = todayNotes.maxByOrNull { it.updatedAt }
        val inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS }

        return when {
            todayNotes.isEmpty() -> NotificationPayload(
                title = "今晚留下一条记录",
                body = "哪怕只写一句，也能给明天的自己留下一个起点。",
            )
            latestToday != null -> NotificationPayload(
                title = "今天记了 ${todayNotes.size} 条",
                body = buildString {
                    append("最值得接着看的：${latestToday.topic.ifBlank { "未命名记录" }}")
                    if (inProgressCount > 0) {
                        append("\n当前还有 $inProgressCount 条在推进中，明天继续往前拱一步。")
                    }
                },
                openNoteId = latestToday.id,
            )
            else -> NotificationPayload(
                title = "今天记了 ${todayNotes.size} 条",
                body = "睡前挑一条最值得继续推进的记录，明天会更容易接上。",
                flowFocus = FlowFocus.REVIEW,
            )
        }
    }

    private fun notify(
        kind: ReminderKind,
        title: String,
        body: String,
        actions: List<ReminderAction>,
        openNoteId: Long? = null,
        flowFocus: FlowFocus? = null,
    ) {
        val launchIntent = Intent(applicationContext, EntryProxyActivity::class.java).apply {
            action = MindFlowEntryIntents.ACTION_OPEN_FLOW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            openNoteId?.takeIf { it > 0L }?.let { putExtra(MindFlowEntryIntents.EXTRA_NOTE_ID, it) }
            flowFocus?.let { putExtra(MindFlowEntryIntents.EXTRA_FLOW_FOCUS, it.name) }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            kind.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        var notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome_inset)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        actions.forEach { action ->
            notification = notification.addAction(
                R.drawable.ic_launcher_monochrome_inset,
                action.label,
                action.pendingIntent,
            )
        }

        NotificationManagerCompat.from(applicationContext).notify(kind.ordinal + 701, notification.build())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MindFlow Daily Brief",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "晨间 brief 与晚间 review 提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

    data class NotificationPayload(
        val title: String,
        val body: String,
        val openNoteId: Long? = null,
        val flowFocus: FlowFocus? = null,
    )

    private fun quickActionPendingIntent(
        kind: ReminderKind,
        action: String,
        requestCodeOffset: Int,
        openNoteId: Long? = null,
        openThreadKey: String = "",
        captureTopic: String = "",
        captureContent: String = "",
        flowFocus: FlowFocus? = null,
    ): PendingIntent {
        val intent = Intent(applicationContext, EntryProxyActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            openNoteId?.takeIf { it > 0L }?.let { putExtra(MindFlowEntryIntents.EXTRA_NOTE_ID, it) }
            if (openThreadKey.isNotBlank()) {
                putExtra(MindFlowEntryIntents.EXTRA_THREAD_KEY, openThreadKey)
            }
            flowFocus?.let {
                putExtra(MindFlowEntryIntents.EXTRA_FLOW_FOCUS, it.name)
            }
            if (captureTopic.isNotBlank()) {
                putExtra(MindFlowEntryIntents.EXTRA_CAPTURE_TOPIC, captureTopic)
            }
            if (captureContent.isNotBlank()) {
                putExtra(MindFlowEntryIntents.EXTRA_CAPTURE_CONTENT, captureContent)
            }
        }
        return PendingIntent.getActivity(
            applicationContext,
            kind.ordinal + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildMorningActions(
        activeNotes: List<NoteEntity>,
        continueNote: NoteEntity?,
        staleNote: NoteEntity?,
        researchContext: ResearchReminderContext?,
    ): List<ReminderAction> {
        val targetNote = continueNote ?: staleNote
        val threadKey = targetNote?.let { note ->
            NoteConnectionAnalyzer.bestThreadKeyFor(
                note = note,
                notes = activeNotes,
            )
        }
        val openLabel = if (continueNote != null) "继续推进" else if (staleNote != null) "重新接上" else "打开 Flow"
        val seedContent = when {
            researchContext != null -> buildResearchValidationSeed(researchContext)
            continueNote != null -> buildProgressSeed(continueNote)
            staleNote != null -> buildReconnectSeed(staleNote)
            else -> ""
        }
        val seedTopic = researchContext?.topic ?: targetNote?.topic.orEmpty()
        return buildList {
            add(
                ReminderAction(
                    label = if (threadKey != null) "打开方向" else openLabel,
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.MORNING,
                        action = MindFlowEntryIntents.ACTION_OPEN_FLOW,
                        requestCodeOffset = 100,
                        openNoteId = if (threadKey == null) targetNote?.id else null,
                        openThreadKey = threadKey.orEmpty(),
                        flowFocus = when {
                            continueNote != null -> FlowFocus.TODAY
                            staleNote != null && threadKey == null -> FlowFocus.RECONNECT
                            else -> FlowFocus.TODAY
                        },
                    ),
                ),
            )
            add(
                ReminderAction(
                    label = when {
                        researchContext != null -> "记验证"
                        seedContent.isNotBlank() -> "补一条"
                        else -> "记一条"
                    },
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.MORNING,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE,
                        requestCodeOffset = 150,
                        captureTopic = seedTopic,
                        captureContent = seedContent,
                    ),
                ),
            )
            add(
                ReminderAction(
                    label = "语音记",
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.MORNING,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE_VOICE,
                        requestCodeOffset = 200,
                        captureTopic = seedTopic,
                        captureContent = seedContent,
                    ),
                ),
            )
        }
    }

    private fun buildEveningActions(activeNotes: List<NoteEntity>): List<ReminderAction> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val todayNotes = activeNotes.filter { it.createdAt.toLocalDate(zoneId) == today }
        val latestToday = todayNotes.maxByOrNull { it.updatedAt }
        val seedContent = buildEveningSeed(latestToday)
        return buildList {
            add(
                ReminderAction(
                    label = if (latestToday != null) "打开记录" else "打开 Flow",
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.EVENING,
                        action = MindFlowEntryIntents.ACTION_OPEN_FLOW,
                        requestCodeOffset = 100,
                        openNoteId = latestToday?.id,
                        flowFocus = if (latestToday == null) FlowFocus.REVIEW else null,
                    ),
                ),
            )
            add(
                ReminderAction(
                    label = if (latestToday != null) "记回看" else "记一条",
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.EVENING,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE,
                        requestCodeOffset = 150,
                        captureTopic = if (latestToday != null) "今晚回看" else "",
                        captureContent = seedContent,
                    ),
                ),
            )
            add(
                ReminderAction(
                    label = "语音记",
                    pendingIntent = quickActionPendingIntent(
                        kind = ReminderKind.EVENING,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE_VOICE,
                        requestCodeOffset = 200,
                        captureTopic = if (latestToday != null) "今晚回看" else "",
                        captureContent = seedContent,
                    ),
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "mindflow_daily_brief"
        const val KEY_KIND = "kind"
    }
}

private data class ReminderAction(
    val label: String,
    val pendingIntent: PendingIntent,
)

private data class ResearchReminderContext(
    val threadKey: String,
    val threadTitle: String,
    val label: String,
    val validationStep: String,
    val followUpReason: String,
) {
    val topic: String
        get() = "${threadTitle.removePrefix("#").trim()} · 验证动作"
}

private fun buildProgressSeed(note: NoteEntity): String =
    buildString {
        appendLine("围绕「${note.topic.ifBlank { "未命名记录" }}」补一条最新进展：")
        appendLine("- 今天的进展：")
        appendLine("- 当前卡点：")
        appendLine("- 下一步：")
    }

private fun buildReconnectSeed(note: NoteEntity): String =
    buildString {
        appendLine("围绕「${note.topic.ifBlank { "未命名记录" }}」重新接上一条记录：")
        appendLine("- 现在重新看它的原因：")
        appendLine("- 今天先做的一步：")
        appendLine("- 这次新的判断：")
    }

private fun buildEveningSeed(note: NoteEntity?): String =
    buildString {
        appendLine("今晚给今天留一条回看：")
        note?.topic?.takeIf { it.isNotBlank() }?.let {
            appendLine("- 今天最值得接着看的：$it")
        }
        appendLine("- 今天最重要的变化：")
        appendLine("- 新的判断：")
        appendLine("- 明天第一步：")
    }

private fun buildResearchValidationSeed(context: ResearchReminderContext): String =
    buildString {
        appendLine("围绕「${context.threadTitle.removePrefix("#").trim()}」记一条验证记录：")
        appendLine("- 研究线索：${context.label}")
        context.followUpReason
            .takeIf { it.isNotBlank() }
            ?.let { appendLine("- 为什么现在做：$it") }
        appendLine("- 先验证：${context.validationStep}")
        appendLine("- 我准备怎么验证：")
        appendLine("- 看什么结果算成立：")
        appendLine("- 这次新的判断：")
    }

private fun buildReminderNextStep(note: NoteEntity): String =
    when (note.status) {
        NoteStatus.IN_PROGRESS -> "先补一句最新进展，再往前拱一步。"
        NoteStatus.DONE -> ""
        NoteStatus.IDEA -> "先补一条更具体的记录，把它重新压回到可推进状态。"
    }

private fun pickContinueNote(notes: List<NoteEntity>): NoteEntity? =
    notes
        .filter { it.status == NoteStatus.IN_PROGRESS }
        .maxByOrNull { it.updatedAt }
        ?: notes
            .filter { it.status == NoteStatus.IDEA }
            .maxByOrNull { it.updatedAt }

private fun pickStaleNote(
    notes: List<NoteEntity>,
    excludeNoteId: Long?,
): NoteEntity? {
    val threshold = System.currentTimeMillis() - 12L * 24 * 60 * 60 * 1_000
    return notes
        .filter { it.id != excludeNoteId }
        .filter { it.status != NoteStatus.DONE }
        .filter { it.updatedAt < threshold }
        .minByOrNull { it.updatedAt }
}

private fun buildResearchReminderContext(
    note: NoteEntity?,
    activeNotes: List<NoteEntity>,
): ResearchReminderContext? {
    val target = note ?: return null
    val threadKey = NoteConnectionAnalyzer.bestThreadKeyFor(target, activeNotes) ?: return null
    val thread = NoteConnectionAnalyzer.threadFromKey(threadKey, activeNotes)
    val researchLead = ThreadResearchAnalyzer.buildResearchClusters(
        notes = NoteConnectionAnalyzer.notesForThread(threadKey, activeNotes)
            .filter(ThreadResearchAnalyzer::isResearchMemoryNote)
            .take(3),
        threadTitle = thread.title,
    ).firstOrNull() ?: return null

    return researchLead.validationStep
        .takeIf { it.isNotBlank() }
        ?.let { validationStep ->
            ResearchReminderContext(
                threadKey = threadKey,
                threadTitle = thread.title,
                label = researchLead.label,
                validationStep = validationStep,
                followUpReason = researchLead.followUpReason,
            )
        }
}
