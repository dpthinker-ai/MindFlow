package com.mindflow.app.ui.components

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.markdown.SimpleMarkdown

enum class RecordKind {
    TEXT,
    VOICE,
    IMAGE,
    LINK,
}

fun NoteEntity.inferRecordKind(): RecordKind {
    val labels = content.captureLabels()
    val haystack = "$topic\n$content\n${tags.joinToString(" ")}".lowercase()
    return when {
        "原始录音" in labels ||
            "语音转写（可编辑）" in labels ||
            "语音" in haystack ||
            "录音" in haystack ||
            "voice" in haystack -> RecordKind.VOICE
        "图片" in labels ||
            "图片" in haystack ||
            "image" in haystack ||
            "photo" in haystack -> RecordKind.IMAGE
        "链接" in labels ||
            "原文链接" in labels ||
            "http://" in haystack ||
            "https://" in haystack ||
            "文章" in haystack ||
            "阅读" in haystack ||
            "收藏" in haystack -> RecordKind.LINK
        else -> RecordKind.TEXT
    }
}

fun NoteEntity.compactRecordPreviewText(): String {
    val text = when (inferRecordKind()) {
        RecordKind.VOICE -> captureField(content, "语音转写（可编辑）")
            .ifBlank { captureField(content, "原始内容") }
            .ifBlank { contentWithoutFields(content, setOf("原始录音", "语音转写（可编辑）", "识别信息")) }
            .ifBlank { "等待语音转写" }
        RecordKind.IMAGE -> captureField(content, "图片理解摘要")
            .ifBlank { captureField(content, "图像理解结果") }
            .ifBlank { captureField(content, "关键信息") }
            .ifBlank {
                contentWithoutFields(
                    content,
                    setOf(
                        "图片",
                        "识别信息",
                        "补充说明",
                        "图像理解结果",
                        "关键信息",
                        "关键信息提取",
                        "结构化识别",
                        "OCR 文本",
                        "OCR 文本(可选)",
                    ),
                )
            }
            .ifBlank { "图片已保存，等待理解" }
        RecordKind.LINK -> captureField(content, "正文")
            .ifBlank { captureField(content, "原始内容") }
            .ifBlank { captureField(content, "补充说明") }
            .ifBlank { contentWithoutFields(content, setOf("链接", "原文链接", "解析信息")) }
            .ifBlank { "等待提取网页正文" }
        RecordKind.TEXT -> content
    }
    return SimpleMarkdown.toPlainText(text)
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "没有正文" }
}

fun NoteEntity.compactRecordTitleText(): String {
    val kind = inferRecordKind()
    val topicTitle = normalizeDisplayText(topic)
    if (topicTitle.isUsableRecordTitle(kind)) return topicTitle

    val fallback = when (kind) {
        RecordKind.VOICE -> captureField(content, "语音转写（可编辑）")
            .ifBlank { captureField(content, "原始内容") }
            .toCompactTitle("语音记录")
        RecordKind.IMAGE -> captureField(content, "图片理解摘要")
            .ifBlank { captureField(content, "图像理解结果") }
            .ifBlank { captureField(content, "关键信息") }
            .toCompactTitle("图片记录")
        RecordKind.LINK -> captureField(content, "标题")
            .ifBlank { captureField(content, "正文") }
            .ifBlank { captureField(content, "补充说明") }
            .toCompactTitle("链接记录")
        RecordKind.TEXT -> content.toCompactTitle("未命名想法")
    }
    return fallback.ifBlank {
        when (kind) {
            RecordKind.VOICE -> "语音记录"
            RecordKind.IMAGE -> "图片记录"
            RecordKind.LINK -> "链接记录"
            RecordKind.TEXT -> "未命名想法"
        }
    }
}

internal fun captureField(content: String, label: String): String =
    content
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            when {
                line.startsWith("$label：") -> line.removePrefix("$label：")
                line.startsWith("$label:") -> line.removePrefix("$label:")
                else -> null
            }
        }
        .firstOrNull()
        ?.trim()
        .orEmpty()

private fun String.captureLabels(): Set<String> =
    lineSequence()
        .map { it.substringBefore("：").substringBefore(":").trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun contentWithoutFields(
    content: String,
    labels: Set<String>,
): String =
    content
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trim()
            labels.any { label -> trimmed.startsWith("$label：") || trimmed.startsWith("$label:") }
        }
        .joinToString("\n")
        .trim()

private fun String.toCompactTitle(fallback: String): String {
    val normalized = normalizeDisplayText(this)
    if (normalized.isBlank() || normalized.looksLikePathOrUrl()) return fallback
    return normalized
        .split(Regex("[。！？!?；;\\n]"))
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(24)
        ?.trim()
        ?.ifBlank { fallback }
        ?: fallback
}

private fun normalizeDisplayText(raw: String): String =
    SimpleMarkdown.toPlainText(raw)
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.isUsableRecordTitle(kind: RecordKind): Boolean {
    if (isBlank() || looksLikePathOrUrl()) return false
    if (kind != RecordKind.TEXT && this == "未命名想法") return false
    val metadataLabels = setOf(
        "原始录音",
        "语音转写（可编辑）",
        "图片",
        "链接",
        "原文链接",
        "正文",
        "识别信息",
        "解析信息",
        "OCR 文本",
    )
    return metadataLabels.none { label -> startsWith("$label：") || startsWith("$label:") }
}

private fun String.looksLikePathOrUrl(): Boolean {
    val lower = lowercase()
    return "/data/" in lower ||
        "/storage/" in lower ||
        "content://" in lower ||
        "file://" in lower ||
        "http://" in lower ||
        "https://" in lower ||
        ".m4a" in lower ||
        ".wav" in lower ||
        ".aac" in lower ||
        ".jpg" in lower ||
        ".jpeg" in lower ||
        ".png" in lower ||
        "com.mindflow.app/files" in lower
}
