package com.mindflow.app.data.topic

import com.mindflow.app.data.model.TagExtractionResult
import com.mindflow.app.data.model.TagSuggestion

interface TagExtractor {
    suspend fun extract(content: String): TagExtractionResult
    fun extractRule(content: String): TagSuggestion
}
