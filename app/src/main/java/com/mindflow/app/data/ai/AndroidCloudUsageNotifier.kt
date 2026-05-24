package com.mindflow.app.data.ai

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
import com.mindflow.app.EntryProxyActivity
import com.mindflow.app.R
import com.mindflow.app.ui.navigation.FlowFocus
import com.mindflow.app.ui.navigation.MindFlowEntryIntents
import kotlin.math.absoluteValue

class AndroidCloudUsageNotifier(
    private val context: Context,
) : CloudUsageNotifier {
    override suspend fun notify(batch: CloudUsageNotificationBatch) {
        if (!hasNotificationPermission()) return
        createChannel()

        val launchIntent = Intent(context, EntryProxyActivity::class.java).apply {
            action = MindFlowEntryIntents.ACTION_OPEN_FLOW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MindFlowEntryIntents.EXTRA_FLOW_FOCUS, FlowFocus.REVIEW.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CLOUD_USAGE_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome_inset)
            .setContentTitle("MindFlow 云端 AI 使用")
            .setContentText(batch.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(batch.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(CLOUD_USAGE_NOTIFICATION_BASE_ID + batch.batchId.hashCode().absoluteValue % 1000, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MindFlow 云端 AI 使用",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "低频提示后台任务何时使用了云端 AI"
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "mindflow_cloud_ai_usage"
        const val CLOUD_USAGE_REQUEST_CODE = 9100
        const val CLOUD_USAGE_NOTIFICATION_BASE_ID = 9100
    }
}
