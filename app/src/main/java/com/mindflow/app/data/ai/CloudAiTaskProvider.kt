package com.mindflow.app.data.ai

import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import org.json.JSONArray
import org.json.JSONObject

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
            AiTaskType.POLISH_CONTENT -> client.polishContent(settings, (input as AiTaskInput.NoteText).content)
            AiTaskType.GRAPH_EXTRACT_CONCEPTS -> client.extractConceptGraphConcepts(settings, (input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> client.canonicalizeConceptGraphConcepts(settings, (input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_GENERATE_RELATIONS -> client.generateConceptGraphRelations(settings, (input as AiTaskInput.GraphContext).contextSummary)
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
    AiTaskType.POLISH_CONTENT -> parsePolishPayload(raw)
    AiTaskType.GRAPH_EXTRACT_CONCEPTS -> parseGraphConceptsPayload(raw)
    AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> parseCanonicalizationPayload(raw)
    AiTaskType.GRAPH_GENERATE_RELATIONS -> parseRelationsPayload(raw)
}

private fun extractJsonObject(raw: String): JSONObject? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
}

private fun parseTopicPayload(raw: String): AiTaskPayload.Topic {
    val json = extractJsonObject(raw)
    val topic = json?.optString("topic").orEmpty().ifBlank { raw.lineSequence().firstOrNull()?.trim().orEmpty() }
    val confidence = json?.optDouble("confidence", 0.0)?.toFloat() ?: 0f
    return AiTaskPayload.Topic(topic = topic.trim(), confidence = confidence)
}

private fun parseTagsPayload(raw: String): AiTaskPayload.Tags {
    val json = extractJsonObject(raw)
    val tags = json?.optJSONArray("tags")?.toStringList()
        ?: raw.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }
    return AiTaskPayload.Tags(
        tags = tags.take(6),
        primaryCategory = json?.optString("primaryCategory")?.takeIf { !it.isNullOrBlank() },
    )
}

private fun parseFolderPayload(raw: String): AiTaskPayload.Folder {
    val json = extractJsonObject(raw)
    val folder = json?.optString("folderKey").orEmpty().ifBlank { raw.lineSequence().firstOrNull()?.trim().orEmpty() }
    val confidence = json?.optDouble("confidence", 0.0)?.toFloat() ?: 0f
    return AiTaskPayload.Folder(folderKey = folder, confidence = confidence)
}

private fun parsePolishPayload(raw: String): AiTaskPayload.Polish {
    val json = extractJsonObject(raw)
    return AiTaskPayload.Polish(
        polishedText = json?.optString("polishedText").orEmpty().ifBlank { raw.trim() },
        changeSummary = json?.optString("changeSummary").orEmpty(),
    )
}

private fun parseGraphConceptsPayload(raw: String): AiTaskPayload.GraphConcepts {
    val json = extractJsonObject(raw)
    val concepts = json?.optJSONArray("concepts")?.toStringList()
        ?: raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    return AiTaskPayload.GraphConcepts(concepts = concepts)
}

private fun parseCanonicalizationPayload(raw: String): AiTaskPayload.GraphCanonicalization {
    val json = extractJsonObject(raw)
    val canonical = linkedMapOf<String, List<String>>()
    json?.optJSONObject("canonical")?.let { map ->
        map.keys().forEach { key ->
            canonical[key] = map.optJSONArray(key)?.toStringList().orEmpty()
        }
    }
    return AiTaskPayload.GraphCanonicalization(canonical = canonical)
}

private fun parseRelationsPayload(raw: String): AiTaskPayload.GraphRelations {
    val json = extractJsonObject(raw)
    val relations = json?.optJSONArray("relations")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    GraphRelation(
                        fromConceptId = item.optString("fromConceptId"),
                        toConceptId = item.optString("toConceptId"),
                        relationType = item.optString("relationType"),
                        reasonLine = item.optString("reasonLine"),
                        confidence = item.optDouble("confidence", 0.0).toFloat(),
                    )
                )
            }
        }
    }.orEmpty()
    return AiTaskPayload.GraphRelations(relations = relations)
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        val value = optString(index).trim()
        if (value.isNotBlank()) add(value)
    }
}
