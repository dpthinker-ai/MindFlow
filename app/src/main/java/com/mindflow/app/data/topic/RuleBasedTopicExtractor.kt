package com.mindflow.app.data.topic

import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.model.TopicSuggestion

class RuleBasedTopicExtractor {
    private val splitPattern = Regex("[。！？!?；;，,、\\n]")
    private val trimPattern = Regex("[*_#>`\\-]+")
    private val noisyPrefixPattern = Regex("^(我想|我要|想做|计划|记录|idea[:：]?|todo[:：]?|note[:：]?|关于)")

    fun extract(content: String): String {
        val firstMeaningfulLine = content
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val rawCandidate = splitPattern
            .split(firstMeaningfulLine)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val cleaned = noisyPrefixPattern
            .replace(rawCandidate.trim(), "")
            .replace(trimPattern, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned.ifBlank { "未命名想法" }.take(24).trim()
    }

    fun toSuggestion(content: String): TopicSuggestion =
        TopicSuggestion(topic = extract(content), source = TopicSource.RULE)
}
