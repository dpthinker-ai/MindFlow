package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoteInsightPlannerTest {
    @Test
    fun `generate returns durable insight from ai payload`() = runTest {
        val planner = NoteInsightPlanner(
            aiTaskRouter = routerWith(
                AiTaskPayload.NoteInsight(
                    summary = "这条记录关注阅读体验与后台洞察的取舍。",
                    keyPoints = listOf("正文需要完整展示", "洞察应后台生成并持久化"),
                ),
            ),
        )

        val result = planner.generate("正文需要完整展示，洞察应后台生成并持久化。")

        assertThat(result).isInstanceOf(NoteInsightResult.Success::class.java)
        val insight = (result as NoteInsightResult.Success).insight
        assertThat(insight.summary).isEqualTo("这条记录关注阅读体验与后台洞察的取舍。")
        assertThat(insight.keyPoints).containsExactly("正文需要完整展示", "洞察应后台生成并持久化").inOrder()
        assertThat(insight.contentHash).isEqualTo(
            NoteInsightPlanner.contentHash("正文需要完整展示，洞察应后台生成并持久化。"),
        )
    }

    @Test
    fun `generate rejects duplicated summary-only payload`() = runTest {
        val planner = NoteInsightPlanner(
            aiTaskRouter = routerWith(
                AiTaskPayload.NoteInsight(
                    summary = "同一句话",
                    keyPoints = listOf("同一句话"),
                ),
            ),
        )

        assertThat(planner.generate("同一句话")).isInstanceOf(NoteInsightResult.Failure::class.java)
    }

    @Test
    fun `voice insight source uses transcript and ignores audio metadata`() {
        val content = """
            原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.m4a
            语音转写（可编辑）：明天上午确认方案风险，并把待办拆成两步。
            识别信息：AI 已根据转写内容提取
        """.trimIndent()

        assertThat(noteInsightSourceContent(content)).isEqualTo("明天上午确认方案风险，并把待办拆成两步。")
        assertThat(shouldAutoGenerateVoiceInsight(content)).isTrue()
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
}
