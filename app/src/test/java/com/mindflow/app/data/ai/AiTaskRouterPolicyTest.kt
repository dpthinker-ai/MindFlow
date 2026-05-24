package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskRouterPolicyTest {
    @Test
    fun automaticPolicyKeepsMediaTasksOnDeviceOnlyEvenWhenCloudIsPreferred() = runTest {
        val cloudProvider = RecordingProvider(AiTaskPayload.ImageUnderstanding("云侧", "photo"))
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = RecordingProvider(AiTaskPayload.ImageUnderstanding("端侧", "photo")),
            cloudProvider = cloudProvider,
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.UNDERSTAND_IMAGE,
                input = AiTaskInput.ImageFile("/data/user/0/com.mindflow.app/files/image.jpg"),
                automaticPreference = AiAutomaticPreference.PREFER_CLOUD,
                validate = { payload -> (payload as AiTaskPayload.ImageUnderstanding).summary.isNotBlank() },
            ),
        )

        assertThat((result.payload as AiTaskPayload.ImageUnderstanding).summary).isEqualTo("端侧")
        assertThat(cloudProvider.calls).isEqualTo(0)
    }

    @Test
    fun backgroundHighSensitivityBlocksCloudFallback() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = RecordingProvider(null),
            cloudProvider = RecordingProvider(AiTaskPayload.NoteInsight("云侧摘要", listOf("要点"))),
            resolveRuntimeSettings = {
                AiRuntimeSettings(cloudAllowedForBackground = true)
            },
        )

        val error = runCatching {
            router.run(
                AiTaskRequest(
                    type = AiTaskType.SUMMARIZE_NOTE,
                    input = AiTaskInput.NoteText("身份证 110101199003071234 的完整记录"),
                    triggerMode = AiTriggerMode.BACKGROUND_AUTOMATION,
                    allowProviderFallback = true,
                    validate = { payload ->
                        val insight = payload as AiTaskPayload.NoteInsight
                        insight.summary.isNotBlank() && insight.keyPoints.isNotEmpty()
                    },
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiTaskRoutingException::class.java)
        assertThat((error as AiTaskRoutingException).firstFailureReason).isEqualTo("background_cloud_blocked")
    }

    private class RecordingProvider(
        private val payload: AiTaskPayload?,
    ) : AiTaskProvider {
        var calls = 0
            private set

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
            calls += 1
            return payload as T?
        }
    }
}
