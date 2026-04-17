package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskTraceRecorderTest {
    @Test
    fun `append writes one json line per task`() = runTest {
        val dir = Files.createTempDirectory("ai-traces")
        val recorder = AiTaskTraceRecorder(dir.toFile())

        recorder.append(
            taskType = AiTaskType.EXTRACT_TOPIC,
            meta = AiTaskMeta(
                providerUsed = AiProvider.ON_DEVICE,
                fallbackOccurred = false,
                latencyMs = 42,
                qualitySignals = mapOf("confidence" to "0.86"),
            ),
        )

        val lines = dir.resolve("ai-task-traces.jsonl").toFile().readLines()
        assertThat(lines).hasSize(1)
        assertThat(lines.first()).contains("EXTRACT_TOPIC")
        assertThat(lines.first()).contains("ON_DEVICE")
        assertThat(lines.first()).contains("confidence")

        val latest = dir.resolve("latest-successful-provider.json").toFile().readText()
        assertThat(latest).contains("EXTRACT_TOPIC")
        assertThat(latest).contains("ON_DEVICE")
    }
}
