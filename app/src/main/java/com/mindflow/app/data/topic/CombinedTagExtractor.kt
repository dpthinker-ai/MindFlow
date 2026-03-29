package com.mindflow.app.data.topic

import com.mindflow.app.data.model.TagExtractionResult
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TagSuggestion

class CombinedTagExtractor(
    private val aiTagExtractor: AiTagExtractor,
    private val ruleBasedTagExtractor: RuleBasedTagExtractor,
) : TagExtractor {
    override suspend fun extract(content: String): TagExtractionResult {
        val aiResult = aiTagExtractor.extract(content)
        return if (aiResult.tags.isEmpty()) {
            TagExtractionResult(
                suggestion = extractRule(content),
                notice = aiResult.notice,
            )
        } else {
            TagExtractionResult(
                suggestion = TagSuggestion(aiResult.tags, TagSource.AI),
                notice = aiResult.notice,
            )
        }
    }

    override fun extractRule(content: String): TagSuggestion =
        ruleBasedTagExtractor.toSuggestion(content)
}
