package com.mindflow.app.data.flow

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class FlowKnowledgeCompressionState(
    val mainline: String = "",
    val whyNow: String = "",
    val mainlineSource: DailyBriefSource = DailyBriefSource.RULE,
    val settledLine: String = "",
    val settledSupport: String = "",
    val settledSource: DailyBriefSource = DailyBriefSource.RULE,
    val gapLine: String = "",
    val gapSupport: String = "",
    val gapSource: DailyBriefSource = DailyBriefSource.RULE,
)

class FlowKnowledgeCompressionPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    private val cache = linkedMapOf<String, FlowKnowledgeCompressionState>()

    suspend fun summarize(
        signature: String,
        mainlineContextSummary: String,
        settledContextSummary: String,
        gapContextSummary: String,
        fallback: FlowKnowledgeCompressionState,
    ): FlowKnowledgeCompressionState {
        if (signature.isBlank()) return fallback
        cache[signature]?.let { return it }

        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()
        val resolved = if (
            settings.aiEnabled &&
            settings.isConfigured &&
            mainlineContextSummary.isNotBlank() &&
            settledContextSummary.isNotBlank() &&
            gapContextSummary.isNotBlank()
        ) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 3,
                dayKey = dayKey,
            )
            coroutineScope {
                val mainlineDeferred = async {
                    aiServiceClient.generateFlowMainline(
                        settings = settings,
                        contextSummary = mainlineContextSummary,
                    )
                }
                val settledDeferred = async {
                    aiServiceClient.generateFlowSettledKnowledge(
                        settings = settings,
                        contextSummary = settledContextSummary,
                    )
                }
                val gapDeferred = async {
                    aiServiceClient.generateFlowBreakthroughGap(
                        settings = settings,
                        contextSummary = gapContextSummary,
                    )
                }

                val mainlineResult = mainlineDeferred.await()
                val settledResult = settledDeferred.await()
                val gapResult = gapDeferred.await()

                val mainlineLines = (mainlineResult as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()
                val settledLines = (settledResult as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()
                val gapLines = (gapResult as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()

                val successCount =
                    listOf(mainlineResult, settledResult, gapResult).count { it is AiChatResult.Success }
                val tokenCount = listOf(mainlineResult, settledResult, gapResult)
                    .mapNotNull { (it as? AiChatResult.Success)?.totalTokens }
                    .sum()

                if (successCount > 0) {
                    aiSettingsRepository.recordUsage(
                        successIncrement = successCount,
                        tokenIncrement = tokenCount,
                        dayKey = dayKey,
                    )
                }

                fallback.copy(
                    mainline = mainlineLines.getOrElse(0) { fallback.mainline }.ifBlank { fallback.mainline },
                    whyNow = mainlineLines.getOrElse(1) { fallback.whyNow }.ifBlank { fallback.whyNow },
                    mainlineSource = if (mainlineLines.size >= 2) DailyBriefSource.AI else fallback.mainlineSource,
                    settledLine = settledLines.getOrElse(0) { fallback.settledLine }.ifBlank { fallback.settledLine },
                    settledSupport = settledLines.getOrElse(1) { fallback.settledSupport }.ifBlank { fallback.settledSupport },
                    settledSource = if (settledLines.size >= 2) DailyBriefSource.AI else fallback.settledSource,
                    gapLine = gapLines.getOrElse(0) { fallback.gapLine }.ifBlank { fallback.gapLine },
                    gapSupport = gapLines.getOrElse(1) { fallback.gapSupport }.ifBlank { fallback.gapSupport },
                    gapSource = if (gapLines.size >= 2) DailyBriefSource.AI else fallback.gapSource,
                )
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
