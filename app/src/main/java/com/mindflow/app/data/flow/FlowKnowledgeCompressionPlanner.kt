package com.mindflow.app.data.flow

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
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
    private val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    private val onDeviceAiClient: OnDeviceAiClient,
) {
    private data class CardCompression(
        val line: String,
        val support: String,
        val source: DailyBriefSource,
    )

    private val mainlineCache = linkedMapOf<String, CardCompression>()
    private val settledCache = linkedMapOf<String, CardCompression>()
    private val gapCache = linkedMapOf<String, CardCompression>()

    suspend fun summarize(
        mainlineKey: String,
        settledKey: String,
        gapKey: String,
        mainlineContextSummary: String,
        settledContextSummary: String,
        gapContextSummary: String,
        fallback: FlowKnowledgeCompressionState,
    ): FlowKnowledgeCompressionState {
        return fallback
    }

    private fun buildCardCompression(
        result: AiChatResult?,
        fallbackLine: String,
        fallbackSupport: String,
        preferredSource: DailyBriefSource,
    ): CardCompression {
        val lines = (result as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()
        return CardCompression(
            line = lines.getOrElse(0) { fallbackLine }.ifBlank { fallbackLine },
            support = lines.getOrElse(1) { fallbackSupport }.ifBlank { fallbackSupport },
            source = if (lines.size >= 2) preferredSource else DailyBriefSource.RULE,
        )
    }

    private fun trimCache(cache: LinkedHashMap<String, CardCompression>) {
        if (cache.size > 64) {
            val oldestKey = cache.entries.firstOrNull()?.key ?: return
            cache.remove(oldestKey)
        }
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
