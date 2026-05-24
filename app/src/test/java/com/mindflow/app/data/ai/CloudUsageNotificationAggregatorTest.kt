package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class CloudUsageNotificationAggregatorTest {
    @Test
    fun backgroundEventsWaitAtLeastFiveMinutesBeforeFlush() {
        val aggregator = CloudUsageNotificationAggregator()
        val event = sampleEvent(timestamp = 1_000L)

        assertThat(aggregator.record(event, now = 1_000L)).isNull()
        assertThat(aggregator.flushIfDue(now = 1_000L + 4.minutes.inWholeMilliseconds)).isNull()

        val batch = aggregator.flushIfDue(now = 1_000L + 5.minutes.inWholeMilliseconds)
        assertThat(batch?.message).contains("DeepSeek")
        assertThat(batch?.message).contains("1 项后台整理")
        assertThat(batch?.eventIds).containsExactly("event-1")
    }

    @Test
    fun continuousBackgroundEventsFlushAtThirtyMinutes() {
        val aggregator = CloudUsageNotificationAggregator()
        aggregator.record(sampleEvent(eventId = "event-1", timestamp = 1_000L), now = 1_000L)
        aggregator.record(sampleEvent(eventId = "event-2", timestamp = 10.minutes.inWholeMilliseconds), now = 10.minutes.inWholeMilliseconds)

        val batch = aggregator.flushIfDue(now = 31.minutes.inWholeMilliseconds)

        assertThat(batch?.message).contains("2 项后台整理")
        assertThat(batch?.eventIds).containsExactly("event-1", "event-2").inOrder()
    }

    @Test
    fun dailyNotificationLimitSuppressesFourthBatch() {
        val aggregator = CloudUsageNotificationAggregator()
        repeat(3) { index ->
            aggregator.record(sampleEvent(eventId = "event-$index", timestamp = index * 10.minutes.inWholeMilliseconds), now = index * 10.minutes.inWholeMilliseconds)
            assertThat(aggregator.flushIfDue(now = index * 10.minutes.inWholeMilliseconds + 5.minutes.inWholeMilliseconds)).isNotNull()
        }

        aggregator.record(sampleEvent(eventId = "event-4", timestamp = 40.minutes.inWholeMilliseconds), now = 40.minutes.inWholeMilliseconds)

        assertThat(aggregator.flushIfDue(now = 46.minutes.inWholeMilliseconds)).isNull()
    }

    private fun sampleEvent(
        eventId: String = "event-1",
        timestamp: Long = 1_000L,
    ): AiUsageEvent = AiUsageEvent(
        eventId = eventId,
        timestamp = timestamp,
        taskType = AiTaskType.EXTRACT_TAGS,
        triggerSurface = AiTriggerSurface.BACKGROUND,
        triggerMode = AiTriggerMode.BACKGROUND_AUTOMATION,
        providerId = "deepseek",
        providerLabel = "DeepSeek",
        model = "deepseek-v4-flash",
        executionMode = AiExecutionMode.AUTOMATIC,
        providerSelectionReason = AiProviderSelectionReason.SELECTED_BY_POLICY,
        fallbackOccurred = false,
        fallbackReason = null,
        dataSensitivity = AiDataSensitivity.LOW,
        payloadPolicy = PromptPayloadPolicy.SELECTED_SNIPPETS,
        tokenCount = 64,
        success = true,
        failureReason = null,
    )
}
