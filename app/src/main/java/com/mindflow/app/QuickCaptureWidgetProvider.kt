package com.mindflow.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mindflow.app.ui.navigation.MindFlowEntryIntents

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

        private fun buildRemoteViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
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
                    ),
                )
            }

        private fun quickIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, EntryProxyActivity::class.java).apply {
                this.action = action
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
