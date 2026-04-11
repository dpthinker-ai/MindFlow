package com.mindflow.app.ui.screens.flow

import com.mindflow.app.data.brief.DailyBriefSource

data class FlowCardProvenance(
    val label: String,
) {
    companion object {
        val LocalMaintainer = FlowCardProvenance("本地维护")
        val LocalModel = FlowCardProvenance("本地模型")
        val CloudUpgrade = FlowCardProvenance("云端升级")
        val Rules = FlowCardProvenance("规则整理")
    }
}

data class IncubationSurfaceState(
    val sparkDirection: FollowedDirectionSummary? = null,
    val threadDirection: FollowedDirectionSummary? = null,
    val collisionDirection: FollowedDirectionSummary? = null,
    val assetDirection: FollowedDirectionSummary? = null,
    val gapDirection: FollowedDirectionSummary? = null,
    val sparkProvenance: FlowCardProvenance = FlowCardProvenance.LocalMaintainer,
    val threadProvenance: FlowCardProvenance = FlowCardProvenance.Rules,
    val collisionProvenance: FlowCardProvenance = FlowCardProvenance.Rules,
    val assetProvenance: FlowCardProvenance = FlowCardProvenance.Rules,
    val gapProvenance: FlowCardProvenance = FlowCardProvenance.LocalMaintainer,
)

fun FlowUiState.toIncubationSurfaceState(): IncubationSurfaceState {
    val sparkDirection = followedDirections.firstOrNull { it.thread.key == localMaintainerSnapshot.recentAbsorption.threadKey }
        ?: followedDirections.firstOrNull()
    val collisionDirection = followedDirections.firstOrNull { it.thread.key == localMaintainerSnapshot.newConnection.threadKey }
        ?: followedDirections.firstOrNull {
            it.wikiOpenQuestion.isNotBlank() ||
                it.wikiMaintenanceLine.isNotBlank() ||
                it.wikiMaintenanceFocusLine.isNotBlank() ||
                it.blocker.isNotBlank()
        } ?: followedDirections.firstOrNull()
    val threadDirection = followedDirections.firstOrNull { it.thread.key == mainlineCandidate?.threadKey }
        ?: followedDirections.firstOrNull { it.thread.key == localMaintainerSnapshot.currentJudgement.threadKey }
        ?: followedDirections.firstOrNull()
    val assetDirection = followedDirections.firstOrNull { it.thread.key == localMaintainerSnapshot.recentAbsorption.threadKey }
        ?: followedDirections.firstOrNull {
            it.wikiValidatedPoint.isNotBlank() ||
                it.wikiVerifiedPoint.isNotBlank() ||
                it.wikiConclusionLine.isNotBlank() ||
                it.assetSummary.isNotBlank()
        } ?: followedDirections.firstOrNull()
    val gapDirection = followedDirections.firstOrNull { it.thread.key == localMaintainerSnapshot.openQuestion.threadKey }
        ?: followedDirections.firstOrNull {
            it.wikiOpenQuestion.isNotBlank() ||
                it.wikiMaintenanceLine.isNotBlank() ||
                it.wikiMaintenanceFocusLine.isNotBlank() ||
                it.validationStep.isNotBlank() ||
                it.blocker.isNotBlank()
        } ?: followedDirections.firstOrNull()
    return IncubationSurfaceState(
        sparkDirection = sparkDirection,
        threadDirection = threadDirection,
        collisionDirection = collisionDirection,
        assetDirection = assetDirection,
        gapDirection = gapDirection,
        sparkProvenance = FlowCardProvenance.LocalMaintainer,
        threadProvenance = when {
            knowledgeCompression.mainline.isNotBlank() -> knowledgeCompression.mainlineSource.toProvenance()
            localMaintainerSnapshot.currentJudgement.hasContent -> FlowCardProvenance.LocalMaintainer
            else -> FlowCardProvenance.Rules
        },
        collisionProvenance = when {
            knowledgeCompression.gapLine.isNotBlank() -> knowledgeCompression.gapSource.toProvenance()
            localMaintainerSnapshot.newConnection.hasContent -> FlowCardProvenance.LocalMaintainer
            collisionDirection?.source == DailyBriefSource.AI -> FlowCardProvenance.CloudUpgrade
            else -> FlowCardProvenance.Rules
        },
        assetProvenance = when {
            localMaintainerSnapshot.recentAbsorption.hasContent -> FlowCardProvenance.LocalMaintainer
            knowledgeCompression.settledLine.isNotBlank() -> knowledgeCompression.settledSource.toProvenance()
            assetDirection?.source == DailyBriefSource.AI -> FlowCardProvenance.CloudUpgrade
            else -> FlowCardProvenance.Rules
        },
        gapProvenance = when {
            localMaintainerSnapshot.openQuestion.hasContent -> FlowCardProvenance.LocalMaintainer
            gapDirection?.source == DailyBriefSource.AI -> FlowCardProvenance.CloudUpgrade
            else -> FlowCardProvenance.LocalMaintainer
        },
    )
}

private fun DailyBriefSource.toProvenance(): FlowCardProvenance =
    when (this) {
        DailyBriefSource.LOCAL -> FlowCardProvenance.LocalModel
        DailyBriefSource.AI -> FlowCardProvenance.CloudUpgrade
        DailyBriefSource.RULE -> FlowCardProvenance.Rules
    }
