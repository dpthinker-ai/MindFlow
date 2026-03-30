package com.mindflow.app.di

import android.content.Context
import androidx.room.Room
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.backup.CloudNoteDocumentCodec
import com.mindflow.app.data.backup.PreferencesCloudBackupIndexRepository
import com.mindflow.app.data.backup.WebDavBackupClient
import com.mindflow.app.BuildConfig
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.local.MindFlowDatabase
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.repository.OfflineFirstNoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.PreferencesAiSettingsRepository
import com.mindflow.app.data.settings.PreferencesCloudBackupSettingsRepository
import com.mindflow.app.data.settings.PreferencesReminderSettingsRepository
import com.mindflow.app.data.settings.PreferencesThreadPreferencesRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.topic.AiFolderClassifier
import com.mindflow.app.data.topic.AiTagExtractor
import com.mindflow.app.data.topic.CombinedTagExtractor
import com.mindflow.app.data.topic.AiTopicExtractor
import com.mindflow.app.data.topic.CombinedFolderClassifier
import com.mindflow.app.data.topic.RuleBasedTagExtractor
import com.mindflow.app.data.topic.CombinedTopicExtractor
import com.mindflow.app.data.topic.RuleBasedFolderClassifier
import com.mindflow.app.data.topic.RuleBasedTopicExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        MindFlowDatabase::class.java,
        "mindflow.db",
    ).addMigrations(
        MindFlowDatabase.MIGRATION_1_2,
        MindFlowDatabase.MIGRATION_2_3,
        MindFlowDatabase.MIGRATION_3_4,
    )
        .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val aiSettingsRepository: AiSettingsRepository = PreferencesAiSettingsRepository(
        context = context.applicationContext,
        defaultSettings = AiSettings(
            apiKey = BuildConfig.AI_API_KEY,
            baseUrl = BuildConfig.AI_BASE_URL,
            model = BuildConfig.AI_MODEL,
        ),
    )

    val cloudBackupSettingsRepository: CloudBackupSettingsRepository =
        PreferencesCloudBackupSettingsRepository(
            context = context.applicationContext,
        )

    val reminderSettingsRepository: ReminderSettingsRepository =
        PreferencesReminderSettingsRepository(
            context = context.applicationContext,
        )

    val threadPreferencesRepository: ThreadPreferencesRepository =
        PreferencesThreadPreferencesRepository(
            context = context.applicationContext,
        )

    private val cloudBackupIndexRepository = PreferencesCloudBackupIndexRepository(
        context = context.applicationContext,
    )

    val aiServiceClient = AiServiceClient()

    private val topicExtractor = CombinedTopicExtractor(
        aiTopicExtractor = AiTopicExtractor(
            aiSettingsRepository = aiSettingsRepository,
            aiServiceClient = aiServiceClient,
        ),
        ruleBasedTopicExtractor = RuleBasedTopicExtractor(),
    )

    private val tagExtractor = CombinedTagExtractor(
        aiTagExtractor = AiTagExtractor(
            aiSettingsRepository = aiSettingsRepository,
            aiServiceClient = aiServiceClient,
        ),
        ruleBasedTagExtractor = RuleBasedTagExtractor(),
    )

    private val folderClassifier = CombinedFolderClassifier(
        aiFolderClassifier = AiFolderClassifier(
            aiSettingsRepository = aiSettingsRepository,
            aiServiceClient = aiServiceClient,
        ),
        ruleBasedFolderClassifier = RuleBasedFolderClassifier(),
    )

    val noteRepository: NoteRepository = OfflineFirstNoteRepository(
        database = database,
        noteDao = database.noteDao(),
        historyDao = database.noteStatusHistoryDao(),
        topicExtractor = topicExtractor,
        folderClassifier = folderClassifier,
        tagExtractor = tagExtractor,
        markdownExporter = MarkdownExporter(),
        markdownImportParser = MarkdownImportParser(),
        applicationScope = applicationScope,
    )

    val cloudBackupCoordinator = CloudBackupCoordinator(
        noteRepository = noteRepository,
        cloudBackupSettingsRepository = cloudBackupSettingsRepository,
        cloudBackupIndexRepository = cloudBackupIndexRepository,
        webDavBackupClient = WebDavBackupClient(),
        cloudNoteDocumentCodec = CloudNoteDocumentCodec(),
        database = database,
        noteDao = database.noteDao(),
        historyDao = database.noteStatusHistoryDao(),
        applicationScope = applicationScope,
    ).also { it.startAutoBackup() }

    val backgroundFolderOrganizer = BackgroundFolderOrganizer(
        context = context.applicationContext,
        noteRepository = noteRepository,
        aiSettingsRepository = aiSettingsRepository,
        applicationScope = applicationScope,
    )

    val dailyBriefPlanner = DailyBriefPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val nextActionPlanner = NextActionPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val weeklyReviewPlanner = WeeklyReviewPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val fusionSuggestionPlanner = FusionSuggestionPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val staleReconnectPlanner = StaleReconnectPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val reminderScheduler = ReminderScheduler(
        context = context.applicationContext,
        reminderSettingsRepository = reminderSettingsRepository,
        applicationScope = applicationScope,
    ).also { it.syncInBackground() }
}
