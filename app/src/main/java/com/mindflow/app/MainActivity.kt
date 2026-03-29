package com.mindflow.app

import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.topic.AiServiceClient
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.ui.MindFlowApp
import com.mindflow.app.ui.theme.MindFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val cloudBackupSettingsRepository: CloudBackupSettingsRepository =
            appContainer.cloudBackupSettingsRepository
        val reminderSettingsRepository: ReminderSettingsRepository = appContainer.reminderSettingsRepository
        val cloudBackupCoordinator: CloudBackupCoordinator = appContainer.cloudBackupCoordinator
        val reminderScheduler: ReminderScheduler = appContainer.reminderScheduler
        val dailyBriefPlanner: DailyBriefPlanner = appContainer.dailyBriefPlanner
        val nextActionPlanner: NextActionPlanner = appContainer.nextActionPlanner
        val weeklyReviewPlanner: WeeklyReviewPlanner = appContainer.weeklyReviewPlanner
        val fusionSuggestionPlanner: FusionSuggestionPlanner = appContainer.fusionSuggestionPlanner
        val aiServiceClient: AiServiceClient = appContainer.aiServiceClient
        setContent {
            MindFlowTheme {
                MindFlowApp(
                    noteRepository = repository,
                    aiSettingsRepository = aiSettingsRepository,
                    cloudBackupSettingsRepository = cloudBackupSettingsRepository,
                    reminderSettingsRepository = reminderSettingsRepository,
                    cloudBackupCoordinator = cloudBackupCoordinator,
                    reminderScheduler = reminderScheduler,
                    backgroundFolderOrganizer = appContainer.backgroundFolderOrganizer,
                    dailyBriefPlanner = dailyBriefPlanner,
                    nextActionPlanner = nextActionPlanner,
                    weeklyReviewPlanner = weeklyReviewPlanner,
                    fusionSuggestionPlanner = fusionSuggestionPlanner,
                    aiServiceClient = aiServiceClient,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val appContainer = (application as MindFlowApplication).appContainer
            appContainer.backgroundFolderOrganizer.organizeInBackgroundIfNeeded()
            appContainer.cloudBackupCoordinator.syncInBackgroundIfNeeded()
        }
    }
}
