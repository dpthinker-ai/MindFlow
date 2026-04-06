package com.mindflow.app.data.flow

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate

data class FlowKnowledgeCompressionState(
    val mainline: String = "",
    val whyNow: String = "",
    val settledLine: String = "",
    val settledSupport: String = "",
    val gapLine: String = "",
    val gapSupport: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowKnowledgeCompressionPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    private val cache = linkedMapOf<String, FlowKnowledgeCompressionState>()

    suspend fun summarize(
        signature: String,
        contextSummary: String,
        fallback: FlowKnowledgeCompressionState,
    ): FlowKnowledgeCompressionState {
        if (signature.isBlank()) return fallback
        cache[signature]?.let { return it }

        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()
        val resolved = if (settings.aiEnabled && settings.isConfigured && contextSummary.isNotBlank()) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateFlowKnowledgeCompression(
                    settings = settings,
                    contextSummary = contextSummary,
                )
            ) {
                is AiChatResult.Success -> {
                    val lines = parseLines(result.content)
                    if (lines.size >= 5) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        fallback.copy(
                            mainline = lines.getOrElse(0) { fallback.mainline }.ifBlank { fallback.mainline },
                            whyNow = lines.getOrElse(1) { fallback.whyNow }.ifBlank { fallback.whyNow },
                            settledLine = lines.getOrElse(2) { fallback.settledLine }.ifBlank { fallback.settledLine },
                            settledSupport = lines.getOrElse(3) { fallback.settledSupport }.ifBlank { fallback.settledSupport },
                            gapLine = lines.getOrElse(4) { fallback.gapLine }.ifBlank { fallback.gapLine },
                            gapSupport = lines.getOrElse(5) { fallback.gapSupport }.ifBlank { fallback.gapSupport },
                            source = DailyBriefSource.AI,
                        )
                    } else {
                        fallback
                    }
                }

                is AiChatResult.Failure -> fallback
            }
        } else {
            fallback
        }

        cache[signature] = resolved
        if (cache.size > 64) {
            val oldestKey = cache.entries.firstOrNull()?.key ?: return resolved
            cache.remove(oldestKey)
        }
        return resolved
    }

    private fun parseLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .removePrefix("3.")
                    .removePrefix("4.")
                    .removePrefix("5.")
                    .removePrefix("6.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(6)
            .toList()
}
