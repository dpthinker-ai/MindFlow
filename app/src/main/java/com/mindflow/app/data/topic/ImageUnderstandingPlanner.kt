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

sealed interface ImageUnderstandingResult {
    data class Success(
        val summary: String,
        val imageType: String,
        val extractedText: String,
        val objects: List<String>,
        val confidence: Float,
        val provider: AiProvider,
        val latencyMs: Long,
    ) : ImageUnderstandingResult

    data class Failure(val message: String) : ImageUnderstandingResult
}

class ImageUnderstandingPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun understand(
        imagePath: String,
        userNote: String = "",
    ): ImageUnderstandingResult {
        val imageFile = File(imagePath.trim())
        if (!imageFile.exists() || !imageFile.isFile || imageFile.length() <= 0L) {
            return ImageUnderstandingResult.Failure("图片文件不存在，无法识别")
        }

        return runCatching {
            aiTaskRouter.run(
                AiTaskRequest<AiTaskPayload.ImageUnderstanding>(
                    type = AiTaskType.UNDERSTAND_IMAGE,
                    input = AiTaskInput.ImageFile(
                        path = imageFile.absolutePath,
                        mimeType = imageMimeTypeForFile(imageFile),
                        userNote = userNote.trim(),
                    ),
                    automaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
                    allowProviderFallback = false,
                    validate = { payload ->
                        payload.summary.isNotBlank() ||
                            payload.extractedText.isNotBlank() ||
                            payload.objects.isNotEmpty()
                    },
                ),
            )
        }.fold(
            onSuccess = { result ->
                val payload = result.payload
                ImageUnderstandingResult.Success(
                    summary = payload.summary.trim(),
                    imageType = payload.imageType.trim(),
                    extractedText = payload.extractedText.trim(),
                    objects = payload.objects.map(String::trim).filter(String::isNotBlank).distinct().take(8),
                    confidence = payload.confidence,
                    provider = result.meta.providerUsed,
                    latencyMs = result.meta.latencyMs,
                )
            },
            onFailure = { error ->
                ImageUnderstandingResult.Failure(
                    message = when (error) {
                        is AiTaskRoutingException -> imageUnderstandingRoutingFailureMessage(error)
                        else -> error.message ?: "图片理解失败，请稍后重试"
                    },
                )
            },
        )
    }
}

internal fun imageMimeTypeForFile(file: File): String =
    when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/*"
    }

internal fun imageUnderstandingRoutingFailureMessage(error: AiTaskRoutingException): String =
    when (error.firstFailureReason) {
        "quality_gate_failed" -> "端侧图片理解没有返回可用内容，请换一张更清晰的图片重试"
        "empty_payload" -> "端侧图片理解没有产出结果；本地模型可用，但当前图片没有被成功识别"
        else -> "图片理解失败，请稍后重试"
    }
