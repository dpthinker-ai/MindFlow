package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.model.TagExtractionResult
import com.mindflow.app.data.model.TagSuggestion

interface TagExtractor {
    suspend fun extract(
        content: String,
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): TagExtractionResult
    fun extractRule(content: String): TagSuggestion
}
