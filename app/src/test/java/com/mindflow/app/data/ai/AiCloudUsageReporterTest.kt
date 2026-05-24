package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiCloudUsageReporterTest {
    @Test
    fun foregroundCloudEventReturnsImmediateNoticeAndIsPersisted() = runTest {
        val repository = FileAiUsageEventRepository(Files.createTempDirectory("ai-usage").toFile())
        val reporter = AiCloudUsageReporter(
            repository = repository,
            notificationAggregator = CloudUsageNotificationAggregator(),
        )

        val result = reporter.record(
            sampleEvent(
                triggerSurface = AiTriggerSurface.EDITOR,
                triggerMode = AiTriggerMode.FOREGROUND_USER_ACTION,
            ),
        )

        assertThat(result.foregroundNotice).isEqualTo("已使用 DeepSeek 处理这次请求。")
        assertThat(repository.recent(limit = 20).single().providerId).isEqualTo("deepseek")
    }

    @Test
    fun fallbackForegroundNoticeMentionsLocalFallback() = runTest {
        val repository = FileAiUsageEventRepository(Files.createTempDirectory("ai-usage").toFile())
        val reporter = AiCloudUsageReporter(
            repository = repository,
            notificationAggregator = CloudUsageNotificationAggregator(),
        )

        val result = reporter.record(
            sampleEvent(
                triggerMode = AiTriggerMode.FOREGROUND_USER_ACTION,
                fallbackOccurred = true,
                fallbackReason = "fallback_from_on_device_unavailable",
            ),
        )

        assertThat(result.foregroundNotice).isEqualTo("本地模型未完成，已改用 DeepSeek 处理这次请求。")
    }

    private fun sampleEvent(
        triggerSurface: AiTriggerSurface = AiTriggerSurface.EDITOR,
        triggerMode: AiTriggerMode = AiTriggerMode.FOREGROUND_USER_ACTION,
        fallbackOccurred: Boolean = false,
        fallbackReason: String? = null,
    ): AiUsageEvent = AiUsageEvent(
        eventId = "event-1",
        timestamp = 1_000L,
        taskType = AiTaskType.POLISH_CONTENT,
        triggerSurface = triggerSurface,
        triggerMode = triggerMode,
        providerId = "deepseek",
        providerLabel = "DeepSeek",
        model = "deepseek-v4-flash",
        executionMode = AiExecutionMode.AUTOMATIC,
        providerSelectionReason = AiProviderSelectionReason.SELECTED_BY_POLICY,
        fallbackOccurred = fallbackOccurred,
        fallbackReason = fallbackReason,
        dataSensitivity = AiDataSensitivity.MEDIUM,
        payloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
        tokenCount = 128,
        success = true,
        failureReason = null,
    )
}
