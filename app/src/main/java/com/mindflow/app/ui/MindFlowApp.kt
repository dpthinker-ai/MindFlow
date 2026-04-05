package com.mindflow.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.ui.navigation.CaptureSeed
import com.mindflow.app.ui.navigation.MindFlowDestinations
import com.mindflow.app.ui.navigation.MindFlowLaunchRequest
import com.mindflow.app.ui.screens.editor.EditorRoute
import com.mindflow.app.ui.screens.feed.FeedRoute
import com.mindflow.app.ui.screens.flow.FlowRoute
import com.mindflow.app.ui.screens.folder.FolderRoute
import com.mindflow.app.ui.screens.search.SearchRoute
import com.mindflow.app.ui.screens.settings.SettingsRoute
import com.mindflow.app.ui.screens.stats.StatsRoute
import com.mindflow.app.ui.screens.thread.ThreadRoute
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.Panel
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun MindFlowApp(
    noteRepository: NoteRepository,
    aiSettingsRepository: AiSettingsRepository,
    cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    reminderSettingsRepository: ReminderSettingsRepository,
    timeBankSettingsRepository: TimeBankSettingsRepository,
    threadPreferencesRepository: ThreadPreferencesRepository,
    cloudBackupCoordinator: CloudBackupCoordinator,
    reminderScheduler: ReminderScheduler,
    backgroundFolderOrganizer: BackgroundFolderOrganizer,
    dailyBriefPlanner: DailyBriefPlanner,
    nextActionPlanner: NextActionPlanner,
    weeklyReviewPlanner: WeeklyReviewPlanner,
    fusionSuggestionPlanner: FusionSuggestionPlanner,
    staleReconnectPlanner: StaleReconnectPlanner,
    threadExecutionPlanner: ThreadExecutionPlanner,
    externalResearchPlanner: ExternalResearchPlanner,
    directionWikiCoordinator: DirectionWikiCoordinator,
    aiServiceClient: AiServiceClient,
    launchRequest: MindFlowLaunchRequest?,
    onLaunchRequestConsumed: (Long) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val captureSeeds = remember { mutableStateMapOf<Long, CaptureSeed>() }

    fun openCapture(seed: CaptureSeed = CaptureSeed()) {
        captureSeeds[seed.requestId] = seed
        navController.navigate(MindFlowDestinations.captureRoute(seed.requestId))
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
        TopLevelDestination(MindFlowDestinations.FEED, "记录", Icons.Outlined.SpaceDashboard),
        TopLevelDestination(MindFlowDestinations.FLOW_BASE, "Flow", Icons.Outlined.AutoAwesome),
        TopLevelDestination(MindFlowDestinations.SEARCH_BASE, "查找", Icons.Outlined.Search),
        TopLevelDestination(MindFlowDestinations.STATS, "统计", Icons.Outlined.QueryStats),
        TopLevelDestination(MindFlowDestinations.SETTINGS, "设置", Icons.Outlined.Settings),
    )

    val normalizedRoute = when {
        currentRoute == null -> null
        currentRoute.startsWith(MindFlowDestinations.FLOW_BASE) -> MindFlowDestinations.FLOW_BASE
        currentRoute.startsWith(MindFlowDestinations.SEARCH_BASE) -> MindFlowDestinations.SEARCH_BASE
        else -> currentRoute
    }

    val showBottomBar = normalizedRoute in setOf(
        MindFlowDestinations.FEED,
        MindFlowDestinations.FLOW_BASE,
        MindFlowDestinations.SEARCH_BASE,
        MindFlowDestinations.STATS,
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
                    onCreateNote = { openCapture() },
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

            composable(
                route = MindFlowDestinations.FLOW,
                arguments = listOf(
                    navArgument(MindFlowDestinations.FLOW_FOCUS_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val focus = backStackEntry.arguments?.getString(MindFlowDestinations.FLOW_FOCUS_ARG)
                    ?.let { raw ->
                        com.mindflow.app.ui.navigation.FlowFocus.entries.firstOrNull { it.name == raw }
                    }
                FlowRoute(
                    noteRepository = noteRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    dailyBriefPlanner = dailyBriefPlanner,
                    nextActionPlanner = nextActionPlanner,
                    weeklyReviewPlanner = weeklyReviewPlanner,
                    fusionSuggestionPlanner = fusionSuggestionPlanner,
                    staleReconnectPlanner = staleReconnectPlanner,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                    initialFocus = focus,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                    onOpenNote = openNoteSafely,
                    onCreateCapture = ::openCapture,
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
                    directionWikiCoordinator = directionWikiCoordinator,
                    initialStatus = initialStatus,
                    initialArchivedOnly = archivedOnly,
                    onOpenNote = openNoteSafely,
                    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
                )
            }

            composable(MindFlowDestinations.STATS) {
                StatsRoute(
                    noteRepository = noteRepository,
                    onOpenNote = openNoteSafely,
                )
            }

            composable(MindFlowDestinations.SETTINGS) {
                SettingsRoute(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    reminderSettingsRepository = reminderSettingsRepository,
                    timeBankSettingsRepository = timeBankSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    reminderScheduler = reminderScheduler,
                    directionWikiCoordinator = directionWikiCoordinator,
                    aiServiceClient = aiServiceClient,
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
                    aiSettingsRepository = aiSettingsRepository,
                    aiServiceClient = aiServiceClient,
                    noteId = null,
                    captureSessionKey = seedId,
                    initialContent = captureSeed.initialContent,
                    initialTopic = captureSeed.initialTopic,
                    initialFolderKey = captureSeed.initialFolderKey,
                    initialTags = captureSeed.initialTags,
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
                route = MindFlowDestinations.DETAIL,
                arguments = listOf(navArgument(MindFlowDestinations.DETAIL_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong(MindFlowDestinations.DETAIL_ARG)
                EditorRoute(
                    noteRepository = noteRepository,
                    aiSettingsRepository = aiSettingsRepository,
                    aiServiceClient = aiServiceClient,
                    noteId = noteId,
                    captureSessionKey = null,
                    initialContent = "",
                    initialTopic = "",
                    initialFolderKey = null,
                    initialTags = emptyList(),
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                color = Panel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topLevelDestinations.forEach { destination ->
                        val active = normalizedRoute == destination.route
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) {
                                        Brush.horizontalGradient(
                                            listOf(
                                                AccentBlue.copy(alpha = 0.12f),
                                                AccentBlue.copy(alpha = 0.12f),
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
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 10.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                        tint = if (active) Accent else TextSoft,
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Accent else TextSoft.copy(alpha = 0.72f),
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
