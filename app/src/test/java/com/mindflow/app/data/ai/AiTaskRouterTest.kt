package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskRouterTest {
    @Test
    fun `automatic mode keeps on-device result when quality gate passes`() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "睡眠恢复", confidence = 0.86f),
            ),
            cloudProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "云端标题", confidence = 0.95f),
            ),
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.EXTRACT_TOPIC,
                input = AiTaskInput.NoteText("睡眠影响恢复"),
                validate = { payload ->
                    val topic = payload as AiTaskPayload.Topic
                    topic.topic.isNotBlank()
                },
            ),
        )

        assertThat((result.payload as AiTaskPayload.Topic).topic).isEqualTo("睡眠恢复")
        assertThat(result.meta.providerUsed).isEqualTo(AiProvider.ON_DEVICE)
        assertThat(result.meta.fallbackOccurred).isFalse()
    }

    @Test
    fun `automatic mode falls back to cloud when on-device payload fails quality gate`() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "记录", confidence = 0.22f),
            ),
            cloudProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "睡眠与恢复", confidence = 0.91f),
            ),
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.EXTRACT_TOPIC,
                input = AiTaskInput.NoteText("睡眠影响恢复"),
                validate = { payload ->
                    val topic = payload as AiTaskPayload.Topic
                    topic.topic.length >= 3 && topic.topic != "记录"
                },
            ),
        )

        assertThat((result.payload as AiTaskPayload.Topic).topic).isEqualTo("睡眠与恢复")
        assertThat(result.meta.providerUsed).isEqualTo(AiProvider.CLOUD)
        assertThat(result.meta.fallbackOccurred).isTrue()
        assertThat(result.meta.fallbackReason).isEqualTo("quality_gate_failed")
    }

    @Test
    fun `automatic mode can prefer cloud first for interactive requests`() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "端侧标题", confidence = 0.81f),
            ),
            cloudProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "云侧标题", confidence = 0.93f),
            ),
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.EXTRACT_TOPIC,
                input = AiTaskInput.NoteText("编辑页重提取主题"),
                automaticPreference = AiAutomaticPreference.PREFER_CLOUD,
                validate = { payload ->
                    val topic = payload as AiTaskPayload.Topic
                    topic.topic.isNotBlank()
                },
            ),
        )

        assertThat((result.payload as AiTaskPayload.Topic).topic).isEqualTo("云侧标题")
        assertThat(result.meta.providerUsed).isEqualTo(AiProvider.CLOUD)
        assertThat(result.meta.fallbackOccurred).isFalse()
    }

    private class FakeProvider(
        private val payload: AiTaskPayload?,
    ) : AiTaskProvider {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = payload as T?
    }
}
