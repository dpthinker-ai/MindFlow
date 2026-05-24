package com.mindflow.app

import android.content.Intent
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.topic.ArticleContentExtractor
import com.mindflow.app.data.topic.ContentPolishPlanner
import com.mindflow.app.data.topic.ImageUnderstandingPlanner
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.data.topic.VoiceTranscriptionPlanner
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.model.AppThemeMode
import com.mindflow.app.data.model.AppearanceSettings
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.ai.AiCloudUsageReporter
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
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.ui.MindFlowApp
import com.mindflow.app.ui.navigation.MindFlowEntryIntents
import com.mindflow.app.ui.theme.MindFlowTheme

class MainActivity : ComponentActivity() {
    private val launchRequestState = mutableStateOf(MindFlowEntryIntents.fromIntent(intent))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRequestState.value = MindFlowEntryIntents.fromIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ),
        )

        val appContainer = (application as MindFlowApplication).appContainer
        val repository: NoteRepository = appContainer.noteRepository
        val aiSettingsRepository: AiSettingsRepository = appContainer.aiSettingsRepository
        val aiRuntimeSettingsRepository: AiRuntimeSettingsRepository = appContainer.aiRuntimeSettingsRepository
        val aiCloudUsageReporter: AiCloudUsageReporter = appContainer.aiCloudUsageReporter
        val cloudBackupSettingsRepository: CloudBackupSettingsRepository =
            appContainer.cloudBackupSettingsRepository
        val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository =
            appContainer.onDeviceModelSettingsRepository
        val reminderSettingsRepository: ReminderSettingsRepository = appContainer.reminderSettingsRepository
        val timeBankSettingsRepository: TimeBankSettingsRepository = appContainer.timeBankSettingsRepository
        val threadPreferencesRepository: ThreadPreferencesRepository = appContainer.threadPreferencesRepository
        val appearanceSettingsRepository: AppearanceSettingsRepository = appContainer.appearanceSettingsRepository
        val cloudBackupCoordinator: CloudBackupCoordinator = appContainer.cloudBackupCoordinator
        val onDeviceModelManager: OnDeviceModelManager = appContainer.onDeviceModelManager
        val reminderScheduler: ReminderScheduler = appContainer.reminderScheduler
        val dailyBriefPlanner: DailyBriefPlanner = appContainer.dailyBriefPlanner
        val nextActionPlanner: NextActionPlanner = appContainer.nextActionPlanner
        val weeklyReviewPlanner: WeeklyReviewPlanner = appContainer.weeklyReviewPlanner
        val fusionSuggestionPlanner: FusionSuggestionPlanner = appContainer.fusionSuggestionPlanner
        val flowKnowledgeCompressionPlanner = appContainer.flowKnowledgeCompressionPlanner
        val staleReconnectPlanner: StaleReconnectPlanner = appContainer.staleReconnectPlanner
        val threadExecutionPlanner: ThreadExecutionPlanner = appContainer.threadExecutionPlanner
        val externalResearchPlanner: ExternalResearchPlanner = appContainer.externalResearchPlanner
        val directionWikiCoordinator: DirectionWikiCoordinator = appContainer.directionWikiCoordinator
        val aiServiceClient: AiServiceClient = appContainer.aiServiceClient
        val contentPolishPlanner: ContentPolishPlanner = appContainer.contentPolishPlanner
        val topicExtractor: TopicExtractor = appContainer.topicExtractor
        val noteInsightPlanner = appContainer.noteInsightPlanner
        val voiceTranscriptionPlanner: VoiceTranscriptionPlanner = appContainer.voiceTranscriptionPlanner
        val articleContentExtractor: ArticleContentExtractor = appContainer.articleContentExtractor
        val imageUnderstandingPlanner: ImageUnderstandingPlanner = appContainer.imageUnderstandingPlanner
        val onDeviceAiClient: OnDeviceAiClient = appContainer.onDeviceAiClient
        val editorKnowledgeRecallPlanner: EditorKnowledgeRecallPlanner = appContainer.editorKnowledgeRecallPlanner
        val localKnowledgeMaintenancePlanner = appContainer.localKnowledgeMaintenancePlanner
        val localKnowledgeBrainPlanner = appContainer.localKnowledgeBrainPlanner
        val reviewChatPlanner: ReviewChatPlanner = appContainer.reviewChatPlanner
        val reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository =
            appContainer.reviewChatSavedConversationRepository
        setContent {
            val appearanceSettings by appearanceSettingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings(),
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (appearanceSettings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDarkTheme
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            scrim = AndroidColor.TRANSPARENT,
                            darkScrim = AndroidColor.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            scrim = AndroidColor.TRANSPARENT,
                            darkScrim = AndroidColor.TRANSPARENT,
                        )
                    },
                )
            }
            MindFlowTheme(darkTheme = darkTheme) {
                MindFlowApp(
                    noteRepository = repository,
                    aiSettingsRepository = aiSettingsRepository,
                    aiCloudUsageReporter = aiCloudUsageReporter,
                    aiRuntimeSettingsRepository = aiRuntimeSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
                    reminderSettingsRepository = reminderSettingsRepository,
                    timeBankSettingsRepository = timeBankSettingsRepository,
                    threadPreferencesRepository = threadPreferencesRepository,
                    appearanceSettingsRepository = appearanceSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    onDeviceModelManager = onDeviceModelManager,
                    reminderScheduler = reminderScheduler,
                    backgroundFolderOrganizer = appContainer.backgroundFolderOrganizer,
                    dailyBriefPlanner = dailyBriefPlanner,
                    nextActionPlanner = nextActionPlanner,
                    weeklyReviewPlanner = weeklyReviewPlanner,
                    fusionSuggestionPlanner = fusionSuggestionPlanner,
                    flowKnowledgeCompressionPlanner = flowKnowledgeCompressionPlanner,
                    staleReconnectPlanner = staleReconnectPlanner,
                    threadExecutionPlanner = threadExecutionPlanner,
                    externalResearchPlanner = externalResearchPlanner,
                    directionWikiCoordinator = directionWikiCoordinator,
                    aiServiceClient = aiServiceClient,
                    contentPolishPlanner = contentPolishPlanner,
                    topicExtractor = topicExtractor,
                    noteInsightPlanner = noteInsightPlanner,
                    voiceTranscriptionPlanner = voiceTranscriptionPlanner,
                    articleContentExtractor = articleContentExtractor,
                    imageUnderstandingPlanner = imageUnderstandingPlanner,
                    onDeviceAiClient = onDeviceAiClient,
                    editorKnowledgeRecallPlanner = editorKnowledgeRecallPlanner,
                    localKnowledgeMaintenancePlanner = localKnowledgeMaintenancePlanner,
                    localKnowledgeBrainPlanner = localKnowledgeBrainPlanner,
                    reviewChatPlanner = reviewChatPlanner,
                    reviewChatSavedConversationRepository = reviewChatSavedConversationRepository,
                    launchRequest = launchRequestState.value,
                    onLaunchRequestConsumed = { requestId ->
                        if (launchRequestState.value?.requestId == requestId) {
                            launchRequestState.value = null
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequestState.value = MindFlowEntryIntents.fromIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val appContainer = (application as MindFlowApplication).appContainer
            appContainer.backgroundFolderOrganizer.organizeInBackgroundIfNeeded()
            appContainer.cloudBackupCoordinator.syncInBackgroundIfNeeded()
            appContainer.directionWikiCoordinator.refreshInBackgroundIfNeeded()
        }
    }
}
