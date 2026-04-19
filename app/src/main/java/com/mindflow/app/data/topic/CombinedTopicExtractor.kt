package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.model.TopicExtractionResult
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.model.TopicSuggestion

class CombinedTopicExtractor(
    private val aiTopicExtractor: AiTopicExtractor,
    private val ruleBasedTopicExtractor: RuleBasedTopicExtractor,
) : TopicExtractor {
    override suspend fun extract(
        content: String,
        automaticPreference: AiAutomaticPreference,
    ): TopicExtractionResult {
        val aiResult = aiTopicExtractor.extract(content, automaticPreference)
        return if (aiResult.topic.isNullOrBlank()) {
            TopicExtractionResult(
                suggestion = extractRule(content),
                notice = aiResult.notice,
            )
        } else {
            TopicExtractionResult(
                suggestion = TopicSuggestion(topic = aiResult.topic, source = TopicSource.AI),
                notice = aiResult.notice,
            )
        }
    }

    override fun extractRule(content: String): TopicSuggestion =
        ruleBasedTopicExtractor.toSuggestion(content)
}
