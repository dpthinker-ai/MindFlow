package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskRoutingException
import com.mindflow.app.data.ai.AiTaskType

sealed interface ContentPolishResult {
    data class Success(val polishedText: String, val summary: String) : ContentPolishResult
    data object NoChange : ContentPolishResult
    data class Failure(val message: String) : ContentPolishResult
}

class ContentPolishPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun polish(content: String): ContentPolishResult {
        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.POLISH_CONTENT,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val polish = payload as AiTaskPayload.Polish
                        polish.polishedText.isNotBlank()
                    },
                ),
            )
        }.getOrElse { error ->
            if (error is AiTaskRoutingException && error.mode == AiExecutionMode.ON_DEVICE_ONLY) {
                return ContentPolishResult.Failure("端侧模型这次没有给出可用结果")
            }
            return ContentPolishResult.Failure(error.message ?: "AI 润色失败")
        }

        val payload = result.payload as AiTaskPayload.Polish
        val polished = normalize(payload.polishedText)
        return if (polished == content.trim()) {
            ContentPolishResult.NoChange
        } else {
            ContentPolishResult.Success(polishedText = polished, summary = payload.changeSummary)
        }
    }

    private fun normalize(raw: String): String =
        raw
            .trim()
            .removePrefix("```markdown")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
