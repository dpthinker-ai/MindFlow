package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskRoutingException
import com.mindflow.app.data.ai.AiTaskType
import java.security.MessageDigest

data class NoteInsight(
    val summary: String,
    val keyPoints: List<String>,
    val contentHash: String,
    val generatedAt: Long,
)

sealed interface NoteInsightResult {
    data class Success(val insight: NoteInsight) : NoteInsightResult
    data object BlankContent : NoteInsightResult
    data class Failure(val message: String) : NoteInsightResult
}

class NoteInsightPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun generate(content: String): NoteInsightResult {
        val normalizedContent = noteInsightSourceContent(content).trim()
        if (normalizedContent.isBlank()) return NoteInsightResult.BlankContent

        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.SUMMARIZE_NOTE,
                    input = AiTaskInput.NoteText(normalizedContent),
                    automaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
                    allowProviderFallback = false,
                    validate = { payload ->
                        val insight = payload as AiTaskPayload.NoteInsight
                        insight.summary.isNotBlank() && insight.keyPoints.isNotEmpty()
                    },
                ),
            )
        }.getOrElse { error ->
            if (error is AiTaskRoutingException && error.mode == AiExecutionMode.ON_DEVICE_ONLY) {
                return NoteInsightResult.Failure("端侧模型这次没有生成可用洞察")
            }
            return NoteInsightResult.Failure(error.message ?: "AI 洞察生成失败")
        }

        val payload = result.payload as AiTaskPayload.NoteInsight
        val summary = normalizeInsightText(payload.summary).take(140)
        val keyPoints = payload.keyPoints
            .map(::normalizeInsightText)
            .filter { it.isNotBlank() && it != summary }
            .distinct()
            .take(4)

        if (summary.isBlank() || keyPoints.isEmpty()) {
            return NoteInsightResult.Failure("AI 洞察质量不足，暂不保存")
        }

        return NoteInsightResult.Success(
            NoteInsight(
                summary = summary,
                keyPoints = keyPoints,
                contentHash = contentHash(normalizedContent),
                generatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        fun contentHash(content: String): String {
            val bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(content.trim().toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

fun noteInsightSourceContent(content: String): String {
    val transcript = extractInsightField(content, "语音转写（可编辑）")
    if (transcript.isNotBlank()) return transcript

    val legacyOriginalContent = extractInsightField(content, "原始内容")
    if (legacyOriginalContent.isNotBlank()) return legacyOriginalContent

    if (hasInsightField(content, "语音转写（可编辑）")) return ""

    return contentWithoutInsightMetadata(
        content = content,
        labels = setOf("原始录音", "语音转写（可编辑）", "AI 快速提取", "关键信息", "识别信息"),
    ).trim()
}

fun shouldAutoGenerateVoiceInsight(content: String): Boolean =
    hasInsightField(content, "原始录音") ||
        hasInsightField(content, "语音转写（可编辑）") ||
        hasInsightField(content, "原始内容")

private fun extractInsightField(
    content: String,
    label: String,
): String = content
    .lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("$label：") || it.startsWith("$label:") }
    ?.substringAfter("：")
    ?.substringAfter(":")
    ?.trim()
    .orEmpty()

private fun hasInsightField(
    content: String,
    label: String,
): Boolean = content
    .lineSequence()
    .map { it.trim() }
    .any { it.startsWith("$label：") || it.startsWith("$label:") }

private fun contentWithoutInsightMetadata(
    content: String,
    labels: Set<String>,
): String = content
    .lineSequence()
    .filterNot { line ->
        val trimmed = line.trim()
        labels.any { label ->
            trimmed.startsWith("$label：") || trimmed.startsWith("$label:")
        }
    }
    .joinToString("\n")

private fun normalizeInsightText(raw: String): String =
    raw
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
        .trim('-', '*', ' ', '\t')
        .trim()
