package com.mindflow.app.data.model

data class TagExtractionResult(
    val suggestion: TagSuggestion,
    val notice: String? = null,
)
