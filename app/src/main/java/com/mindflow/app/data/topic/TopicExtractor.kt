package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.model.TopicExtractionResult
import com.mindflow.app.data.model.TopicSuggestion

interface TopicExtractor {
    suspend fun extract(
        content: String,
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): TopicExtractionResult
    fun extractRule(content: String): TopicSuggestion
}
