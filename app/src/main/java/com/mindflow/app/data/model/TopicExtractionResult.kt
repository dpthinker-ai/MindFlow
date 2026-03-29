package com.mindflow.app.data.model

data class TopicExtractionResult(
    val suggestion: TopicSuggestion,
    val notice: String? = null,
)
