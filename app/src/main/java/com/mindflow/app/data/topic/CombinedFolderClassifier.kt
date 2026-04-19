package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.model.FolderExtractionResult
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.FolderSuggestion

class CombinedFolderClassifier(
    private val aiFolderClassifier: AiFolderClassifier,
    private val ruleBasedFolderClassifier: RuleBasedFolderClassifier,
) : FolderClassifier {
    override suspend fun classify(
        content: String,
        automaticPreference: AiAutomaticPreference,
    ): FolderExtractionResult {
        val aiResult = aiFolderClassifier.classify(content, automaticPreference)
        return if (aiResult.folderKey.isNullOrBlank()) {
            FolderExtractionResult(
                suggestion = classifyRule(content),
                notice = aiResult.notice,
            )
        } else {
            FolderExtractionResult(
                suggestion = FolderSuggestion(folderKey = aiResult.folderKey, source = FolderSource.AI),
                notice = aiResult.notice,
            )
        }
    }

    override fun classifyRule(content: String): FolderSuggestion =
        ruleBasedFolderClassifier.toSuggestion(content)
}
