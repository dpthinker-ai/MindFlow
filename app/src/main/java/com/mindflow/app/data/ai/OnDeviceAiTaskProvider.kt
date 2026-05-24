package com.mindflow.app.data.ai

import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository

class OnDeviceAiTaskProvider(
    private val settingsRepository: OnDeviceModelSettingsRepository,
    private val client: OnDeviceAiClient,
) : AiTaskProvider {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
        val settings = settingsRepository.getCurrent()
        if (!settings.isReady) return null
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
            AiTaskType.TRANSCRIBE_AUDIO -> {
                val audio = input as AiTaskInput.AudioFile
                client.transcribeAudio(settings, audio.path, audio.localeHint)
            }
            AiTaskType.TRANSLATE_AUDIO -> {
                val audio = input as AiTaskInput.AudioFile
                client.translateAudio(settings, audio.path, audio.targetLanguage ?: "zh-CN")
            }
            AiTaskType.UNDERSTAND_IMAGE -> {
                val image = input as AiTaskInput.ImageFile
                client.understandImage(settings, image.path, image.userNote)
            }
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
