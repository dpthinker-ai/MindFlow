package com.mindflow.app.data.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AiCloudUsageReportResult(
    val foregroundNotice: String? = null,
    val backgroundBatch: CloudUsageNotificationBatch? = null,
)

class AiCloudUsageReporter(
    private val repository: AiUsageEventRepository,
    private val notificationAggregator: CloudUsageNotificationAggregator,
    private val backgroundNotifier: CloudUsageNotifier? = null,
) {
    private val _foregroundNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val foregroundNotices: SharedFlow<String> = _foregroundNotices.asSharedFlow()

    suspend fun record(event: AiUsageEvent): AiCloudUsageReportResult {
        repository.append(event)
        if (event.triggerMode == AiTriggerMode.FOREGROUND_USER_ACTION) {
            val notice = foregroundNoticeFor(event)
            _foregroundNotices.tryEmit(notice)
            return AiCloudUsageReportResult(foregroundNotice = notice)
        }

        notificationAggregator.record(event, now = event.timestamp)
        val batch = notificationAggregator.flushIfDue(now = event.timestamp)
        if (batch != null) {
            repository.markNotified(
                eventIds = batch.eventIds,
                notifiedAt = batch.notifiedAt,
                notificationBatchId = batch.batchId,
            )
            backgroundNotifier?.notify(batch)
        }
        return AiCloudUsageReportResult(backgroundBatch = batch)
    }

    private fun foregroundNoticeFor(event: AiUsageEvent): String =
        if (event.fallbackOccurred) {
            "本地模型未完成，已改用 ${event.providerLabel} 处理这次请求。"
        } else {
            "已使用 ${event.providerLabel} 处理这次请求。"
        }
}

interface CloudUsageNotifier {
    suspend fun notify(batch: CloudUsageNotificationBatch)
}
