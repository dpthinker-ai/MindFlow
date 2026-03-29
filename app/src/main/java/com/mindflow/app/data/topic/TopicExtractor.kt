package com.mindflow.app.data.topic

import com.mindflow.app.data.model.TopicExtractionResult
import com.mindflow.app.data.model.TopicSuggestion

interface TopicExtractor {
    suspend fun extract(content: String): TopicExtractionResult
    fun extractRule(content: String): TopicSuggestion
}
