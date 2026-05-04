package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContentPolishPlannerTest {
    @Test
    fun `polish planner rejects unchanged payloads`() = runTest {
        val planner = ContentPolishPlanner(
            aiTaskRouter = routerWith(AiTaskPayload.Polish(polishedText = "原文", changeSummary = "无变化")),
        )

        val result = planner.polish("原文")

        assertThat(result).isEqualTo(ContentPolishResult.NoChange)
    }

    @Test
    fun `polish planner returns success for changed payload`() = runTest {
        val planner = ContentPolishPlanner(
            aiTaskRouter = routerWith(AiTaskPayload.Polish(polishedText = "更清晰的原文", changeSummary = "修掉重复")),
        )

        val result = planner.polish("原文")

        assertThat(result).isInstanceOf(ContentPolishResult.Success::class.java)
        val success = result as ContentPolishResult.Success
        assertThat(success.polishedText).isEqualTo("更清晰的原文")
        assertThat(success.summary).isEqualTo("修掉重复")
    }

    @Test
    fun `title polish planner returns concise first line`() = runTest {
        val planner = ContentPolishPlanner(
            aiTaskRouter = routerWith(
                AiTaskPayload.Polish(
                    polishedText = "更清晰的标题\n多余解释",
                    changeSummary = "压缩标题",
                ),
            ),
        )

        val result = planner.polishTitle("旧标题", "正文材料")

        assertThat(result).isInstanceOf(ContentPolishResult.Success::class.java)
        val success = result as ContentPolishResult.Success
        assertThat(success.polishedText).isEqualTo("更清晰的标题")
        assertThat(success.summary).isEqualTo("压缩标题")
    }

    @Test
    fun `polish planner prefers cloud first in automatic mode`() = runTest {
        val requests = mutableListOf<AiTaskRequest<*>>()
        val planner = ContentPolishPlanner(
            aiTaskRouter = AiTaskRouter(
                resolveMode = { AiExecutionMode.AUTOMATIC },
                onDeviceProvider = RecordingProvider(
                    requests = requests,
                    payload = AiTaskPayload.Polish(polishedText = "端侧润色", changeSummary = "端侧"),
                ),
                cloudProvider = RecordingProvider(
                    requests = requests,
                    payload = AiTaskPayload.Polish(polishedText = "云侧润色", changeSummary = "云侧"),
                ),
            ),
        )

        val result = planner.polish("原文")

        assertThat(result).isInstanceOf(ContentPolishResult.Success::class.java)
        val success = result as ContentPolishResult.Success
        assertThat(success.polishedText).isEqualTo("云侧润色")
        assertThat(requests).isNotEmpty()
        assertThat(requests.single().automaticPreference).isEqualTo(AiAutomaticPreference.PREFER_CLOUD)
    }

    private fun routerWith(payload: AiTaskPayload): AiTaskRouter = AiTaskRouter(
        resolveMode = { AiExecutionMode.AUTOMATIC },
        onDeviceProvider = FakeProvider(payload),
        cloudProvider = FakeProvider(null),
    )

    private class FakeProvider(
        private val payload: AiTaskPayload?,
    ) : AiTaskProvider {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = payload as T?
    }

    private class RecordingProvider(
        private val requests: MutableList<AiTaskRequest<*>>,
        private val payload: AiTaskPayload?,
    ) : AiTaskProvider {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
            requests += request
            return payload as T?
        }
    }
}
