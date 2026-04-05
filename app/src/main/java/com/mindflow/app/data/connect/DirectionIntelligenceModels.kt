package com.mindflow.app.data.connect

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.model.NoteHorizon

data class ThreadExecutionSummary(
    val focusNoteId: Long? = null,
    val summary: String = "",
    val blocker: String = "",
    val stage: DirectionStage = DirectionStage.FORMING,
    val stageReason: String = "",
    val rhythmLine: String = "",
    val dominantHorizon: NoteHorizon = NoteHorizon.MEDIUM,
    val whyNow: String = "",
    val lastProgressLine: String = "",
    val nextStep: String = "",
    val nextCheckInLine: String = "",
    val validationStep: String = "",
    val validationReason: String = "",
    val postValidationAction: String = "",
    val source: DailyBriefSource = DailyBriefSource.RULE,
)

data class ExternalResearchSnapshot(
    val outsideAngle: String = "",
    val opportunityGap: String = "",
    val contrarianQuestion: String = "",
    val externalHypothesis: String = "",
    val queries: List<String> = emptyList(),
    val source: DailyBriefSource = DailyBriefSource.RULE,
)
