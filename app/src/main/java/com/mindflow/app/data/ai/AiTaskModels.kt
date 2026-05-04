package com.mindflow.app.data.ai

enum class AiTaskType {
    POLISH_CONTENT,
    POLISH_TITLE,
    SUMMARIZE_NOTE,
    EXTRACT_TOPIC,
    EXTRACT_TAGS,
    CLASSIFY_CATEGORY,
    TRANSCRIBE_AUDIO,
    TRANSLATE_AUDIO,
    UNDERSTAND_IMAGE,
    GRAPH_EXTRACT_CONCEPTS,
    GRAPH_CANONICALIZE_CONCEPTS,
    GRAPH_GENERATE_RELATIONS,
}

enum class AiAutomaticPreference {
    PREFER_ON_DEVICE,
    PREFER_CLOUD,
}

sealed interface AiTaskInput {
    data class NoteText(val content: String) : AiTaskInput
    data class TitleText(val title: String, val content: String) : AiTaskInput
    data class GraphContext(val contextSummary: String) : AiTaskInput
    data class AudioFile(
        val path: String,
        val mimeType: String? = null,
        val localeHint: String? = null,
        val targetLanguage: String? = null,
    ) : AiTaskInput

    data class ImageFile(
        val path: String,
        val mimeType: String? = null,
        val userNote: String = "",
    ) : AiTaskInput
}

sealed interface AiTaskPayload {
    data class Topic(val topic: String, val confidence: Float) : AiTaskPayload
    data class Tags(val tags: List<String>, val primaryCategory: String? = null) : AiTaskPayload
    data class Folder(val folderKey: String, val confidence: Float) : AiTaskPayload
    data class Polish(val polishedText: String, val changeSummary: String) : AiTaskPayload
    data class NoteInsight(
        val summary: String,
        val keyPoints: List<String>,
    ) : AiTaskPayload
    data class AudioTranscription(
        val transcript: String,
        val language: String? = null,
        val topic: String = "",
        val confidence: Float = 0f,
    ) : AiTaskPayload

    data class AudioTranslation(
        val translatedText: String,
        val sourceLanguage: String? = null,
        val targetLanguage: String,
        val confidence: Float = 0f,
    ) : AiTaskPayload

    data class ImageUnderstanding(
        val summary: String,
        val imageType: String,
        val extractedText: String = "",
        val objects: List<String> = emptyList(),
        val confidence: Float = 0f,
    ) : AiTaskPayload

    data class GraphConcepts(val concepts: List<String>) : AiTaskPayload
    data class GraphCanonicalization(val canonical: Map<String, List<String>>) : AiTaskPayload
    data class GraphRelations(val relations: List<GraphRelation>) : AiTaskPayload
}

data class GraphRelation(
    val fromConceptId: String,
    val toConceptId: String,
    val relationType: String,
    val reasonLine: String,
    val confidence: Float,
)

data class AiTaskMeta(
    val providerUsed: AiProvider,
    val fallbackOccurred: Boolean,
    val fallbackReason: String? = null,
    val latencyMs: Long,
    val qualitySignals: Map<String, String> = emptyMap(),
)

data class AiTaskResult<T : AiTaskPayload>(
    val payload: T,
    val meta: AiTaskMeta,
)

class AiTaskRoutingException(
    val mode: AiExecutionMode,
    val taskType: AiTaskType,
    val firstFailureReason: String?,
) : IllegalStateException(
    "No provider produced a valid payload for $taskType in $mode",
)

data class AiTaskRequest<T : AiTaskPayload>(
    val type: AiTaskType,
    val input: AiTaskInput,
    val automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    val allowProviderFallback: Boolean = true,
    val validate: (T) -> Boolean,
)

interface AiTaskProvider {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T?
}
