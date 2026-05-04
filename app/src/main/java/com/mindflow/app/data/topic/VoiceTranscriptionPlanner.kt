package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskRoutingException
import com.mindflow.app.data.ai.AiTaskType
import java.io.File

sealed interface VoiceTranscriptionResult {
    data class Success(
        val transcript: String,
        val topic: String,
        val language: String?,
        val provider: AiProvider,
        val latencyMs: Long,
    ) : VoiceTranscriptionResult

    data class Failure(val message: String) : VoiceTranscriptionResult
}

class VoiceTranscriptionPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun transcribe(
        audioPath: String,
        localeHint: String = "zh-CN",
    ): VoiceTranscriptionResult {
        val audioFile = File(audioPath.trim())
        if (!audioFile.exists() || !audioFile.isFile || audioFile.length() <= 0L) {
            return VoiceTranscriptionResult.Failure("录音文件不存在，无法转写")
        }

        return runCatching {
            aiTaskRouter.run(
                AiTaskRequest<AiTaskPayload.AudioTranscription>(
                    type = AiTaskType.TRANSCRIBE_AUDIO,
                    input = AiTaskInput.AudioFile(
                        path = audioFile.absolutePath,
                        mimeType = audioMimeTypeForFile(audioFile),
                        localeHint = localeHint,
                    ),
                    automaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
                    allowProviderFallback = false,
                    validate = { payload -> payload.transcript.isNotBlank() },
                ),
            )
        }.fold(
            onSuccess = { result ->
                VoiceTranscriptionResult.Success(
                    transcript = result.payload.transcript.trim(),
                    topic = result.payload.topic.trim(),
                    language = result.payload.language,
                    provider = result.meta.providerUsed,
                    latencyMs = result.meta.latencyMs,
                )
            },
            onFailure = { error ->
                VoiceTranscriptionResult.Failure(
                    message = when (error) {
                        is AiTaskRoutingException -> voiceTranscriptionRoutingFailureMessage(error)
                        else -> error.message ?: "端侧转写失败，请稍后重试"
                    },
                )
            },
        )
    }
}

internal fun audioMimeTypeForFile(file: File): String =
    when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        else -> "audio/*"
    }

internal fun voiceTranscriptionRoutingFailureMessage(error: AiTaskRoutingException): String =
    when (error.firstFailureReason) {
        "quality_gate_failed" -> "端侧转写没有返回可用文本，请确认录音内容清晰后重试"
        "empty_payload" -> "端侧转写没有产出结果；本地模型可用，但当前音频没有被成功识别"
        else -> "端侧转写失败，请稍后重试"
    }
