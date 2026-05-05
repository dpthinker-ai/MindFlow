package com.mindflow.app.ui.screens.editor

import java.net.URI

internal data class ArticleCaptureModel(
    val title: String,
    val host: String,
    val url: String,
    val summary: String,
    val keyPoints: List<String>,
    val topics: List<String>,
)

internal data class ParsedCaptureModel(
    val title: String,
    val sourceTitle: String,
    val sourceLabel: String,
    val sourceDetail: String,
    val summary: String,
    val keyPoints: List<String>,
    val topics: List<String>,
    val sourcePlaceholder: String,
    val saveLabel: String,
)

internal fun textInputReferenceLabels(): List<String> = listOf(
    "纯文本输入",
    "内容（可编辑）",
    "AI 建议标题",
    "类型识别",
    "标签",
    "附件",
    "完成记录",
)

internal fun voiceInputReferenceLabels(): List<String> = listOf(
    "语音输入",
    "录音动态效果",
    "原始内容信息",
    "AI 洞察",
    "删除",
    "继续录入",
    "完成解析",
)

internal fun imageInputReferenceLabels(): List<String> = listOf(
    "图片输入",
    "图片预览",
    "图像理解结果",
    "关键信息提取",
    "结构化识别",
    "OCR 文本(可选)",
    "重新拍摄",
    "从相册导入",
    "继续解析",
)

internal fun textContentReferenceLabels(): List<String> = listOf(
    "文本记录",
    "标题",
    "重新生成标题",
    "正文",
    "润色正文",
    "记录类型",
    "相关主题",
    "AI 洞察",
    "附件",
    "插入今天",
    "链接任务",
    "导入项目",
)

internal fun textContentReferenceExcludedLabels(): List<String> = listOf(
    "AI 整理",
    "归档与时间",
    "标题（可编辑）",
    "生成标题",
    "润色标题",
    "正文内容（可全文编辑）",
    "AI 摘要",
    "关键要点",
    "重新生成摘要与要点",
    "Markdown 预览",
    "编辑正文",
    "已手动确认",
    "先存下这颗火花",
    "B",
    "I",
    "U",
    "•",
    "1.",
    "☑",
)

internal fun mediaContentReferenceExcludedLabels(): List<String> = listOf(
    "归档与时间",
    "Markdown 预览",
    "编辑正文",
    "已手动确认",
)

internal fun voiceContentReferenceLabels(): List<String> = listOf(
    "语音记录",
    "语音暂存音频（可回放）",
    "播放音频",
    "标题",
    "语音转写（可编辑）",
    "AI 洞察",
    "关键信息",
    "记录信息",
    "插入今天",
    "链接任务",
    "导入项目",
)

internal fun imageContentReferenceLabels(): List<String> = listOf(
    "图片记录",
    "图片预览",
    "图片理解摘要（可编辑）",
    "AI 洞察",
    "视觉识别结果",
    "OCR 全文（可选）",
    "记录信息（可修改）",
    "插入今天",
    "链接任务",
    "导入项目",
)

internal fun buildArticleCaptureModel(
    topic: String,
    content: String,
    tags: List<String>,
    updatedAt: Long?,
): ArticleCaptureModel {
    val url = articleUrlFromContent(content)
    val host = runCatching { URI(url).host.orEmpty().removePrefix("www.") }
        .getOrDefault("")
        .ifBlank { "待识别来源" }
    val articleTitle = articleTitleFromContent(content)
    val noteLine = extractNoteLine(content)
    val body = articleBodyFromContent(content)
    val displayTitle = when {
        articleTitle.isNotBlank() -> articleTitle
        noteLine.isNotBlank() -> noteLine
        topic.isNotBlank() && topic != "文章收藏" -> topic
        host != "待识别来源" -> host
        else -> "粘贴链接后提取正文"
    }
    val topics = (tags + listOf("文章"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(4)
    val localTimeHint = if (updatedAt != null) "已保留收藏时间" else "保存后记录收藏时间"

    return ArticleCaptureModel(
        title = displayTitle,
        host = host,
        url = url,
        summary = body.ifBlank { "粘贴链接后可提取正文，并统一生成 AI 洞察。" },
        keyPoints = listOf(
            "来源链接已提取，点按链接后由系统选择合适应用打开。",
            if (body.isBlank()) "正文提取前可以先补充收藏原因。" else "网页正文已进入记录正文区，可直接编辑。",
            "$localTimeHint，并可在回看中继续整理为任务或项目线索。",
        ),
        topics = topics,
    )
}

internal fun buildVoiceCaptureModel(
    topic: String,
    content: String,
    tags: List<String>,
    updatedAt: Long?,
): ParsedCaptureModel {
    val transcript = voiceTranscriptFromContent(content)
    val localTimeHint = if (updatedAt != null) "转写时间已保留" else "保存后记录转写时间"

    return ParsedCaptureModel(
        title = "语音输入",
        sourceTitle = "原始内容信息",
        sourceLabel = "继续录入",
        sourceDetail = transcript.ifBlank { "等待语音识别结果，也可以先手动补录原始内容。" },
        summary = "录音结束后会自动进入 Gemma 4 端侧转写，结果写回转写内容并提取标题。",
        keyPoints = listOf(
            "原始语音内容先落到记录正文，识别失败时也能手动补齐。",
            "摘要只做轻量本地整理，避免在输入阶段打断快速捕捉。",
            "$localTimeHint，并可在回看中继续整理为任务或项目。",
        ),
        topics = normalizedTopics(tags, "语音"),
        sourcePlaceholder = "语音转写、翻译结果或你手动补充的原始内容。",
        saveLabel = "完成解析",
    )
}

internal fun buildImageCaptureModel(
    topic: String,
    content: String,
    tags: List<String>,
    updatedAt: Long?,
): ParsedCaptureModel {
    val imageLine = imagePathFromContent(content)
    val localTimeHint = if (updatedAt != null) "图片记录时间已保留" else "保存后记录图片输入时间"

    return ParsedCaptureModel(
        title = "图片输入",
        sourceTitle = "图片预览",
        sourceLabel = "从相册导入",
        sourceDetail = imageLine.ifBlank { "等待选择图片，或先补充这张图里值得保留的信息。" },
        summary = "本地已保留图片输入说明，后续会优先交给 Gemma 4 端侧能力理解图片内容、提取文字或总结画面。",
        keyPoints = listOf(
            "图片预览与补充说明先进入记录正文，避免识别链路失败时丢失上下文。",
            "AI 会根据图片类型选择文字识别、场景理解、图表解释或摘要。",
            "$localTimeHint，并可在回看中继续整理为任务或项目。",
        ),
        topics = normalizedTopics(tags, "图片"),
        sourcePlaceholder = "OCR 文本、视觉识别结果或你对这张图的说明。",
        saveLabel = "继续解析",
    )
}

private fun firstMeaningfulLine(content: String): String =
    content.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

private fun extractNoteLine(content: String): String = content
    .lineSequence()
    .map { it.trim() }
    .mapNotNull { line ->
        when {
            line.startsWith("补充说明：") -> line.removePrefix("补充说明：")
            line.startsWith("补充说明:") -> line.removePrefix("补充说明:")
            else -> null
        }
    }
    .firstOrNull()
    ?.trim()
    .orEmpty()

private fun normalizedTopics(tags: List<String>, fallback: String): List<String> =
    (tags + fallback)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(4)

private fun extractFirstUrl(content: String): String {
    val match = Regex("""https?://[^\s，。)）]+""").find(content)
    return match?.value?.trim().orEmpty()
}
