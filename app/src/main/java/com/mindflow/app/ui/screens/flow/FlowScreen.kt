package com.mindflow.app.ui.screens.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionState
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.review.WeeklyReviewItem
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightChip
import com.mindflow.app.ui.components.InsightLine
import com.mindflow.app.ui.components.InsightTone
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.PanelShape
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.noteStatusAccent
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.FlowFocus
import com.mindflow.app.ui.navigation.KnowledgeMaintenanceSeedContext
import com.mindflow.app.ui.navigation.buildKnowledgeMaintenanceCaptureSeed
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter
import kotlinx.coroutines.delay

@Composable
fun FlowRoute(
    viewModel: FlowViewModel,
    reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository,
    initialFocus: FlowFocus? = null,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenReviewChat: (String) -> Unit,
    onOpenLatestSavedReviewChat: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestSavedConversationSummary by reviewChatSavedConversationRepository
        .observeLatestSavedSessionSummary()
        .collectAsStateWithLifecycle(initialValue = null)
    FlowScreen(
        uiState = uiState,
        latestSavedConversationSummary = latestSavedConversationSummary,
        focus = initialFocus,
        onRefreshMainline = viewModel::refreshMainline,
        onRefreshLocalKnowledgeBrain = viewModel::refreshLocalKnowledgeBrain,
        onMarkSettledFeedback = viewModel::markSettledFeedback,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
        onCreateCapture = onCreateCapture,
        onOpenReviewChat = onOpenReviewChat,
        onOpenLatestSavedReviewChat = onOpenLatestSavedReviewChat,
        onOpenReviewChatHistory = onOpenReviewChatHistory,
    )
}

private enum class FlowPage {
    TODAY,
    REVIEW,
}

private fun FlowFocus?.toPage(): FlowPage = when (this) {
    FlowFocus.REVIEW,
    FlowFocus.RECONNECT -> FlowPage.REVIEW
    FlowFocus.MAINLINE,
    FlowFocus.DIRECTION -> FlowPage.TODAY
    FlowFocus.TODAY,
    null -> FlowPage.TODAY
}

@Composable
private fun FlowScreen(
    uiState: FlowUiState,
    latestSavedConversationSummary: SavedReviewChatSessionSummary?,
    focus: FlowFocus?,
    onRefreshMainline: () -> Unit,
    onRefreshLocalKnowledgeBrain: () -> Unit,
    onMarkSettledFeedback: (Boolean) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenReviewChat: (String) -> Unit,
    onOpenLatestSavedReviewChat: (Long) -> Unit,
    onOpenReviewChatHistory: () -> Unit,
) {
    val maintainerSnapshot = uiState.localMaintainerSnapshot
    val surface = remember(uiState) { uiState.toIncubationSurfaceState() }
    val page = remember(focus) { focus.toPage() }
    val screenTitle = remember(page) {
        when (page) {
            FlowPage.TODAY -> "今天"
            FlowPage.REVIEW -> "回看"
        }
    }
    val subtitle = remember(page) {
        when (page) {
            FlowPage.TODAY -> "只看今天该接住的事。"
            FlowPage.REVIEW -> "从旧积累里翻出真正有用的。"
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                }

                when (page) {
                    FlowPage.TODAY -> {
                        item {
                            TodayOverviewCard(
                                todayCount = uiState.todayCount,
                                continueNote = uiState.continueNote,
                                candidate = uiState.mainlineCandidate,
                                nextActionText = uiState.nextActionText,
                                onCreateCapture = onCreateCapture,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                            )
                        }
                        item {
                            RecentAbsorptionCard(
                                snapshot = maintainerSnapshot,
                                todayCount = uiState.todayCount,
                                direction = surface.sparkDirection,
                                provenance = surface.sparkProvenance,
                                highlighted = true,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                                onCreateCapture = onCreateCapture,
                            )
                        }
                        item {
                            MainlineFocusCard(
                                note = uiState.continueNote,
                                candidate = uiState.mainlineCandidate,
                                direction = surface.threadDirection,
                                provenance = surface.threadProvenance,
                                snapshot = maintainerSnapshot,
                                nextActionText = uiState.nextActionText,
                                compression = uiState.knowledgeCompression,
                                reconnectBridge = uiState.staleBridge,
                                reconnectStep = uiState.staleNextStep,
                                compact = true,
                                highlighted = false,
                                onRefresh = onRefreshMainline,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                                onCreateCapture = onCreateCapture,
                            )
                        }
                    }
                    FlowPage.REVIEW -> {
                        item {
                            ReviewChatEntryCard(
                                latestSavedSummary = latestSavedConversationSummary,
                                onOpenChat = { onOpenReviewChat("") },
                                onOpenHistory = onOpenReviewChatHistory,
                                onOpenSaved = onOpenLatestSavedReviewChat,
                            )
                        }
                        item {
                            SettledKnowledgeCard(
                                direction = surface.assetDirection,
                                provenance = surface.assetProvenance,
                                compression = uiState.knowledgeCompression,
                                feedback = uiState.settledFeedback,
                                highlighted = true,
                                onFeedback = onMarkSettledFeedback,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                            )
                        }
                        item {
                            FeedGapCard(
                                snapshot = maintainerSnapshot,
                                direction = surface.gapDirection,
                                directions = uiState.followedDirections,
                                primaryThreadKey = surface.assetDirection?.thread?.key,
                                highlighted = true,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                                onCreateCapture = onCreateCapture,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                GhostActionButton(
                                    text = "刷新本地知识层",
                                    onClick = onRefreshLocalKnowledgeBrain,
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun TodayOverviewCard(
    todayCount: Int,
    continueNote: NoteEntity?,
    candidate: MainlineBetCandidate?,
    nextActionText: String,
    onCreateCapture: (CaptureSeed) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val primaryLine = when {
        todayCount > 0 -> "今天已经接住 $todayCount 条，下一步是挑一条推进。"
        continueNote != null || candidate != null -> "今天还没有新记录，但有一条线可以继续。"
        else -> "今天还没有新记录，先接住一个想法。"
    }
    val recommendedAction = nextActionText
        .takeIf { it.isNotBlank() }
        ?: candidate?.nextStep?.takeIf { it.isNotBlank() }
        ?: continueNote?.content?.take(96)?.takeIf { it.isNotBlank() }
        ?: "先写下一条真实想法，后面再整理。"
    val hasContinueTarget = continueNote != null || candidate != null

    PanelCard {
        SectionHeader(
            title = "今日节奏",
            headline = primaryLine,
        )
        InsightBlock(tone = InsightTone.Primary) {
            InsightLine(
                label = "推荐动作",
                text = recommendedAction,
                maxLines = 3,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = "新建记录",
                onClick = { onCreateCapture(CaptureSeed()) },
                modifier = Modifier.weight(1f),
            )
            if (hasContinueTarget) {
                GhostActionButton(
                    text = "继续主线",
                    onClick = {
                        candidate?.focusNoteId?.let(onOpenNote)
                            ?: candidate?.noteId?.let(onOpenNote)
                            ?: candidate?.threadKey?.let(onOpenThread)
                            ?: continueNote?.id?.let(onOpenNote)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentAbsorptionCard(
    snapshot: com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot,
    todayCount: Int,
    direction: FollowedDirectionSummary?,
    provenance: FlowCardProvenance,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val card = snapshot.recentAbsorption
    val directionCount = snapshot.activeDirectionCount
    val headline = when {
        card.hasContent && todayCount > 0 -> "今天新进了 $todayCount 条"
        card.hasContent -> "今天先从这条开始"
        directionCount > 0 -> "先补一条新记录"
        else -> "今天还没有内容"
    }
    Surface(
        color = WhiteGlass.copy(alpha = 0.9f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) AccentBlue.copy(alpha = 0.34f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "先做这一件",
                headline = headline,
            )
            if (card.hasContent) {
                card.anchorLabel.takeIf { it.isNotBlank() }?.let { anchor ->
                    AnchorLine(
                        text = "来自：$anchor",
                        onClick = {
                            card.noteId?.let(onOpenNote)
                                ?: card.threadKey.takeIf { it.isNotBlank() }?.let(onOpenThread)
                                ?: direction?.thread?.key?.let(onOpenThread)
                        },
                    )
                }
                Text(
                    text = card.line,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                card.support.takeIf { it.isNotBlank() }?.let {
                    InsightBlock(tone = InsightTone.Neutral) {
                        InsightLine(label = "线索", text = it, maxLines = 2)
                    }
                }
            } else {
                AnchorLine(
                    text = "先记下一颗火花",
                    onClick = { onCreateCapture(CaptureSeed()) },
                )
                Text(
                    text = if (directionCount > 0) "旧积累还在，但今天要先记下一条新的。" else "先记下一条，今天这页才会开始长出来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            }
            ActionButton(
                text = if (card.hasContent) "去做" else "新建记录",
                onClick = {
                    if (card.hasContent) {
                        card.noteId?.let(onOpenNote)
                            ?: card.threadKey.takeIf { it.isNotBlank() }?.let(onOpenThread)
                            ?: direction?.thread?.key?.let(onOpenThread)
                            ?: onCreateCapture(CaptureSeed())
                    } else {
                        onCreateCapture(CaptureSeed())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MainlineFocusCard(
    note: NoteEntity?,
    candidate: MainlineBetCandidate?,
    snapshot: com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot,
    direction: FollowedDirectionSummary?,
    provenance: FlowCardProvenance,
    nextActionText: String,
    compression: FlowKnowledgeCompressionState,
    reconnectBridge: String,
    reconnectStep: String,
    compact: Boolean,
    highlighted: Boolean,
    onRefresh: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val accent = noteStatusAccent(note?.status ?: NoteStatus.IN_PROGRESS)
    val anchoredJudgement = snapshot.currentJudgement.takeIf {
        it.threadKey.isNotBlank() || it.noteId != null
    }
    val title = candidate?.title
        ?: anchoredJudgement?.anchorLabel?.takeIf { it.isNotBlank() }
        ?: note?.topic?.takeIf { it.isNotBlank() }
        ?: "一条正在成形的暗线"
    val mainLine = candidate?.summary?.takeIf { it.isNotBlank() }
        ?: anchoredJudgement?.line?.takeIf { it.isNotBlank() }
        ?: nextActionText.takeIf { it.isNotBlank() }
        ?: reconnectStep.takeIf { it.isNotBlank() }
        ?: "先让几条记录开始互相咬合，这里才会慢慢长出真正值得继续推进的一条线。"
    val whyNowLine = candidate?.whyNow?.takeIf { it.isNotBlank() }
        ?: anchoredJudgement?.support?.takeIf { it.isNotBlank() }
        ?: reconnectBridge.takeIf { it.isNotBlank() }
    val resolvedMainLine = compression.mainline.ifBlank { mainLine }
    val resolvedWhyNow = compression.whyNow.ifBlank { whyNowLine.orEmpty() }
    val hasRecurringContext = candidate != null || note != null || anchoredJudgement?.hasContent == true
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.45f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = if (compact) "接着推进" else "当前主线",
                headline = if (compact) {
                    null
                } else if (hasRecurringContext) {
                    "把前面的积累用起来"
                } else {
                    "先让几条记录开始互相呼应，这里才会开始长出主线"
                },
            )
            if (!compact) {
                InsightChip(text = provenance.label, tone = InsightTone.Neutral)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidate?.takeIf { it.stageLabel.isNotBlank() || it.horizonLabel.isNotBlank() }?.let {
                        if (it.stageLabel.isNotBlank()) {
                            InsightChip(text = it.stageLabel, tone = InsightTone.Primary)
                        }
                        if (it.horizonLabel.isNotBlank()) {
                            InsightChip(text = it.horizonLabel, tone = InsightTone.Neutral)
                        }
                    } ?: note?.let {
                        InsightChip(text = it.status.label, tone = InsightTone.Primary)
                        InsightChip(text = it.horizon.label, tone = InsightTone.Neutral)
                    }
                }
            }
            title.takeIf { it.isNotBlank() && hasRecurringContext }?.let {
                AnchorLine(
                    text = if (compact) "接着：$it" else "这条线主要来自：$it",
                    onClick = {
                        candidate?.focusNoteId?.let(onOpenNote)
                            ?: candidate?.noteId?.let(onOpenNote)
                            ?: candidate?.threadKey?.let(onOpenThread)
                            ?: anchoredJudgement?.noteId?.let(onOpenNote)
                            ?: anchoredJudgement?.threadKey?.takeIf { it.isNotBlank() }?.let(onOpenThread)
                            ?: note?.id?.let(onOpenNote)
                    },
                )
            }
            AnimatedContent(
                targetState = resolvedMainLine,
                label = "flowMainlineText",
            ) { text ->
                Text(
                    text = text,
                    style = if (compact) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = TextMain,
                    maxLines = if (compact) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (resolvedWhyNow.isNotBlank()) {
                InsightBlock(
                    tone = if (compact) InsightTone.Neutral else InsightTone.Primary,
                ) {
                    AnimatedContent(
                        targetState = resolvedWhyNow,
                        label = "flowMainlineWhyNow",
                    ) { text ->
                        if (text.isNotBlank()) {
                            InsightLine(label = if (compact) "原因" else "为什么现在", text = text, maxLines = 2)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = if (compact) "继续" else "继续养这条线",
                    onClick = {
                        candidate?.focusNoteId?.let(onOpenNote)
                            ?: candidate?.noteId?.let(onOpenNote)
                            ?: candidate?.threadKey?.let(onOpenThread)
                            ?: anchoredJudgement?.noteId?.let(onOpenNote)
                            ?: anchoredJudgement?.threadKey?.takeIf { it.isNotBlank() }?.let(onOpenThread)
                            ?: note?.id?.let(onOpenNote)
                            ?: direction?.thread?.key?.let(onOpenThread)
                            ?: onCreateCapture(CaptureSeed())
                    },
                    modifier = Modifier.weight(1f),
                )
                GhostActionButton(
                    text = if (compact) "换一条" else "换一条线",
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettledKnowledgeCard(
    direction: FollowedDirectionSummary?,
    provenance: FlowCardProvenance,
    compression: FlowKnowledgeCompressionState,
    feedback: FlowCardFeedback?,
    highlighted: Boolean,
    onFeedback: (Boolean) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val settledLine = direction?.wikiValidatedPoint
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiVerifiedPoint?.takeIf { it.isNotBlank() }
        ?: direction?.wikiConclusionLine?.takeIf { it.isNotBlank() }
        ?: direction?.assetSummary?.takeIf { it.isNotBlank() }
    val trustChip = when {
        direction?.wikiValidatedPoint?.isNotBlank() == true -> "已验证"
        direction?.wikiVerifiedPoint?.isNotBlank() == true -> "已查证"
        direction?.wikiConclusionLine?.isNotBlank() == true -> "当前结论"
        else -> "正在沉淀"
    }
    val supportLine = direction?.wikiGroundingLine
        ?.takeIf { it.isNotBlank() }
        ?: direction?.wikiTrustLine?.takeIf { it.isNotBlank() }
        ?: direction?.wikiKnowledgeObjectLine?.takeIf { it.isNotBlank() }
    val resolvedSettledLine = compression.settledLine.ifBlank { settledLine.orEmpty() }
    val resolvedSupportLine = compression.settledSupport.ifBlank { supportLine.orEmpty() }
    Surface(
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) AccentBlue.copy(alpha = 0.38f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "值得回看的",
                headline = if (resolvedSettledLine.isNotBlank()) "先回看这一条" else "先等一条结果站稳",
            )
            if (direction == null || resolvedSettledLine.isBlank()) {
                Text(
                    text = "当一条线反复被验证、被用上，这里才会开始留下真正值得回看的东西。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            } else {
                AnchorLine(
                    text = "回到：${direction.thread.title}",
                    onClick = { onOpenThread(direction.thread.key) },
                )
                AnimatedContent(
                    targetState = resolvedSettledLine,
                    label = "flowSettledLine",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextMain,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                resolvedSupportLine.takeIf { it.isNotBlank() }?.let {
                    InsightBlock(
                        tone = InsightTone.Neutral,
                    ) {
                        AnimatedContent(
                            targetState = it,
                            label = "flowSettledSupport",
                        ) { text ->
                            InsightLine(
                                label = when (trustChip) {
                                    "已验证" -> "为什么它可靠"
                                    "已查证" -> "为什么它站得住"
                                    "当前结论" -> "现在先记住什么"
                                    else -> "为什么值得留"
                                },
                                text = text,
                                maxLines = 2,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "回看这条",
                        onClick = {
                            direction.assetNoteId?.let(onOpenNote) ?: onOpenThread(direction.thread.key)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedGapCard(
    snapshot: com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot,
    direction: FollowedDirectionSummary?,
    directions: List<FollowedDirectionSummary>,
    primaryThreadKey: String?,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    val card = snapshot.openQuestion
    val line = card.line.ifBlank {
        direction?.wikiOpenQuestion
            ?.takeIf { it.isNotBlank() }
            ?: direction?.wikiMaintenanceLine?.takeIf { it.isNotBlank() }
            ?: direction?.blocker?.takeIf { it.isNotBlank() }
            ?: direction?.validationStep?.takeIf { it.isNotBlank() }
            ?: ""
    }
    val support = card.support.ifBlank {
        direction?.wikiMaintenanceFocusLine
            ?.takeIf { it.isNotBlank() }
            ?: direction?.wikiMaintenanceTargetLine?.takeIf { it.isNotBlank() }
            ?: direction?.validationReason?.takeIf { it.isNotBlank() }
            ?: direction?.postValidationAction?.takeIf { it.isNotBlank() }
            ?: ""
    }
    val reviewFollowups = remember(directions, direction, primaryThreadKey) {
        directions
            .filterNot { candidate ->
                candidate.thread.key == primaryThreadKey || candidate.thread.key == direction?.thread?.key
            }
            .sortedByDescending { candidate ->
                when {
                    candidate.wikiValidatedPoint.isNotBlank() -> 6
                    candidate.wikiVerifiedPoint.isNotBlank() -> 5
                    candidate.wikiConclusionLine.isNotBlank() -> 4
                    candidate.assetSummary.isNotBlank() -> 3
                    candidate.wikiOpenQuestion.isNotBlank() -> 2
                    candidate.blocker.isNotBlank() || candidate.validationStep.isNotBlank() -> 1
                    else -> 0
                }
            }
            .take(3)
    }
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = PanelShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) AccentBlue.copy(alpha = 0.34f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "还值得翻的",
                headline = if (line.isNotBlank()) "接着翻这一条" else "先等旧积累再厚一点",
            )
            if (line.isBlank()) {
                Text(
                    text = "当旧积累之间开始互相支撑或互相冲突时，这里才会出现真正值得翻出来再看的点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            } else {
                val anchorText = card.anchorLabel.takeIf { it.isNotBlank() } ?: direction?.thread?.title
                anchorText?.let {
                    AnchorLine(
                        text = "继续看：$it",
                        onClick = {
                            card.noteId?.let(onOpenNote)
                                ?: card.threadKey.takeIf { key -> key.isNotBlank() }?.let(onOpenThread)
                                ?: direction?.thread?.key?.let(onOpenThread)
                        },
                    )
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                support.takeIf { it.isNotBlank() }?.let {
                    InsightBlock(tone = InsightTone.Neutral) {
                        InsightLine(label = "再看哪里", text = it, maxLines = 2)
                    }
                }
                ActionButton(
                    text = "回去看看",
                    onClick = {
                        card.noteId?.let(onOpenNote)
                            ?: card.threadKey.takeIf { key -> key.isNotBlank() }?.let(onOpenThread)
                            ?: direction?.thread?.key?.let(onOpenThread)
                            ?: when {
                                direction?.wikiMaintenanceLine?.isNotBlank() == true -> onCreateCapture(direction.toMaintenanceCaptureSeed())
                                direction?.validationStep?.isNotBlank() == true -> onCreateCapture(direction.toResearchCaptureSeed())
                                else -> onCreateCapture(
                                    buildGapCaptureSeed(
                                        line = line,
                                        support = support,
                                        direction = direction,
                                    ),
                                )
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reviewFollowups.isNotEmpty()) {
                    ReviewFollowupsBlock(
                        directions = reviewFollowups,
                        onOpenThread = onOpenThread,
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewFollowupsBlock(
    directions: List<FollowedDirectionSummary>,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "另外值得翻的",
            style = MaterialTheme.typography.labelLarge,
            color = TextSoft,
        )
        directions.forEach { direction ->
            val summaryLine = direction.reviewReasonLine()
                .takeIf { it.isNotBlank() }
                ?: direction.reviewPeekLine()
            Surface(
                color = WhiteGlass.copy(alpha = 0.78f),
                shape = CardShape,
                border = BorderStroke(1.dp, BorderSoft),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        direction.assetNoteId?.let(onOpenNote)
                            ?: direction.focusNoteId?.let(onOpenNote)
                            ?: onOpenThread(direction.thread.key)
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = direction.thread.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    summaryLine.takeIf { it.isNotBlank() }?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorLine(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AccentBlue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun KnowledgeTrailCard(
    weeklyItems: List<WeeklyReviewItem>,
    weeklySource: DailyBriefSource,
    weeklyStatsLine: String,
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    onOpenThread: (String) -> Unit,
) {
    val weeklyLine = weeklyItems.firstOrNull()?.text.orEmpty()
    val connectionLine = suggestions.firstOrNull().orEmpty()
    if (
        weeklyLine.isBlank() &&
        connectionLine.isBlank() &&
        followedDirections.isEmpty() &&
        threads.isEmpty()
    ) {
        return
    }
    PanelCard {
        SectionHeader(
            title = "知识脉络",
            headline = "需要时再看更长的线，不打断今天主线",
        )
        if (weeklyLine.isNotBlank() || connectionLine.isNotBlank()) {
            InsightBlock(
                sourceLabel = when {
                    weeklySource == DailyBriefSource.AI || fusionSource == DailyBriefSource.AI -> "AI 判断"
                    else -> "最近脉络"
                },
                tone = InsightTone.Neutral,
            ) {
                weeklyLine.takeIf { it.isNotBlank() }?.let {
                    InsightLine(label = "本周判断", text = it, maxLines = 2)
                }
                connectionLine.takeIf { it.isNotBlank() }?.let {
                    InsightLine(label = "值得串起来", text = it, maxLines = 2)
                }
            }
        }
        weeklyStatsLine.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
        followedDirections.take(2).forEach { summary ->
            ThreadRow(
                thread = summary.thread,
                showFocus = true,
                onOpenThread = onOpenThread,
            )
        }
        if (followedDirections.isEmpty()) {
            threads.take(2).forEach { thread ->
                ThreadRow(
                    thread = thread,
                    showFocus = true,
                    onOpenThread = onOpenThread,
                )
            }
        }
        val remainingCount = (followedDirections.size + threads.size - 2).coerceAtLeast(0)
        if (remainingCount > 0) {
            Text(
                text = "还有 $remainingCount 条方向或线索可以继续看",
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun DirectionCard(
    weeklyItems: List<WeeklyReviewItem>,
    weeklySource: DailyBriefSource,
    weeklyStatsLine: String,
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    fusionSource: DailyBriefSource,
    highlightReview: Boolean,
    highlightConnection: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    PanelCard {
        SectionHeader(
            title = "知识沉淀",
            headline = when {
                followedDirections.isNotEmpty() -> "先看最近长出来的判断，再决定往哪推"
                suggestions.isNotEmpty() -> "先看最值得继续追的一个点"
                threads.isNotEmpty() -> "先把分散的线索收成一条可经营的主线"
                else -> null
            },
        )
        KnowledgePayoffCard(
            followedDirections = followedDirections,
            suggestions = suggestions,
            source = fusionSource,
        )
        ConnectionCard(
            followedDirections = followedDirections,
            threads = threads,
            suggestions = suggestions,
            source = fusionSource,
            highlighted = highlightConnection,
            onOpenThread = onOpenThread,
            onOpenNote = onOpenNote,
            onCreateCapture = onCreateCapture,
        )
        WeeklyReviewCard(
            items = weeklyItems,
            source = weeklySource,
            statsLine = weeklyStatsLine,
            highlighted = highlightReview,
        )
    }
}

@Composable
private fun KnowledgePayoffCard(
    followedDirections: List<FollowedDirectionSummary>,
    suggestions: List<String>,
    source: DailyBriefSource,
) {
    val recentLine = followedDirections.firstNonBlankValue {
        it.wikiConclusionLine.ifBlank { it.assetSummary }
    }
    val trustedLine = followedDirections.firstNonBlankValue {
        it.wikiValidatedPoint.ifBlank { it.wikiVerifiedPoint }
    }
    val gapLine = followedDirections.firstNonBlankValue {
        it.wikiOpenQuestion
            .ifBlank { it.wikiMaintenanceLine }
            .ifBlank { it.blocker }
    }
    val ideaLine = suggestions.firstOrNull().orEmpty()

    if (
        recentLine.isBlank() &&
        trustedLine.isBlank() &&
        gapLine.isBlank() &&
        ideaLine.isBlank()
    ) {
        return
    }

    InsightBlock(
        sourceLabel = if (source == DailyBriefSource.AI) "AI 沉淀" else "最近沉淀",
        tone = InsightTone.Primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        recentLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "已经沉淀", text = it, emphasize = true, maxLines = 2)
        }
        trustedLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "最可靠的一点", text = it, maxLines = 2)
        }
        gapLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "还待补", text = it, maxLines = 2)
        }
        ideaLine.takeIf { it.isNotBlank() }?.let {
            InsightLine(label = "值得延展", text = it, maxLines = 2)
        }
    }
}

@Composable
private fun ConnectionCard(
    followedDirections: List<FollowedDirectionSummary>,
    threads: List<ThemeThread>,
    suggestions: List<String>,
    source: DailyBriefSource,
    highlighted: Boolean,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (followedDirections.isNotEmpty()) {
                Text(
                    text = "正在经营的方向",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                followedDirections.forEach { summary ->
                    FollowedDirectionRow(
                        summary = summary,
                        onOpenThread = onOpenThread,
                        onOpenNote = onOpenNote,
                        onCreateCapture = onCreateCapture,
                    )
                }
            }
            if (threads.isNotEmpty() || suggestions.isNotEmpty()) {
                InsightBlock(
                    sourceLabel = if (source == DailyBriefSource.AI) "AI 串联" else "串联线索",
                    tone = InsightTone.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    threads.firstOrNull()?.let { thread ->
                        InsightLine(
                            label = "值得串起来",
                            text = "${thread.title} · ${thread.summary}",
                            maxLines = 2,
                        )
                    }
                    suggestions.firstOrNull()?.let { suggestion ->
                        InsightLine(
                            label = "值得延展",
                            text = suggestion,
                            maxLines = 2,
                        )
                    }
                }
                if (threads.size > 1) {
                    Text(
                        text = "还有 ${threads.size - 1} 条线索可以继续串起来",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                } else if (suggestions.size > 1) {
                    Text(
                        text = "还有 ${suggestions.size - 1} 条延展想法",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSoft,
                    )
                }
            }
            if (followedDirections.isEmpty() && threads.isEmpty() && suggestions.isEmpty()) {
                Text(
                    text = "记录再多一点，这里就会帮你把反复出现的方向串起来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
        }
    }
}

@Composable
private fun FollowedDirectionRow(
    summary: FollowedDirectionSummary,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onCreateCapture: (CaptureSeed) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable { onOpenThread(summary.thread.key) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val trustChip = when {
                summary.wikiValidatedPoint.isNotBlank() -> "已验证"
                summary.wikiVerifiedPoint.isNotBlank() -> "已查证"
                summary.wikiHypothesisPoint.isNotBlank() -> "待验证"
                else -> "方向中"
            }
            val recentKnowledge = summary.wikiConclusionLine
                .ifBlank { summary.assetSummary }
                .ifBlank { summary.summary }
            val trustedResult = summary.wikiValidatedPoint
                .ifBlank { summary.wikiVerifiedPoint }
                .ifBlank { summary.wikiTrustLine }
            val gapLine = summary.wikiOpenQuestion
                .ifBlank { summary.wikiMaintenanceLine }
                .ifBlank { summary.blocker }
            val nextLine = summary.validationStep
                .ifBlank { summary.nextStep }
                .ifBlank { summary.wikiNextShiftLine }
            Text(
                text = summary.thread.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InsightChip(text = summary.stage.label, tone = InsightTone.Primary)
                InsightChip(text = trustChip, tone = InsightTone.Neutral)
                InsightChip(text = summary.dominantHorizon.label, tone = InsightTone.Neutral)
            }
            summary.rhythmLine.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.summary.isNotBlank() || summary.nextStep.isNotBlank() || summary.validationStep.isNotBlank()) {
                InsightBlock(
                    sourceLabel = if (summary.source == DailyBriefSource.AI) "AI 洞察" else "当前判断",
                    tone = InsightTone.Primary,
                ) {
                    recentKnowledge
                        .takeIf { it.isNotBlank() }
                        ?.let { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMain,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    summary.whyNow
                        .takeIf { it.isNotBlank() }
                        ?.let { reason ->
                            InsightLine(label = "为什么现在", text = reason, maxLines = 2)
                        }
                    nextLine
                        .takeIf { it.isNotBlank() }
                        ?.let { action ->
                            InsightLine(label = "下一步", text = action, emphasize = true, maxLines = 2)
                        }
                }
            }
            if (trustedResult.isNotBlank() || gapLine.isNotBlank()) {
                InsightBlock(
                    sourceLabel = "知识层",
                    tone = InsightTone.Neutral,
                ) {
                    trustedResult
                        .takeIf { it.isNotBlank() }
                        ?.let { trust ->
                            InsightLine(label = trustChip, text = trust, maxLines = 2)
                        }
                    gapLine
                        .takeIf { it.isNotBlank() }
                        ?.let { gap ->
                            InsightLine(label = "当前缺口", text = gap, maxLines = 2)
                        }
                    summary.whyNow
                        .takeIf { it.isNotBlank() }
                        ?.let { reason ->
                            InsightLine(label = "为什么现在", text = reason, maxLines = 2)
                        }
                    summary.lastProgressLine
                        .takeIf { it.isNotBlank() }
                        ?.let { progress ->
                            InsightLine(label = "最近推进", text = progress, maxLines = 2)
                        }
                }
            }
            if (
                summary.outsideAngle.isNotBlank() ||
                summary.opportunityGap.isNotBlank() ||
                summary.contrarianQuestion.isNotBlank() ||
                summary.externalHypothesis.isNotBlank()
            ) {
                InsightBlock(
                    sourceLabel = if (summary.source == DailyBriefSource.AI) "AI 外部线索" else "外部线索",
                    tone = InsightTone.Neutral,
                ) {
                    summary.outsideAngle.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    (summary.externalHypothesis.takeIf { it.isNotBlank() }
                        ?: summary.contrarianQuestion.takeIf { it.isNotBlank() })
                        ?.let {
                            InsightLine(label = "外部假设", text = it, maxLines = 2)
                        }
                    summary.opportunityGap.takeIf { it.isNotBlank() }?.let {
                        InsightLine(label = "机会缺口", text = it, maxLines = 2)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "继续推进",
                    onClick = {
                        summary.focusNoteId?.let(onOpenNote) ?: onOpenThread(summary.thread.key)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (summary.validationStep.isNotBlank()) {
                    GhostActionButton(
                        text = "记验证",
                        onClick = { onCreateCapture(summary.toResearchCaptureSeed()) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (summary.wikiMaintenanceLine.isNotBlank()) {
                    GhostActionButton(
                        text = "补材料",
                        onClick = { onCreateCapture(summary.toMaintenanceCaptureSeed()) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (summary.assetNoteId != null) {
                    GhostActionButton(
                        text = "看沉淀",
                        onClick = { onOpenNote(summary.assetNoteId) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GhostActionButton(
                        text = "看方向",
                        onClick = { onOpenThread(summary.thread.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun FollowedDirectionSummary.toResearchCaptureSeed(): CaptureSeed {
    val cleanTitle = thread.title.removePrefix("#").trim()
    val initialFolderKey = thread.key
        .takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.ifBlank { null }
    val initialTags = thread.key
        .takeIf { it.startsWith("tag:") }
        ?.removePrefix("tag:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()
    return CaptureSeed(
        initialTopic = "$cleanTitle · 验证动作",
        initialContent = buildString {
            appendLine("围绕「$cleanTitle」补一条验证记录：")
            appendLine("- 当前阶段：${stage.label} · ${dominantHorizon.label}")
            appendLine("- 为什么现在接：$whyNow")
            lastProgressLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 最近推进：$it")
            }
            nextStep.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前最小动作：$it")
            }
            nextCheckInLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 下次检查：$it")
            }
            outsideAngle.takeIf { it.isNotBlank() }?.let {
                appendLine("- AI 外部线索：$it")
            }
            opportunityGap.takeIf { it.isNotBlank() }?.let {
                appendLine("- 机会缺口：$it")
            }
            contrarianQuestion.takeIf { it.isNotBlank() }?.let {
                appendLine("- 反问：$it")
            }
            appendLine("- 先验证：$validationStep")
            validationReason.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么现在做：$it")
            }
            postValidationAction.takeIf { it.isNotBlank() }?.let {
                appendLine("- 如果成立，下一步：$it")
            }
            appendLine("- 我准备怎么验证：")
            appendLine("- 看什么结果算成立：")
        },
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
        initialKnowledgeTrust = KnowledgeTrust.HYPOTHESIS,
    )
}

private fun FollowedDirectionSummary.toMaintenanceCaptureSeed(): CaptureSeed =
    buildKnowledgeMaintenanceCaptureSeed(
        KnowledgeMaintenanceSeedContext(
            title = thread.title,
            threadKey = thread.key,
            stageLabel = stage.label,
            horizonLabel = dominantHorizon.label,
            whyNow = whyNow,
            nextStep = nextStep,
            validationStep = validationStep,
            conclusionLine = wikiConclusionLine,
            nextShiftLine = wikiNextShiftLine,
            groundingLine = wikiGroundingLine,
            maintenanceLine = wikiMaintenanceLine,
            maintenanceTargetLine = wikiMaintenanceTargetLine,
            maintenanceSourceLine = wikiMaintenanceSourceLine,
            maintenanceDimensionLine = wikiMaintenanceDimensionLine,
            maintenanceFocusLine = wikiMaintenanceFocusLine,
        ),
    )

private fun buildOpportunityCaptureSeed(
    line: String,
    support: String,
    direction: FollowedDirectionSummary?,
): CaptureSeed {
    val cleanTitle = direction?.thread?.title?.removePrefix("#")?.trim().orEmpty()
    val initialFolderKey = direction?.thread?.key
        ?.takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.ifBlank { null }
    val initialTags = direction?.thread?.key
        ?.takeIf { it.startsWith("tag:") }
        ?.removePrefix("tag:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()
    val topic = if (cleanTitle.isNotBlank()) "$cleanTitle · 新机会" else "新机会"
    return CaptureSeed(
        initialTopic = topic,
        initialContent = buildString {
            appendLine("把这条新机会接成一条可继续推进的记录：")
            if (cleanTitle.isNotBlank()) {
                appendLine("- 相关方向：$cleanTitle")
            }
            appendLine("- 机会：$line")
            support.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么值得试：$it")
            }
            direction?.whyNow?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么现在：$it")
            }
            direction?.postValidationAction?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 如果试成，下一步：$it")
            }
            appendLine("- 我准备怎么把它接成一条更具体的思路：")
            appendLine("- 这条机会和我过去哪段经验最有关：")
        },
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
        initialKnowledgeTrust = KnowledgeTrust.SIGNAL,
    )
}

private fun buildGapCaptureSeed(
    line: String,
    support: String,
    direction: FollowedDirectionSummary?,
): CaptureSeed {
    val cleanTitle = direction?.thread?.title?.removePrefix("#")?.trim().orEmpty()
    val initialFolderKey = direction?.thread?.key
        ?.takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.ifBlank { null }
    val initialTags = direction?.thread?.key
        ?.takeIf { it.startsWith("tag:") }
        ?.removePrefix("tag:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()
    val topic = if (cleanTitle.isNotBlank()) "$cleanTitle · 补材料" else "补材料"
    return CaptureSeed(
        initialTopic = topic,
        initialContent = buildString {
            appendLine("围绕这条缺口补一条新材料：")
            if (cleanTitle.isNotBlank()) {
                appendLine("- 相关线程：$cleanTitle")
            }
            appendLine("- 当前缺口：$line")
            support.takeIf { it.isNotBlank() }?.let {
                appendLine("- 这一口该喂什么：$it")
            }
            direction?.whyNow?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么现在值得继续养：$it")
            }
            appendLine("- 我补到的新材料：")
            appendLine("- 这条材料支持了什么：")
            appendLine("- 它又带出了什么新问题：")
        },
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
        initialKnowledgeTrust = KnowledgeTrust.HYPOTHESIS,
    )
}

private fun <T> Iterable<T>.firstNonBlankValue(
    selector: (T) -> String,
): String = asSequence()
    .map(selector)
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun FollowedDirectionSummary.reviewPeekLine(): String =
    wikiValidatedPoint
        .ifBlank { wikiVerifiedPoint }
        .ifBlank { wikiConclusionLine }
        .ifBlank { assetSummary }
        .ifBlank { wikiOpenQuestion }
        .ifBlank { blocker }
        .ifBlank { summary }
        .ifBlank { thread.summary }

private fun FollowedDirectionSummary.reviewReasonLine(): String =
    whyNow
        .ifBlank { wikiGroundingLine }
        .ifBlank { wikiTrustLine }
        .ifBlank { wikiMaintenanceFocusLine }
        .ifBlank { validationReason }
        .ifBlank { lastProgressLine }

@Composable
private fun ThreadRow(
    thread: ThemeThread,
    showFocus: Boolean,
    onOpenThread: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable { onOpenThread(thread.key) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${thread.title} · ${thread.noteCount} 条",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            if (showFocus && thread.focusLine.isNotBlank()) {
                Text(
                    text = thread.focusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = thread.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeeklyReviewCard(
    items: List<WeeklyReviewItem>,
    source: DailyBriefSource,
    statsLine: String,
    highlighted: Boolean,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "本周判断",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
                maxLines = 1,
            )
            if (statsLine.isNotBlank()) {
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                    maxLines = 1,
                )
            }
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 洞察")
            }
            if (items.isEmpty()) {
                Text(
                    text = "这一周的线索还不够多，再记几条更具体的内容会更有价值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSoft,
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayNoteCard(
    title: String,
    note: NoteEntity?,
    emptyText: String,
    accent: Color,
    nextActionText: String,
    nextActionSource: DailyBriefSource,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.45f) else BorderSoft,
        ),
        modifier = modifier.then(
            if (note != null) {
                Modifier.clickable { onOpenNote(note.id) }
            } else {
                Modifier
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
            )
            if (note == null) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                Text(
                    text = note.topic.ifBlank { "未命名记录" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nextActionText.isNotBlank()) {
                    InsightBlock(
                        sourceLabel = if (nextActionSource == DailyBriefSource.AI) "下一步 · AI 洞察" else "下一步",
                        tone = InsightTone.Primary,
                    ) {
                        Text(
                            text = nextActionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = note.content.asTodayPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${note.horizon.label} · ${note.status.label} · ${TimeFormatter.compact(note.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExplorationPromptCard(
    prompts: List<String>,
    source: DailyBriefSource,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今天值得想",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
                maxLines = 1,
            )
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 洞察")
            }
            if (prompts.isEmpty()) {
                Text(
                    text = "继续记录更具体的想法，AI 会从中提炼出更有启发性的探索方向。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                prompts.take(2).forEach { prompt ->
                    Text(
                        text = "• $prompt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
                    )
                }
            }
        }
    }
}

@Composable
private fun GentleReconnectCard(
    note: NoteEntity,
    reason: String,
    bridge: String,
    nextStep: String,
    source: DailyBriefSource,
    highlighted: Boolean,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else BorderSoft,
        ),
        modifier = Modifier.clickable { onOpenNote(note.id) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "重新接上",
                style = MaterialTheme.typography.labelLarge,
                color = TextSoft,
            )
            if (source == DailyBriefSource.AI) {
                InsightChip(text = "AI 提醒")
            }
            Text(
                text = note.topic.ifBlank { "未命名记录" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${note.horizon.label} · ${note.status.label}",
                style = MaterialTheme.typography.labelSmall,
                color = if (source == DailyBriefSource.AI) MaterialTheme.colorScheme.primary else TextSoft,
            )
            if (reason.isNotBlank()) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
            if (bridge.isNotBlank()) {
                Text(
                    text = bridge,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
            if (nextStep.isNotBlank()) {
                InsightBlock(
                    sourceLabel = "先做这一步",
                    tone = InsightTone.Primary,
                ) {
                    Text(
                        text = nextStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                    )
                }
            }
            Text(
                text = TimeFormatter.compact(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
            )
        }
    }
}


private fun String.asTodayPreview(): String =
    replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
