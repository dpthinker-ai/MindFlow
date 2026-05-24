package com.mindflow.app.data.ai

import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.skills.SkillJsonValue
import com.mindflow.app.data.skills.SkillMiniJsonParser
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient

class CloudAiTaskProvider(
    private val settingsRepository: AiSettingsRepository,
    private val client: AiServiceClient,
) : AiTaskProvider {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
        val settings = settingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) return null
        val input = request.input
        val result = when (request.type) {
            AiTaskType.EXTRACT_TOPIC -> client.extractTopic(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.EXTRACT_TAGS -> client.extractTags(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.CLASSIFY_CATEGORY -> client.classifyFolder(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.POLISH_TITLE -> {
                val title = input as AiTaskInput.TitleText
                client.polishTitle(settings, title.title, title.content)
            }
            AiTaskType.SUMMARIZE_NOTE -> client.summarizeNote(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.POLISH_CONTENT -> client.polishContent(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.TRANSCRIBE_AUDIO,
            AiTaskType.TRANSLATE_AUDIO,
            AiTaskType.UNDERSTAND_IMAGE -> return null
            AiTaskType.GRAPH_EXTRACT_CONCEPTS -> client.extractConceptGraphConcepts(settings, (input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> client.canonicalizeConceptGraphConcepts(settings, (input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_GENERATE_RELATIONS -> client.generateConceptGraphRelations(settings, (input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.TEST_CONNECTION,
            AiTaskType.GRAPH_GENERATE_SNAPSHOT,
            AiTaskType.DAILY_BRIEF,
            AiTaskType.NEXT_ACTION,
            AiTaskType.WEEKLY_REVIEW,
            AiTaskType.FUSION_SUGGESTION,
            AiTaskType.FLOW_MAINLINE,
            AiTaskType.FLOW_SETTLED_KNOWLEDGE,
            AiTaskType.FLOW_BREAKTHROUGH_GAP,
            AiTaskType.THREAD_WORKSPACE,
            AiTaskType.RESEARCH_BRIEF,
            AiTaskType.RESEARCH_ACTION_SUMMARY,
            AiTaskType.THREAD_EXECUTION,
            AiTaskType.EXTERNAL_RESEARCH,
            AiTaskType.STALE_RECONNECT,
            AiTaskType.REVIEW_CHAT_REPLY,
            AiTaskType.REVIEW_CHAT_QUERY_PLAN -> return null
        }
        return result.toPayloadOrNull(request.type) as T?
    }
}

internal fun AiChatResult.toPayloadOrNull(taskType: AiTaskType): AiTaskPayload? = when (this) {
    is AiChatResult.Failure -> null
    is AiChatResult.Success -> parsePayload(taskType, content)
}

private fun parsePayload(taskType: AiTaskType, raw: String): AiTaskPayload? = when (taskType) {
    AiTaskType.EXTRACT_TOPIC -> parseTopicPayload(raw)
    AiTaskType.EXTRACT_TAGS -> parseTagsPayload(raw)
    AiTaskType.CLASSIFY_CATEGORY -> parseFolderPayload(raw)
    AiTaskType.POLISH_TITLE -> parsePolishPayload(raw)
    AiTaskType.SUMMARIZE_NOTE -> parseNoteInsightPayload(raw)
    AiTaskType.POLISH_CONTENT -> parsePolishPayload(raw)
    AiTaskType.TRANSCRIBE_AUDIO -> parseAudioTranscriptionPayload(raw)
    AiTaskType.TRANSLATE_AUDIO -> parseAudioTranslationPayload(raw)
    AiTaskType.UNDERSTAND_IMAGE -> parseImageUnderstandingPayload(raw)
    AiTaskType.GRAPH_EXTRACT_CONCEPTS -> parseGraphConceptsPayload(raw)
    AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> parseCanonicalizationPayload(raw)
    AiTaskType.GRAPH_GENERATE_RELATIONS -> parseRelationsPayload(raw)
    AiTaskType.TEST_CONNECTION,
    AiTaskType.GRAPH_GENERATE_SNAPSHOT,
    AiTaskType.DAILY_BRIEF,
    AiTaskType.NEXT_ACTION,
    AiTaskType.WEEKLY_REVIEW,
    AiTaskType.FUSION_SUGGESTION,
    AiTaskType.FLOW_MAINLINE,
    AiTaskType.FLOW_SETTLED_KNOWLEDGE,
    AiTaskType.FLOW_BREAKTHROUGH_GAP,
    AiTaskType.THREAD_WORKSPACE,
    AiTaskType.RESEARCH_BRIEF,
    AiTaskType.RESEARCH_ACTION_SUMMARY,
    AiTaskType.THREAD_EXECUTION,
    AiTaskType.EXTERNAL_RESEARCH,
    AiTaskType.STALE_RECONNECT,
    AiTaskType.REVIEW_CHAT_REPLY,
    AiTaskType.REVIEW_CHAT_QUERY_PLAN -> null
}

private fun extractJsonObject(raw: String): Map<String, SkillJsonValue>? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { SkillMiniJsonParser(raw.substring(start, end + 1)).parseObject() }.getOrNull()
}

private fun parseTopicPayload(raw: String): AiTaskPayload.Topic {
    val json = extractJsonObject(raw)
    val topic = json.stringValue("topic").orEmpty().ifBlank { raw.lineSequence().firstOrNull()?.trim().orEmpty() }
    val confidence = json.doubleValue("confidence").toFloat()
    return AiTaskPayload.Topic(topic = topic.trim(), confidence = confidence)
}

private fun parseTagsPayload(raw: String): AiTaskPayload.Tags {
    val json = extractJsonObject(raw)
    val tags = json.stringArrayValue("tags").takeIf { it.isNotEmpty() }
        ?: raw.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }
    return AiTaskPayload.Tags(
        tags = tags.take(6),
        primaryCategory = json.stringValue("primaryCategory"),
    )
}

private fun parseFolderPayload(raw: String): AiTaskPayload.Folder {
    val json = extractJsonObject(raw)
    val folder = json.stringValue("folderKey").orEmpty().ifBlank { raw.lineSequence().firstOrNull()?.trim().orEmpty() }
    val confidence = json.doubleValue("confidence").toFloat()
    return AiTaskPayload.Folder(folderKey = folder, confidence = confidence)
}

private fun parsePolishPayload(raw: String): AiTaskPayload.Polish {
    val json = extractJsonObject(raw)
    return AiTaskPayload.Polish(
        polishedText = json.stringValue("polishedText").orEmpty().ifBlank { raw.trim() },
        changeSummary = json.stringValue("changeSummary").orEmpty(),
    )
}

private fun parseNoteInsightPayload(raw: String): AiTaskPayload.NoteInsight {
    val json = extractJsonObject(raw)
    val keyPoints = json.stringArrayValue("keyPoints").ifEmpty {
        json.stringArrayValue("points")
    }
    return AiTaskPayload.NoteInsight(
        summary = json.stringValue("summary").orEmpty(),
        keyPoints = keyPoints,
    )
}

private fun parseAudioTranscriptionPayload(raw: String): AiTaskPayload.AudioTranscription {
    val json = extractJsonObject(raw)
    val transcript = sanitizeAudioTranscript(
        json.stringValue("transcript").orEmpty().ifBlank { raw.trim() },
    )
    return AiTaskPayload.AudioTranscription(
        transcript = transcript,
        language = json.stringValue("language"),
        topic = "",
        confidence = json.doubleValue("confidence").toFloat(),
    )
}

private fun sanitizeAudioTranscript(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val leakStart = AudioPromptLeakMarkers.asSequence()
        .map { marker -> trimmed.indexOfPromptLeak(marker) }
        .filter { it >= 0 }
        .minOrNull()
    val withoutPromptLeak = if (leakStart == null) trimmed else trimmed.substring(0, leakStart)
    return withoutPromptLeak.trim().trim('"', '\'', '`')
}

private fun String.indexOfPromptLeak(marker: String): Int {
    val directIndex = indexOf(marker, ignoreCase = true)
    if (directIndex >= 0) return directIndex

    val compactText = StringBuilder(length)
    val sourceIndexes = mutableListOf<Int>()
    forEachIndexed { index, char ->
        if (!char.isWhitespace()) {
            compactText.append(char)
            sourceIndexes += index
        }
    }
    val compactMarker = marker.filterNot { it.isWhitespace() }
    val compactIndex = compactText.indexOf(compactMarker, ignoreCase = true)
    return if (compactIndex >= 0) sourceIndexes[compactIndex] else -1
}

private val AudioPromptLeakMarkers = listOf(
    "你在做语音转写",
    "目标：把录音中的真实说话内容",
    "规则：只记录音频中实际听到的话",
    "禁止输出任务说明",
    "提示词、文件名、路径",
    "如果没有清晰语音",
    "音频输入不可用",
    "只返回 JSON",
)

private fun parseAudioTranslationPayload(raw: String): AiTaskPayload.AudioTranslation {
    val json = extractJsonObject(raw)
    return AiTaskPayload.AudioTranslation(
        translatedText = json.stringValue("translatedText").orEmpty().ifBlank { raw.trim() },
        sourceLanguage = json.stringValue("sourceLanguage"),
        targetLanguage = json.stringValue("targetLanguage").orEmpty(),
        confidence = json.doubleValue("confidence").toFloat(),
    )
}

private fun parseImageUnderstandingPayload(raw: String): AiTaskPayload.ImageUnderstanding {
    val json = extractJsonObject(raw)
    return AiTaskPayload.ImageUnderstanding(
        summary = json.stringValue("summary").orEmpty().ifBlank { raw.trim() },
        imageType = json.stringValue("imageType").orEmpty(),
        extractedText = json.stringValue("extractedText").orEmpty(),
        objects = json.stringArrayValue("objects"),
        confidence = json.doubleValue("confidence").toFloat(),
    )
}

private fun parseGraphConceptsPayload(raw: String): AiTaskPayload.GraphConcepts {
    val json = extractJsonObject(raw)
    val concepts = json.stringArrayValue("concepts").takeIf { it.isNotEmpty() }
        ?: raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    return AiTaskPayload.GraphConcepts(concepts = concepts)
}

private fun parseCanonicalizationPayload(raw: String): AiTaskPayload.GraphCanonicalization {
    val json = extractJsonObject(raw)
    val canonical = linkedMapOf<String, List<String>>()
    json.objectValue("canonical").let { map ->
        map.keys.forEach { key ->
            canonical[key] = map.stringArrayValue(key)
        }
    }
    return AiTaskPayload.GraphCanonicalization(canonical = canonical)
}

private fun parseRelationsPayload(raw: String): AiTaskPayload.GraphRelations {
    val json = extractJsonObject(raw)
    val relations = json.objectArrayValue("relations").map { item ->
        GraphRelation(
            fromConceptId = item.stringValue("fromConceptId").orEmpty(),
            toConceptId = item.stringValue("toConceptId").orEmpty(),
            relationType = item.stringValue("relationType").orEmpty(),
            reasonLine = item.stringValue("reasonLine").orEmpty(),
            confidence = item.doubleValue("confidence").toFloat(),
        )
    }
    return AiTaskPayload.GraphRelations(relations = relations)
}

private fun Map<String, SkillJsonValue>?.stringValue(key: String): String? =
    ((this?.get(key) as? SkillJsonValue.JsonString)?.value)?.takeIf { it.isNotBlank() }

private fun Map<String, SkillJsonValue>?.doubleValue(key: String): Double =
    (this?.get(key) as? SkillJsonValue.JsonNumber)?.value ?: 0.0

private fun Map<String, SkillJsonValue>?.objectValue(key: String): Map<String, SkillJsonValue> =
    (this?.get(key) as? SkillJsonValue.JsonObject)?.values.orEmpty()

private fun Map<String, SkillJsonValue>?.stringArrayValue(key: String): List<String> {
    val array = this?.get(key) as? SkillJsonValue.JsonArray ?: return emptyList()
    return array.items.mapNotNull { item ->
        (item as? SkillJsonValue.JsonString)?.value?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun Map<String, SkillJsonValue>?.objectArrayValue(key: String): List<Map<String, SkillJsonValue>> {
    val array = this?.get(key) as? SkillJsonValue.JsonArray ?: return emptyList()
    return array.items.mapNotNull { item ->
        (item as? SkillJsonValue.JsonObject)?.values
    }
}
