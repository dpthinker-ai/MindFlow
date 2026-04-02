package com.mindflow.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.ui.navigation.MindFlowEntryIntents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class QuickCaptureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuickCaptureWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetManager.updateAppWidget(appWidgetIds, buildRemoteViews(context))
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val notes = runBlocking {
                val app = context.applicationContext as? MindFlowApplication
                app?.appContainer?.noteRepository?.observeAllNotes()?.first().orEmpty()
            }
            val activeNotes = notes.filter { !it.isArchived }
            val continueNote = pickContinueNote(activeNotes)
            val latestNote = activeNotes.maxByOrNull { it.updatedAt }
            val subtitle = when {
                continueNote != null -> "当前继续：${continueNote.topic.ifBlank { "未命名记录" }}"
                latestNote != null -> "最近记录：${latestNote.topic.ifBlank { "未命名记录" }}"
                else -> context.getString(R.string.widget_capture_subtitle)
            }
            val tertiaryLabel = when {
                continueNote != null -> "继续"
                latestNote != null -> "最近"
                else -> context.getString(R.string.widget_flow_button)
            }
            return RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
                setTextViewText(R.id.widget_subtitle, subtitle)
                setTextViewText(R.id.widget_flow_button, tertiaryLabel)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    quickIntent(
                        context = context,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE,
                        requestCode = 100,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_capture_button,
                    quickIntent(
                        context = context,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE,
                        requestCode = 101,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_flow_button,
                    quickIntent(
                        context = context,
                        action = MindFlowEntryIntents.ACTION_OPEN_FLOW,
                        requestCode = 102,
                        openNoteId = continueNote?.id ?: latestNote?.id,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_voice_button,
                    quickIntent(
                        context = context,
                        action = MindFlowEntryIntents.ACTION_OPEN_CAPTURE_VOICE,
                        requestCode = 103,
                    ),
                )
            }
        }

        private fun quickIntent(
            context: Context,
            action: String,
            requestCode: Int,
            openNoteId: Long? = null,
        ): PendingIntent {
            val intent = Intent(context, EntryProxyActivity::class.java).apply {
                this.action = action
                openNoteId?.takeIf { it > 0L }?.let {
                    putExtra(MindFlowEntryIntents.EXTRA_NOTE_ID, it)
                }
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun pickContinueNote(notes: List<NoteEntity>): NoteEntity? =
            notes
                .filter { it.status == NoteStatus.IN_PROGRESS }
                .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
                .firstOrNull()
                ?: notes
                    .filter { it.status == NoteStatus.IDEA }
                    .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
                    .firstOrNull()
    }
}
