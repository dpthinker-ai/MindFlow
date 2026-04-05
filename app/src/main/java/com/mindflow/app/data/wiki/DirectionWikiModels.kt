package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage

data class DirectionWikiDirectionSummary(
    val threadKey: String,
    val slug: String,
    val title: String,
    val stage: DirectionStage = DirectionStage.FORMING,
    val assetSummary: String = "",
    val groundingLine: String = "",
    val signalPoints: List<String> = emptyList(),
    val hypothesisPoints: List<String> = emptyList(),
    val verifiedPoints: List<String> = emptyList(),
    val validatedPoints: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val continuityLine: String = "",
    val trajectoryLine: String = "",
    val stageHistorySummary: String = "",
    val snapshotStageLine: String = "",
    val snapshotCadenceLine: String = "",
    val updatedAt: Long = 0L,
)

data class DirectionWikiSnapshot(
    val rootPath: String = "",
    val lastGeneratedAt: Long = 0L,
    val directions: Map<String, DirectionWikiDirectionSummary> = emptyMap(),
)

data class DirectionWikiRefreshResult(
    val generatedDirectionCount: Int,
    val generatedAt: Long,
)
