package com.mindflow.app.ui.screens.flow

import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.compactRecordPreviewText
import com.mindflow.app.ui.components.compactRecordTitleText

data class TodayDesignModel(
    val heroTitle: String,
    val heroSubtitle: String,
    val focus: TodayFocusModel,
    val reason: TodayReasonModel,
    val discoveryActionLabel: String,
    val discoveryCards: List<TodayDiscoveryCardModel>,
    val trackingActionLabel: String,
    val trackingRows: List<TodayTrackingRowModel>,
    val review: TodayReviewModel,
)

data class TodayFocusModel(
    val title: String,
    val summary: String,
    val progress: Float,
    val progressLabel: String,
    val nextStep: String,
    val focusNoteId: Long?,
    val noteId: Long?,
    val threadKey: String?,
    val hasTarget: Boolean,
)

data class TodayReasonModel(
    val title: String,
    val sourceLine: String,
    val frequencyLine: String,
    val valueLine: String,
    val actionLine: String,
    val detailLines: List<String>,
)

data class TodayDiscoveryCardModel(
    val title: String,
    val source: String,
    val confidence: String,
    val reason: String,
    val focusNoteId: Long?,
    val threadKey: String?,
    val destinationLabel: String,
)

data class TodayTrackingRowModel(
    val title: String,
    val subtitle: String,
    val progressLabel: String,
    val focusNoteId: Long?,
    val threadKey: String?,
    val destinationLabel: String,
)

data class TodayReviewModel(
    val title: String,
    val description: String,
    val savedSessionId: Long?,
)

data class TodayTaskDetailModel(
    val title: String,
    val threadKey: String,
    val createdLine: String,
    val statusLabel: String,
    val progress: Float,
    val progressLabel: String,
    val sourceLine: String,
    val nextSuggestion: String,
    val relatedRecordCount: Int,
    val relatedArticleCount: Int,
    val timeline: List<TodayTaskTimelineStep>,
    val materials: List<TodayTaskMaterialModel>,
    val primaryActionLabel: String,
    val secondaryActionLabel: String,
    val tertiaryActionLabel: String,
)

data class TodayTaskTimelineStep(
    val label: String,
    val detail: String,
    val completed: Boolean,
    val active: Boolean,
)

data class TodayTaskMaterialModel(
    val title: String,
    val description: String,
    val meta: String,
)

fun TodayUiState.toTodayDesignModel(
    latestSavedConversationSummary: SavedReviewChatSessionSummary?,
    surface: IncubationSurfaceState,
): TodayDesignModel {
    val candidateCount = todayDirectionCount().coerceAtLeast(0).coerceAtMost(3)
    val focus = buildTodayFocusModel(surface)
    val discoveryCards = buildTodayDiscoveryCards(surface)
    val trackingRows = buildTodayTrackingRows(surface)
    return TodayDesignModel(
        heroTitle = if (candidateCount > 0) {
            "今天，MindFlow 发现你有 $candidateCount 个值得推进的方向"
        } else {
            "今天，MindFlow 正在等待新的推进方向"
        },
        heroSubtitle = "基于你的记录、习惯与目标",
        focus = focus,
        reason = buildTodayReasonModel(focus, surface),
        discoveryActionLabel = "共 ${discoveryCards.size} 个建议",
        discoveryCards = discoveryCards,
        trackingActionLabel = "共 ${trackingRows.size} 个任务",
        trackingRows = trackingRows,
        review = TodayReviewModel(
            title = "今日回看",
            description = latestSavedConversationSummary
                ?.toTodayReviewDescription()
                ?: "打开回看历史，手动选择一个问题再继续",
            savedSessionId = latestSavedConversationSummary?.sessionId,
        ),
    )
}

private fun SavedReviewChatSessionSummary.toTodayReviewDescription(): String {
    val titleText = title.cleanTodayVisibleText()
    val excerptText = latestExcerpt.cleanTodayVisibleText()
    val combined = when {
        titleText.isBlank() -> excerptText
        excerptText.isBlank() || excerptText == titleText -> titleText
        else -> "$titleText：$excerptText"
    }
    return combined.asTodayDesignPreview(72)
}

fun TodayDesignModel.taskDetailFor(threadKey: String): TodayTaskDetailModel? {
    val trackingRow = trackingRows.firstOrNull { it.threadKey == threadKey }
    val discoveryCard = discoveryCards.firstOrNull { it.threadKey == threadKey }
    val focusMatches = focus.threadKey == threadKey
    if (trackingRow == null && discoveryCard == null && !focusMatches) return null

    val title = trackingRow?.title
        ?: discoveryCard?.title
        ?: focus.title
    val rawProgressLabel = trackingRow?.progressLabel
        ?: discoveryCard?.confidence
        ?: focus.progressLabel.substringAfter("·", focus.progressLabel).trim()
    val progressPercent = rawProgressLabel
        .filter(Char::isDigit)
        .toIntOrNull()
        ?.coerceIn(0, 100)
        ?: 65
    val nextSuggestion = when {
        focusMatches && focus.nextStep.isNotBlank() -> focus.nextStep
        trackingRow?.subtitle?.isNotBlank() == true -> trackingRow.subtitle
        discoveryCard?.reason?.isNotBlank() == true -> discoveryCard.reason
        else -> "先补一句最新进展，再明确今天这一小步。"
    }
    val sourceLine = discoveryCard?.source
        ?: reason.sourceLine.removePrefix("推荐来源：").ifBlank { "来自正在跟踪方向" }
    val materials = listOf(
        TodayTaskMaterialModel(
            title = "来源",
            description = sourceLine,
            meta = "自动发现",
        ),
        TodayTaskMaterialModel(
            title = "最近推进",
            description = trackingRow?.subtitle ?: focus.summary,
            meta = "今天",
        ),
        TodayTaskMaterialModel(
            title = "推荐依据",
            description = reason.detailLines.joinToString(" · "),
            meta = "AI 判断",
        ),
    )
    return TodayTaskDetailModel(
        title = title,
        threadKey = threadKey,
        createdLine = "创建时间：基于近期记录",
        statusLabel = if (progressPercent >= 100) "已完成" else "推进中",
        progress = progressPercent / 100f,
        progressLabel = "$progressPercent%",
        sourceLine = sourceLine,
        nextSuggestion = nextSuggestion,
        relatedRecordCount = 12,
        relatedArticleCount = 5,
        timeline = listOf(
            TodayTaskTimelineStep("已识别", "05-19", completed = true, active = false),
            TodayTaskTimelineStep("已激活", "05-20", completed = true, active = false),
            TodayTaskTimelineStep("推进中", "$progressPercent%", completed = false, active = progressPercent < 100),
            TodayTaskTimelineStep("已完成", "--", completed = progressPercent >= 100, active = false),
        ),
        materials = materials,
        primaryActionLabel = "开始推进",
        secondaryActionLabel = "询问回看",
        tertiaryActionLabel = "拆成任务",
    )
}

private fun TodayUiState.buildTodayFocusModel(
    surface: IncubationSurfaceState,
): TodayFocusModel {
    val direction = surface.threadDirection
    val judgement = localMaintainerSnapshot.currentJudgement
    val candidateTitle = mainlineCandidate?.title?.trim()
    val continueTitle = continueNote?.compactRecordTitleText()?.takeIf { it.isNotBlank() }
    val continueSummary = continueNote?.compactRecordPreviewText()?.cleanTodayVisibleText()?.takeIf { it.isNotBlank() }
    val title = candidateTitle?.takeIf { it.isNotBlank() && !it.isBroadTodayBucket() }
        ?: direction?.todayDisplayTitle()?.takeIf { it.isNotBlank() }
        ?: candidateTitle?.takeIf { it.isNotBlank() }
        ?: continueTitle
        ?: judgement.anchorLabel.takeIf { it.isNotBlank() }
        ?: "等待新的可推进方向"
    val summary = mainlineCandidate?.summary?.takeIf { it.isNotBlank() }
        ?: direction?.summary?.takeIf { it.isNotBlank() }
        ?: knowledgeCompression.mainline.takeIf { it.isNotBlank() }
        ?: judgement.line.takeIf { it.isNotBlank() }
        ?: continueSummary?.asTodayDesignPreview(88)
        ?: "继续记录，AI 会自动识别今天值得推进的方向。"
    val nextStep = nextActionText.takeIf { it.isNotBlank() }
        ?: mainlineCandidate?.nextStep?.takeIf { it.isNotBlank() }
        ?: direction?.nextStep?.takeIf { it.isNotBlank() }
        ?: localMaintainerSnapshot.currentJudgement.support.takeIf { it.isNotBlank() }
        ?: "先补一条真实记录，再决定是否加入今天。"
    val progress = todayProgressFraction()
    val hasTarget = mainlineCandidate != null ||
        direction != null ||
        continueNote != null ||
        judgement.hasContent
    return TodayFocusModel(
        title = title,
        summary = summary,
        progress = progress,
        progressLabel = if (hasTarget) "推进中 · ${(progress * 100).toInt()}%" else "待开始",
        nextStep = nextStep,
        focusNoteId = mainlineCandidate?.focusNoteId ?: direction?.focusNoteId,
        noteId = mainlineCandidate?.noteId ?: continueNote?.id ?: judgement.noteId,
        threadKey = mainlineCandidate?.threadKey ?: direction?.thread?.key ?: judgement.threadKey.takeIf { it.isNotBlank() },
        hasTarget = hasTarget,
    )
}

private fun TodayUiState.buildTodayReasonModel(
    focus: TodayFocusModel,
    surface: IncubationSurfaceState,
): TodayReasonModel {
    val direction = surface.threadDirection
    val source = when {
        knowledgeCompression.mainline.isNotBlank() -> "本地模型结合相关记录与方向状态"
        localMaintainerSnapshot.currentJudgement.hasContent -> "本地维护结果与近期记录"
        direction != null -> "本地模型结合相关记录与正在跟踪的方向"
        else -> "本地记录与今天新增内容"
    }
    val frequencyCount = listOf(
        todayCount,
        direction?.thread?.noteCount ?: 0,
        followedDirections.size,
    ).maxOrNull().orEmptyCount()
    val frequencyLine = "近期频率：近 7 天出现 $frequencyCount 次，近 30 天仍有上下文"
    val valueLine = "价值判断：${mainlineCandidate?.stageLabel?.takeIf { it.isNotBlank() } ?: "高价值"} · 进行中 · 待补充"
    val actionLine = "建议动作：${focus.nextStep}"
    return TodayReasonModel(
        title = "AI 为什么推荐这个方向?",
        sourceLine = "推荐来源：$source",
        frequencyLine = frequencyLine,
        valueLine = valueLine,
        actionLine = actionLine,
        detailLines = listOf(frequencyLine, valueLine, actionLine),
    )
}

private fun TodayUiState.buildTodayDiscoveryCards(
    surface: IncubationSurfaceState,
): List<TodayDiscoveryCardModel> {
    val confidence = listOf("92%", "84%", "78%")
    val sourceDirections = followedDirections
        .ifEmpty { listOfNotNull(surface.sparkDirection, surface.threadDirection) }
    val models = sourceDirections
        .distinctBy { it.thread.key }
        .take(3)
        .mapIndexed { index, direction ->
            TodayDiscoveryCardModel(
                title = direction.todayDisplayTitle(),
                source = direction.todaySourceLabel(),
                confidence = confidence.getOrElse(index) { "72%" },
                reason = direction.whyNow
                    .ifBlank { direction.stageReason }
                    .ifBlank { direction.summary }
                    .ifBlank { "和当前推进目标高度相关。" }
                    .asTodayDesignPreview(44),
                focusNoteId = direction.focusNoteId,
                threadKey = direction.thread.key,
                destinationLabel = "",
            )
        }
    if (models.size >= 3) return models
    val candidate = mainlineCandidate
    val supplemental = listOfNotNull(
        candidate?.let {
            TodayDiscoveryCardModel(
                title = it.title,
                source = it.anchorLabel.ifBlank { "来自主线候选" }.asTodayDesignPreview(28),
                confidence = confidence.getOrElse(models.size) { "72%" },
                reason = it.whyNow.ifBlank { it.summary }.ifBlank { "适合加入今天继续推进。" }.asTodayDesignPreview(44),
                focusNoteId = it.focusNoteId,
                threadKey = it.threadKey,
                destinationLabel = "",
            )
        },
        TodayDiscoveryCardModel(
            title = "补一条新的记录",
            source = "来自今天输入",
            confidence = confidence.getOrElse(models.size + 1) { "72%" },
            reason = "记录变多后，自动发现会更准确。",
            focusNoteId = null,
            threadKey = null,
            destinationLabel = "",
        ),
        TodayDiscoveryCardModel(
            title = "回看一条旧问题",
            source = "来自历史记忆",
            confidence = confidence.getOrElse(models.size + 2) { "72%" },
            reason = "把旧问题带回今天，常常能补上下一步。",
            focusNoteId = null,
            threadKey = null,
            destinationLabel = "",
        ),
    )
    return (models + supplemental).take(3)
}

private fun TodayUiState.buildTodayTrackingRows(
    surface: IncubationSurfaceState,
): List<TodayTrackingRowModel> {
    val tracked = followedDirections.ifEmpty { listOfNotNull(surface.threadDirection) }
    val rows = tracked
        .distinctBy { it.thread.key }
        .take(2)
        .map { direction ->
            TodayTrackingRowModel(
                title = direction.todayDisplayTitle(),
                subtitle = direction.lastProgressLine
                    .ifBlank { direction.nextCheckInLine }
                    .ifBlank { direction.nextStep }
                    .ifBlank { "上次推进：今天 09:20" }
                    .cleanTodayVisibleText()
                    .asTodayDesignPreview(42),
                progressLabel = when {
                    direction.wikiValidatedPoint.isNotBlank() -> "90%"
                    direction.stageReason.isNotBlank() || direction.lastProgressLine.isNotBlank() -> "70%"
                    else -> "65%"
                },
                focusNoteId = null,
                threadKey = direction.thread.key,
                destinationLabel = "详情",
            )
        }
    if (rows.isNotEmpty()) return rows
    return listOf(
        TodayTrackingRowModel(
            title = "等待第一条进行中方向",
            subtitle = "加入今天后，这里会显示推进进度。",
        progressLabel = "0%",
        focusNoteId = null,
        threadKey = null,
        destinationLabel = todayDestinationLabel(
            focusNoteId = null,
            noteId = null,
            threadKey = null,
        ),
    ),
)
}

private fun todayDestinationLabel(
    focusNoteId: Long?,
    noteId: Long?,
    threadKey: String?,
): String = when {
    focusNoteId != null || noteId != null -> "打开记录"
    !threadKey.isNullOrBlank() -> "打开线程"
    else -> "新建记录"
}

private fun TodayUiState.todayDirectionCount(): Int {
    val candidateCount = if (mainlineCandidate != null) 1 else 0
    val count = followedDirections.size + candidateCount
    return when {
        count > 0 -> count
        continueNote != null -> 1
        todayCount > 0 -> 1
        else -> 0
    }
}

private fun TodayUiState.todayProgressFraction(): Float = when {
    continueNote?.status == NoteStatus.DONE -> 1f
    mainlineCandidate != null -> 0.65f
    continueNote?.status == NoteStatus.IN_PROGRESS -> 0.65f
    continueNote != null -> 0.3f
    followedDirections.isNotEmpty() -> 0.65f
    todayCount > 0 -> 0.18f
    else -> 0f
}

private fun Int?.orEmptyCount(): Int = this?.takeIf { it > 0 } ?: 1

private fun FollowedDirectionSummary.todayDisplayTitle(): String {
    val threadTitle = thread.title.trim()
    val specificTitle = thread.summary
        .ifBlank { summary }
        .substringBefore(" · ")
        .asTodayDesignPreview(18)
    return when {
        threadTitle.isNotBlank() && !threadTitle.isBroadTodayBucket() -> threadTitle
        specificTitle.isNotBlank() -> specificTitle
        threadTitle.isNotBlank() -> threadTitle
        else -> "值得推进的方向"
    }
}

private fun FollowedDirectionSummary.todaySourceLabel(): String {
    val threadTitle = thread.title.trim()
    val sourceSummary = thread.summary.trim()
    return when {
        threadTitle.isBroadTodayBucket() -> "来自 $threadTitle"
        sourceSummary.isNotBlank() && !sourceSummary.looksLikeTodayCaptureMetadata() ->
            sourceSummary.cleanTodayVisibleText().asTodayDesignPreview(28)
        else -> "来自相关记录"
    }
}

private fun String.isBroadTodayBucket(): Boolean =
    this in setOf("工作", "生活", "健康", "学习", "想法", "任务", "文章", "语音", "图片", "未分类")

private fun String.asTodayDesignPreview(limit: Int): String {
    val normalized = replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (normalized.length <= limit) {
        normalized
    } else {
        normalized.take(limit).trimEnd() + "…"
    }
}

private fun String.cleanTodayVisibleText(): String {
    val normalized = replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    Regex(""""summary"\s*:\s*"([^"]+)"""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    if (!normalized.startsWith("{")) return normalized
    return normalized
        .removePrefix("{")
        .removeSuffix("}")
        .replace(Regex(""""[A-Za-z_]+":"""), "")
        .replace("\"", "")
        .replace("[", "")
        .replace("]", "")
        .trim()
}

private fun String.looksLikeTodayCaptureMetadata(): Boolean {
    val lower = lowercase()
    return "/data/" in lower ||
        "/storage/" in lower ||
        "content://" in lower ||
        "file://" in lower ||
        "com.mindflow.app/files" in lower ||
        ".m4a" in lower ||
        ".wav" in lower ||
        ".aac" in lower ||
        ".jpg" in lower ||
        ".jpeg" in lower ||
        ".png" in lower ||
        startsWith("原始录音：") ||
        startsWith("原始录音:") ||
        startsWith("图片：") ||
        startsWith("图片:")
}
