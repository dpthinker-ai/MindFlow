package com.mindflow.app.data.topic

import com.mindflow.app.data.model.FolderExtractionResult
import com.mindflow.app.data.model.FolderSuggestion

interface FolderClassifier {
    suspend fun classify(content: String): FolderExtractionResult
    fun classifyRule(content: String): FolderSuggestion
}
