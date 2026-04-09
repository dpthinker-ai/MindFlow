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
        if (mainlineKey.isBlank() || settledKey.isBlank() || gapKey.isBlank()) return fallback

        val onDeviceSettings = onDeviceModelSettingsRepository.getCurrent()
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()
        val localEnabled = onDeviceSettings.preferOnDevice && onDeviceSettings.isReady
        val localMainlineKey = if (localEnabled) "local:${onDeviceSettings.localModelPath}:$mainlineKey" else mainlineKey
        val localSettledKey = if (localEnabled) "local:${onDeviceSettings.localModelPath}:$settledKey" else settledKey
        val localGapKey = if (localEnabled) "local:${onDeviceSettings.localModelPath}:$gapKey" else gapKey
        val shouldCallAi = (
            settings.aiEnabled &&
            settings.isConfigured &&
            mainlineContextSummary.isNotBlank() &&
            settledContextSummary.isNotBlank() &&
            gapContextSummary.isNotBlank()
        )
        val resolved = if (localEnabled) {
            val mainlineCard = mainlineCache[localMainlineKey]
                ?: buildCardCompression(
                    result = onDeviceAiClient.generateFlowMainline(
                        settings = onDeviceSettings,
                        contextSummary = mainlineContextSummary,
                    ),
                    fallbackLine = fallback.mainline,
                    fallbackSupport = fallback.whyNow,
                ).also {
                    mainlineCache[localMainlineKey] = it
                    trimCache(mainlineCache)
                }
            val settledCard = settledCache[localSettledKey]
                ?: buildCardCompression(
                    result = onDeviceAiClient.generateFlowSettledKnowledge(
                        settings = onDeviceSettings,
                        contextSummary = settledContextSummary,
                    ),
                    fallbackLine = fallback.settledLine,
                    fallbackSupport = fallback.settledSupport,
                ).also {
                    settledCache[localSettledKey] = it
                    trimCache(settledCache)
                }
            val gapCard = gapCache[localGapKey]
                ?: buildCardCompression(
                    result = onDeviceAiClient.generateFlowBreakthroughGap(
                        settings = onDeviceSettings,
                        contextSummary = gapContextSummary,
                    ),
                    fallbackLine = fallback.gapLine,
                    fallbackSupport = fallback.gapSupport,
                ).also {
                    gapCache[localGapKey] = it
                    trimCache(gapCache)
                }

            fallback.copy(
                mainline = mainlineCard.line,
                whyNow = mainlineCard.support,
                mainlineSource = mainlineCard.source,
                settledLine = settledCard.line,
                settledSupport = settledCard.support,
                settledSource = settledCard.source,
                gapLine = gapCard.line,
                gapSupport = gapCard.support,
                gapSource = gapCard.source,
            )
        } else if (shouldCallAi) {
            val needMainline = mainlineCache[mainlineKey] == null
            val needSettled = settledCache[settledKey] == null
            val needGap = gapCache[gapKey] == null
            val requestCount = listOf(needMainline, needSettled, needGap).count { it }
            if (requestCount > 0) {
            aiSettingsRepository.recordUsage(
                    requestIncrement = requestCount,
                dayKey = dayKey,
            )
            }
            coroutineScope {
                val mainlineDeferred = if (needMainline) {
                    async {
                        aiServiceClient.generateFlowMainline(
                            settings = settings,
                            contextSummary = mainlineContextSummary,
                        )
                    }
                } else {
                    null
                }
                val settledDeferred = if (needSettled) {
                    async {
                        aiServiceClient.generateFlowSettledKnowledge(
                            settings = settings,
                            contextSummary = settledContextSummary,
                        )
                    }
                } else {
                    null
                }
                val gapDeferred = if (needGap) {
                    async {
                        aiServiceClient.generateFlowBreakthroughGap(
                            settings = settings,
                            contextSummary = gapContextSummary,
                        )
                    }
                } else {
                    null
                }

                val mainlineResult = mainlineDeferred?.await()
                val settledResult = settledDeferred?.await()
                val gapResult = gapDeferred?.await()

                val successCount = listOf(mainlineResult, settledResult, gapResult)
                    .count { it is AiChatResult.Success }
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

                val mainlineCard = mainlineCache[mainlineKey]
                    ?: buildCardCompression(
                        result = mainlineResult,
                        fallbackLine = fallback.mainline,
                        fallbackSupport = fallback.whyNow,
                    ).also {
                        mainlineCache[mainlineKey] = it
                        trimCache(mainlineCache)
                    }
                val settledCard = settledCache[settledKey]
                    ?: buildCardCompression(
                        result = settledResult,
                        fallbackLine = fallback.settledLine,
                        fallbackSupport = fallback.settledSupport,
                    ).also {
                        settledCache[settledKey] = it
                        trimCache(settledCache)
                    }
                val gapCard = gapCache[gapKey]
                    ?: buildCardCompression(
                        result = gapResult,
                        fallbackLine = fallback.gapLine,
                        fallbackSupport = fallback.gapSupport,
                    ).also {
                        gapCache[gapKey] = it
                        trimCache(gapCache)
                    }

                fallback.copy(
                    mainline = mainlineCard.line,
                    whyNow = mainlineCard.support,
                    mainlineSource = mainlineCard.source,
                    settledLine = settledCard.line,
                    settledSupport = settledCard.support,
                    settledSource = settledCard.source,
                    gapLine = gapCard.line,
                    gapSupport = gapCard.support,
                    gapSource = gapCard.source,
                )
            }
        } else {
            fallback
        }
        return resolved
    }

    private fun buildCardCompression(
        result: AiChatResult?,
        fallbackLine: String,
        fallbackSupport: String,
    ): CardCompression {
        val lines = (result as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()
        return CardCompression(
            line = lines.getOrElse(0) { fallbackLine }.ifBlank { fallbackLine },
            support = lines.getOrElse(1) { fallbackSupport }.ifBlank { fallbackSupport },
            source = if (lines.size >= 2) DailyBriefSource.AI else DailyBriefSource.RULE,
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
