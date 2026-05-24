package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiUsageEventRepositoryTest {
    @Test
    fun repositoryPersistsMetadataWithoutPromptBodyApiKeyOrLocalPath() = runTest {
        val repository = FileAiUsageEventRepository(Files.createTempDirectory("ai-usage").toFile())

        repository.append(
            sampleEvent(
                providerId = "deepseek",
                providerLabel = "DeepSeek",
                model = "deepseek-v4-flash",
                payloadPolicy = PromptPayloadPolicy.SELECTED_SNIPPETS,
            ),
        )

        val recent = repository.recent(limit = 20)
        assertThat(recent.single().providerId).isEqualTo("deepseek")
        assertThat(recent.single().providerLabel).isEqualTo("DeepSeek")
        assertThat(recent.single().payloadPolicy).isEqualTo(PromptPayloadPolicy.SELECTED_SNIPPETS)
        assertThat(repository.rawText()).doesNotContain("sk-test")
        assertThat(repository.rawText()).doesNotContain("完整正文")
        assertThat(repository.rawText()).doesNotContain("/data/user/0/com.mindflow.app")
    }

    @Test
    fun repositoryKeepsNewestOneThousandEvents() = runTest {
        val repository = FileAiUsageEventRepository(Files.createTempDirectory("ai-usage").toFile())

        repeat(1_005) { index ->
            repository.append(sampleEvent(eventId = "event-$index", timestamp = index.toLong()))
        }

        val recent = repository.recent(limit = 2_000)
        assertThat(recent).hasSize(1_000)
        assertThat(recent.first().eventId).isEqualTo("event-5")
        assertThat(recent.last().eventId).isEqualTo("event-1004")
    }

    private fun sampleEvent(
        eventId: String = "event-1",
        timestamp: Long = 1_000L,
        providerId: String = "deepseek",
        providerLabel: String = "DeepSeek",
        model: String = "deepseek-v4-flash",
        payloadPolicy: PromptPayloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
    ): AiUsageEvent = AiUsageEvent(
        eventId = eventId,
        timestamp = timestamp,
        taskType = AiTaskType.EXTRACT_TAGS,
        triggerSurface = AiTriggerSurface.BACKGROUND,
        triggerMode = AiTriggerMode.BACKGROUND_AUTOMATION,
        providerId = providerId,
        providerLabel = providerLabel,
        model = model,
        executionMode = AiExecutionMode.AUTOMATIC,
        providerSelectionReason = AiProviderSelectionReason.SELECTED_BY_POLICY,
        fallbackOccurred = false,
        fallbackReason = null,
        dataSensitivity = AiDataSensitivity.LOW,
        payloadPolicy = payloadPolicy,
        tokenCount = 128,
        success = true,
        failureReason = null,
    )
}
