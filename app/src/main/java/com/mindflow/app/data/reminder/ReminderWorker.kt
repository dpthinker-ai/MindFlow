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
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
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

        createChannel()

        val payload = when (kind) {
            ReminderKind.MORNING -> buildMorningPayload(
                container = container,
                notes = activeNotes,
                continueNote = continueNote,
                staleNote = staleNote,
            )
            ReminderKind.EVENING -> buildEveningPayload(activeNotes)
        }

        notify(kind, payload.title, payload.body, payload.openNoteId)
        return Result.success()
    }

    private suspend fun buildMorningPayload(
        container: com.mindflow.app.di.AppContainer,
        notes: List<NoteEntity>,
        continueNote: NoteEntity?,
        staleNote: NoteEntity?,
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

        return NotificationPayload(title = title, body = body)
            .copy(openNoteId = continueNote?.id ?: staleNote?.id)
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
            )
        }
    }

    private fun notify(
        kind: ReminderKind,
        title: String,
        body: String,
        openNoteId: Long? = null,
    ) {
        val launchIntent = Intent(applicationContext, EntryProxyActivity::class.java).apply {
            action = MindFlowEntryIntents.ACTION_OPEN_FLOW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            openNoteId?.takeIf { it > 0L }?.let { putExtra(MindFlowEntryIntents.EXTRA_NOTE_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            kind.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val quickCaptureIntent = quickActionPendingIntent(
            kind = kind,
            action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE,
            requestCodeOffset = 100,
        )
        val voiceCaptureIntent = quickActionPendingIntent(
            kind = kind,
            action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE_VOICE,
            requestCodeOffset = 150,
        )
        val flowIntent = quickActionPendingIntent(
            kind = kind,
            action = MindFlowEntryIntents.ACTION_OPEN_FLOW,
            requestCodeOffset = 200,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome_inset)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_launcher_monochrome_inset,
                "记一条",
                quickCaptureIntent,
            )
            .addAction(
                R.drawable.ic_launcher_monochrome_inset,
                "语音记",
                voiceCaptureIntent,
            )
            .addAction(
                R.drawable.ic_launcher_monochrome_inset,
                "打开 Flow",
                flowIntent,
            )
            .build()

        NotificationManagerCompat.from(applicationContext).notify(kind.ordinal + 701, notification)
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
    )

    private fun quickActionPendingIntent(
        kind: ReminderKind,
        action: String,
        requestCodeOffset: Int,
    ): PendingIntent {
        val intent = Intent(applicationContext, EntryProxyActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            kind.ordinal + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "mindflow_daily_brief"
        const val KEY_KIND = "kind"
    }
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
