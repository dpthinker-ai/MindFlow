package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.model.FolderExtractionResult
import com.mindflow.app.data.model.FolderSuggestion

interface FolderClassifier {
    suspend fun classify(
        content: String,
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): FolderExtractionResult
    fun classifyRule(content: String): FolderSuggestion
}
