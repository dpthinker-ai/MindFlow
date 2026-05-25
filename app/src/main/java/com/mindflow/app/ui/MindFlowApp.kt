package com.mindflow.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.ai.AiCloudUsageReporter
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.today.TodayKnowledgeCompressionPlanner
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.AiRuntimeSettingsRepository
import com.mindflow.app.data.settings.AppearanceSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.localmodel.EditorKnowledgeRecallPlanner
import com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlanner
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.topic.ArticleContentExtractor
import com.mindflow.app.data.topic.ContentPolishPlanner
import com.mindflow.app.data.topic.ImageUnderstandingPlanner
import com.mindflow.app.data.topic.NoteInsightPlanner
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.data.topic.VoiceTranscriptionPlanner
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.MindFlowDestinations
import com.mindflow.app.ui.navigation.MindFlowLaunchRequest
import com.mindflow.app.ui.navigation.ReviewChatSeed
import com.mindflow.app.ui.components.MindFlowUiTestTags
import com.mindflow.app.ui.screens.editor.EditorRoute
import com.mindflow.app.ui.screens.feed.FeedRoute
import com.mindflow.app.ui.screens.flow.KnowledgeGraphRoute
import com.mindflow.app.ui.screens.review.ReviewHomeRoute
import com.mindflow.app.ui.screens.today.TodayDiscoveryRoute
import com.mindflow.app.ui.screens.today.TodayRoute
import com.mindflow.app.ui.screens.today.TodayTaskDetailRoute
import com.mindflow.app.ui.screens.today.TodayViewModel
import com.mindflow.app.ui.screens.folder.FolderRoute
import com.mindflow.app.ui.screens.reviewchat.ReviewChatHistoryRoute
import com.mindflow.app.ui.screens.reviewchat.ReviewChatRoute
import com.mindflow.app.ui.screens.search.SearchRoute
import com.mindflow.app.ui.screens.settings.SettingsRoute
import com.mindflow.app.ui.screens.thread.ThreadRoute
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String,
)

@Composable
fun MindFlowApp(
    noteRepository: NoteRepository,
    aiSettingsRepository: AiSettingsRepository,
    aiCloudUsageReporter: AiCloudUsageReporter,
    aiRuntimeSettingsRepository: AiRuntimeSettingsRepository,
    cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    reminderSettingsRepository: ReminderSettingsRepository,
    timeBankSettingsRepository: TimeBankSettingsRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    appearanceSettingsRepository: AppearanceSettingsRepository,
    cloudBackupCoordinator: CloudBackupCoordinator,
    onDeviceModelManager: OnDeviceModelManager,
    reminderScheduler: ReminderScheduler,
    backgroundFolderOrganizer: BackgroundFolderOrganizer,
    dailyBriefPlanner: DailyBriefPlanner,
    nextActionPlanner: NextActionPlanner,
    weeklyReviewPlanner: WeeklyReviewPlanner,
    fusionSuggestionPlanner: FusionSuggestionPlanner,
    todayKnowledgeCompressionPlanner: TodayKnowledgeCompressionPlanner,
    staleReconnectPlanner: StaleReconnectPlanner,
    threadExecutionPlanner: ThreadExecutionPlanner,
    externalResearchPlanner: ExternalResearchPlanner,
    directionWikiCoordinator: DirectionWikiCoordinator,
    aiServiceClient: AiServiceClient,
    contentPolishPlanner: ContentPolishPlanner,
    topicExtractor: TopicExtractor,
    noteInsightPlanner: NoteInsightPlanner,
    voiceTranscriptionPlanner: VoiceTranscriptionPlanner,
    articleContentExtractor: ArticleContentExtractor,
    imageUnderstandingPlanner: ImageUnderstandingPlanner,
    onDeviceAiClient: OnDeviceAiClient,
    editorKnowledgeRecallPlanner: EditorKnowledgeRecallPlanner,
    localKnowledgeMaintenancePlanner: LocalKnowledgeMaintenancePlanner,
    localKnowledgeBrainPlanner: LocalKnowledgeBrainPlanner,
    reviewChatPlanner: ReviewChatPlanner,
    reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository,
    launchRequest: MindFlowLaunchRequest?,
    onLaunchRequestConsumed: (Long) -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val captureSeeds = remember { mutableStateMapOf<Long, CaptureSeed>() }
    val reviewChatSeeds = remember { mutableStateMapOf<Long, ReviewChatSeed>() }
    val sharedTodayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.factory(
            noteRepository = noteRepository,
            threadPreferencesRepository = threadPreferencesRepository,
            dailyBriefPlanner = dailyBriefPlanner,
            nextActionPlanner = nextActionPlanner,
            weeklyReviewPlanner = weeklyReviewPlanner,
            fusionSuggestionPlanner = fusionSuggestionPlanner,
            knowledgeCompressionPlanner = todayKnowledgeCompressionPlanner,
            staleReconnectPlanner = staleReconnectPlanner,
            threadExecutionPlanner = threadExecutionPlanner,
            externalResearchPlanner = externalResearchPlanner,
            directionWikiCoordinator = directionWikiCoordinator,
            localKnowledgeMaintenancePlanner = localKnowledgeMaintenancePlanner,
            localKnowledgeBrainPlanner = localKnowledgeBrainPlanner,
        ),
    )

    LaunchedEffect(aiCloudUsageReporter) {
        aiCloudUsageReporter.foregroundNotices.collect { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        }
    }

    fun openCapture(seed: CaptureSeed = CaptureSeed()) {
        captureSeeds[seed.requestId] = seed
        navController.navigate(MindFlowDestinations.captureRoute(seed.requestId))
    }

    fun openReviewChat(seed: ReviewChatSeed) {
        reviewChatSeeds[seed.requestId] = seed
        navController.navigate(MindFlowDestinations.reviewChatRoute(seed.requestId)) {
            launchSingleTop = true
        }
    }

    fun openReviewChatHistory() {
        navController.navigate(MindFlowDestinations.REVIEW_CHAT_HISTORY) {
            launchSingleTop = true
        }
    }

    fun openTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openNoteSafely: (Long) -> Unit = { noteId ->
        runCatching {
            navController.navigate(MindFlowDestinations.detailRoute(noteId)) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(launchRequest?.requestId) {
        when (val request = launchRequest) {
            is MindFlowLaunchRequest.OpenCapture -> {
                openCapture(request.seed)
                onLaunchRequestConsumed(request.requestId)
            }
            is MindFlowLaunchRequest.OpenNote -> {
                openNoteSafely(request.noteId)
                onLaunchRequestConsumed(request.requestId)
            }
            is MindFlowLaunchRequest.OpenFlow -> {
                openTopLevel(MindFlowDestinations.flowRoute(request.focus))
                onLaunchRequestConsumed(request.requestId)
            }
            is MindFlowLaunchRequest.OpenThread -> {
                navController.navigate(MindFlowDestinations.threadRoute(request.threadKey)) {
                    launchSingleTop = true
                }
                onLaunchRequestConsumed(request.requestId)
            }
            is MindFlowLaunchRequest.OpenSearch -> {
                openTopLevel(MindFlowDestinations.SEARCH_BASE)
                onLaunchRequestConsumed(request.requestId)
            }
            null -> Unit
        }
    }

    val topLevelDestinations = listOf(
        TopLevelDestination(MindFlowDestinations.FEED, "记录", Icons.Outlined.Edit, MindFlowUiTestTags.NAV_RECORD),
        TopLevelDestination(MindFlowDestinations.TODAY, "今天", Icons.Outlined.WbSunny, MindFlowUiTestTags.NAV_TODAY),
        TopLevelDestination(MindFlowDestinations.REVIEW, "回看", Icons.Outlined.History, MindFlowUiTestTags.NAV_REVIEW),
        TopLevelDestination(MindFlowDestinations.GRAPH, "图谱", Icons.Outlined.Hub, MindFlowUiTestTags.NAV_GRAPH),
        TopLevelDestination(MindFlowDestinations.SETTINGS, "设置", Icons.Outlined.Settings, MindFlowUiTestTags.NAV_SETTINGS),
    )

    val normalizedRoute = when {
        currentRoute == null -> null
        currentRoute == MindFlowDestinations.GRAPH -> MindFlowDestinations.GRAPH
        currentRoute == MindFlowDestinations.TODAY -> MindFlowDestinations.TODAY
        currentRoute == MindFlowDestinations.REVIEW -> MindFlowDestinations.REVIEW
        currentRoute == MindFlowDestinations.SETTINGS -> MindFlowDestinations.SETTINGS
        currentRoute == MindFlowDestinations.SEARCH -> MindFlowDestinations.SEARCH_BASE
        else -> currentRoute
    }

    val showBottomBar = normalizedRoute in setOf(
        MindFlowDestinations.FEED,
        MindFlowDestinations.TODAY,
        MindFlowDestinations.REVIEW,
        MindFlowDestinations.GRAPH,
        MindFlowDestinations.SETTINGS,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = MindFlowDestinations.FEED,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(MindFlowDestinations.FEED) {
                FeedRoute(
                    noteRepository = noteRepository,
                    timeBankSettingsRepository = timeBankSettingsRepository,
                    onCreateCapture = ::openCapture,
                    onOpenStatusFilter = { status, archivedOnly ->
                        navController.navigate(
                            MindFlowDestinations.searchRoute(
                                status = status,
                                archivedOnly = archivedOnly,
                            ),
                        )
                    },
                    onOpenNote = openNoteSafely,
                )
            }

            composable(MindFlowDestinations.TODAY) {
                TodayRoute(
                    viewModel = sharedTodayViewModel,
                    reviewChatSavedConversationRepository = reviewChatSavedConversationRepository,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onOpenNote = openNoteSafely,
                    onCreateCapture = ::openCapture,
                    onOpenTodayDiscovery = { navController.navigate(MindFlowDestinations.TODAY_DISCOVERY) },
                    onOpenTodayTask = { threadKey ->
                        navController.navigate(MindFlowDestinations.todayTaskDetailRoute(threadKey))
                    },
                    onOpenLatestSavedReviewChat = { sessionId ->
                        openReviewChat(ReviewChatSeed(savedSessionId = sessionId))
                    },
                    onOpenReviewChatHistory = ::openReviewChatHistory,
                )
            }

            composable(MindFlowDestinations.REVIEW) {
                ReviewHomeRoute(
                    reviewChatSavedConversationRepository = reviewChatSavedConversationRepository,
                    onOpenReviewChat = { question ->
                        openReviewChat(ReviewChatSeed(initialQuestion = question))
                    },
                    onOpenLatestSavedReviewChat = { sessionId ->
                        openReviewChat(ReviewChatSeed(savedSessionId = sessionId))
                    },
                    onOpenReviewChatHistory = ::openReviewChatHistory,
                )
            }

            composable(MindFlowDestinations.TODAY_DISCOVERY) {
                TodayDiscoveryRoute(
                    viewModel = sharedTodayViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTaskDetail = { threadKey ->
                        navController.navigate(MindFlowDestinations.todayTaskDetailRoute(threadKey))
                    },
                    onCreateCapture = ::openCapture,
                )
            }

            composable(
                route = MindFlowDestinations.TODAY_TASK_DETAIL,
                arguments = listOf(navArgument(MindFlowDestinations.TODAY_TASK_DETAIL_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val rawThreadKey = backStackEntry.arguments
                    ?.getString(MindFlowDestinations.TODAY_TASK_DETAIL_ARG)
                    .orEmpty()
                val threadKey = Uri.decode(rawThreadKey)
                TodayTaskDetailRoute(
                    viewModel = sharedTodayViewModel,
                    threadKey = threadKey,
                    onBack = { navController.popBackStack() },
                    onOpenThread = { key -> navController.navigate(MindFlowDestinations.threadRoute(key)) },
                    onAskReview = { question ->
                        openReviewChat(ReviewChatSeed(initialQuestion = question))
                    },
                )
            }

            composable(MindFlowDestinations.GRAPH) {
                KnowledgeGraphRoute(
                    noteRepository = noteRepository,
                    directionWikiCoordinator = directionWikiCoordinator,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onOpenNote = openNoteSafely,
                )
            }

            composable(
                route = MindFlowDestinations.THREAD,
                arguments = listOf(navArgument(MindFlowDestinations.THREAD_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val rawThreadKey = backStackEntry.arguments?.getString(MindFlowDestinations.THREAD_ARG).orEmpty()
                val threadKey = Uri.decode(rawThreadKey)
                ThreadRoute(
                    noteRepository = noteRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                    threadKey = threadKey,
                    onBack = { navController.popBackStack() },
                    onOpenNote = openNoteSafely,
                    onCreateThreadNote = ::openCapture,
                )
            }

            composable(
                route = MindFlowDestinations.FOLDER,
                arguments = listOf(navArgument(MindFlowDestinations.FOLDER_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val folderKey = backStackEntry.arguments?.getString(MindFlowDestinations.FOLDER_ARG).orEmpty()
                FolderRoute(
                    noteRepository = noteRepository,
                    folderKey = folderKey,
                    onBack = { navController.popBackStack() },
                    onOpenNote = openNoteSafely,
                )
            }

            composable(
                route = MindFlowDestinations.SEARCH,
                arguments = listOf(
                    navArgument(MindFlowDestinations.SEARCH_STATUS_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(MindFlowDestinations.SEARCH_ARCHIVED_ONLY_ARG) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                val statusName = backStackEntry.arguments?.getString(MindFlowDestinations.SEARCH_STATUS_ARG)
                val initialStatus = statusName?.let(NoteStatus::valueOf)
                val archivedOnly = backStackEntry.arguments?.getBoolean(MindFlowDestinations.SEARCH_ARCHIVED_ONLY_ARG) ?: false
                SearchRoute(
                    noteRepository = noteRepository,
                    backgroundFolderOrganizer = backgroundFolderOrganizer,
                    initialStatus = initialStatus,
                    initialArchivedOnly = archivedOnly,
                    onOpenNote = openNoteSafely,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onCreateCapture = ::openCapture,
                )
            }

            composable(MindFlowDestinations.SETTINGS) {
                SettingsRoute(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    aiRuntimeSettingsRepository = aiRuntimeSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
                    reminderSettingsRepository = reminderSettingsRepository,
                    timeBankSettingsRepository = timeBankSettingsRepository,
                    appearanceSettingsRepository = appearanceSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    onDeviceModelManager = onDeviceModelManager,
                    reminderScheduler = reminderScheduler,
                    directionWikiCoordinator = directionWikiCoordinator,
                    aiServiceClient = aiServiceClient,
                    onDeviceAiClient = onDeviceAiClient,
                    localKnowledgeMaintenancePlanner = localKnowledgeMaintenancePlanner,
                )
            }

            composable(
                route = MindFlowDestinations.CAPTURE,
                arguments = listOf(navArgument(MindFlowDestinations.CAPTURE_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val seedId = backStackEntry.arguments?.getLong(MindFlowDestinations.CAPTURE_ARG) ?: 0L
                val captureSeed = captureSeeds[seedId] ?: CaptureSeed(requestId = seedId)
                EditorRoute(
                    noteRepository = noteRepository,
                    contentPolishPlanner = contentPolishPlanner,
                    topicExtractor = topicExtractor,
                    noteInsightPlanner = noteInsightPlanner,
                    voiceTranscriptionPlanner = voiceTranscriptionPlanner,
                    articleContentExtractor = articleContentExtractor,
                    imageUnderstandingPlanner = imageUnderstandingPlanner,
                    editorKnowledgeRecallPlanner = editorKnowledgeRecallPlanner,
                    noteId = null,
                    captureSessionKey = seedId,
                    captureMode = captureSeed.mode,
                    initialContent = captureSeed.initialContent,
                    initialTopic = captureSeed.initialTopic,
                    initialFolderKey = captureSeed.initialFolderKey,
                    initialTags = captureSeed.initialTags,
                    initialKnowledgeTrust = captureSeed.initialKnowledgeTrust,
                    autoStartVoiceInput = captureSeed.autoStartVoiceInput,
                    onOpenNote = openNoteSafely,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onBack = {
                        captureSeeds.remove(seedId)
                        navController.popBackStack()
                    },
                    onSavedNewNote = {
                        captureSeeds.remove(seedId)
                        navController.navigate(MindFlowDestinations.FEED) {
                            popUpTo(MindFlowDestinations.FEED) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = MindFlowDestinations.REVIEW_CHAT,
                arguments = listOf(navArgument(MindFlowDestinations.REVIEW_CHAT_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val seedId = backStackEntry.arguments?.getLong(MindFlowDestinations.REVIEW_CHAT_ARG) ?: 0L
                val seed = reviewChatSeeds.remove(seedId) ?: ReviewChatSeed(requestId = seedId)
                ReviewChatRoute(
                    seed = seed,
                    planner = reviewChatPlanner,
                    savedConversationRepository = reviewChatSavedConversationRepository,
                    onBack = { navController.popBackStack() },
                    onOpenHistory = ::openReviewChatHistory,
                    onOpenRecord = openNoteSafely,
                    onCreateCapture = ::openCapture,
                )
            }

            composable(MindFlowDestinations.REVIEW_CHAT_HISTORY) {
                ReviewChatHistoryRoute(
                    savedConversationRepository = reviewChatSavedConversationRepository,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { sessionId ->
                        openReviewChat(ReviewChatSeed(savedSessionId = sessionId))
                    },
                    onStartNewChat = {
                        openReviewChat(ReviewChatSeed())
                    },
                )
            }

            composable(
                route = MindFlowDestinations.DETAIL,
                arguments = listOf(navArgument(MindFlowDestinations.DETAIL_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong(MindFlowDestinations.DETAIL_ARG)
                EditorRoute(
                    noteRepository = noteRepository,
                    contentPolishPlanner = contentPolishPlanner,
                    topicExtractor = topicExtractor,
                    noteInsightPlanner = noteInsightPlanner,
                    voiceTranscriptionPlanner = voiceTranscriptionPlanner,
                    articleContentExtractor = articleContentExtractor,
                    imageUnderstandingPlanner = imageUnderstandingPlanner,
                    editorKnowledgeRecallPlanner = editorKnowledgeRecallPlanner,
                    noteId = noteId,
                    captureSessionKey = null,
                    captureMode = com.mindflow.app.ui.navigation.CaptureMode.TEXT,
                    initialContent = "",
                    initialTopic = "",
                    initialFolderKey = null,
                    initialTags = emptyList(),
                    initialKnowledgeTrust = com.mindflow.app.data.model.KnowledgeTrust.NONE,
                    autoStartVoiceInput = false,
                    onOpenNote = openNoteSafely,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onBack = { navController.popBackStack() },
                    onSavedNewNote = { navController.popBackStack() },
                )
            }
        }

        if (showBottomBar) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    topLevelDestinations.forEach { destination ->
                        val active = normalizedRoute == destination.route
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) {
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            ),
                                        )
                                    } else {
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                    },
                                ),
                        ) {
                                TextButton(
                                    onClick = {
                                        openTopLevel(destination.route)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                                        .testTag(destination.testTag),
                                    contentPadding = PaddingValues(vertical = 7.dp),
                                ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                        modifier = Modifier.size(23.dp),
                                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.size(2.dp))
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
