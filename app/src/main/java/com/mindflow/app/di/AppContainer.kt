package com.mindflow.app.di

import android.content.Context
import com.mindflow.app.data.backup.CloudBackupCoordinator
import com.mindflow.app.data.backup.CloudNoteDocumentCodec
import com.mindflow.app.data.backup.PreferencesCloudBackupIndexRepository
import com.mindflow.app.data.backup.WebDavBackupClient
import com.mindflow.app.BuildConfig
import com.mindflow.app.data.action.NextActionPlanner
import com.mindflow.app.data.brief.DailyBriefPlanner
import com.mindflow.app.data.connect.FusionSuggestionPlanner
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionPlanner
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.localmodel.LiteRtLmOnDeviceAiClient
import com.mindflow.app.data.localmodel.EditorKnowledgeRecallPlanner
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.repository.MarkdownNoteRepository
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.PreferencesAiSettingsRepository
import com.mindflow.app.data.settings.PreferencesCloudBackupSettingsRepository
import com.mindflow.app.data.settings.PreferencesOnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.PreferencesReminderSettingsRepository
import com.mindflow.app.data.settings.PreferencesTimeBankSettingsRepository
import com.mindflow.app.data.settings.PreferencesThreadPreferencesRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
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
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.ConceptGraphPlanner
import com.mindflow.app.data.wiki.KnowledgeGraphPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloudNoteDocumentCodec = CloudNoteDocumentCodec()

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

    val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository =
        PreferencesOnDeviceModelSettingsRepository(
            context = context.applicationContext,
        )

    val reminderSettingsRepository: ReminderSettingsRepository =
        PreferencesReminderSettingsRepository(
            context = context.applicationContext,
        )

    val timeBankSettingsRepository: TimeBankSettingsRepository =
        PreferencesTimeBankSettingsRepository(
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
    val onDeviceAiClient: OnDeviceAiClient = LiteRtLmOnDeviceAiClient(
        context = context.applicationContext,
    )
    val onDeviceModelManager = OnDeviceModelManager(
        context = context.applicationContext,
        repository = onDeviceModelSettingsRepository,
    )
    val editorKnowledgeRecallPlanner = EditorKnowledgeRecallPlanner(
        onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
        onDeviceAiClient = onDeviceAiClient,
    )

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

    val noteRepository: NoteRepository = MarkdownNoteRepository(
        appContext = context.applicationContext,
        topicExtractor = topicExtractor,
        folderClassifier = folderClassifier,
        tagExtractor = tagExtractor,
        markdownExporter = MarkdownExporter(),
        markdownImportParser = MarkdownImportParser(),
        cloudNoteDocumentCodec = cloudNoteDocumentCodec,
        applicationScope = applicationScope,
    )

    val cloudBackupCoordinator = CloudBackupCoordinator(
        noteRepository = noteRepository,
        cloudBackupSettingsRepository = cloudBackupSettingsRepository,
        cloudBackupIndexRepository = cloudBackupIndexRepository,
        webDavBackupClient = WebDavBackupClient(),
        cloudNoteDocumentCodec = cloudNoteDocumentCodec,
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

    val flowKnowledgeCompressionPlanner = FlowKnowledgeCompressionPlanner(
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
        onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
        onDeviceAiClient = onDeviceAiClient,
    )

    val staleReconnectPlanner = StaleReconnectPlanner(
        context = context.applicationContext,
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val threadExecutionPlanner = ThreadExecutionPlanner(
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val externalResearchPlanner = ExternalResearchPlanner(
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val knowledgeGraphPlanner = KnowledgeGraphPlanner(
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val conceptGraphPlanner = ConceptGraphPlanner(
        aiSettingsRepository = aiSettingsRepository,
        aiServiceClient = aiServiceClient,
    )

    val directionWikiCoordinator = DirectionWikiCoordinator(
        context = context.applicationContext,
        noteRepository = noteRepository,
        threadPreferencesRepository = threadPreferencesRepository,
        threadExecutionPlanner = threadExecutionPlanner,
        externalResearchPlanner = externalResearchPlanner,
        knowledgeGraphPlanner = knowledgeGraphPlanner,
        conceptGraphPlanner = conceptGraphPlanner,
        applicationScope = applicationScope,
    )

    val localKnowledgeMaintenancePlanner = LocalKnowledgeMaintenancePlanner(
        context = context.applicationContext,
        noteRepository = noteRepository,
        directionWikiCoordinator = directionWikiCoordinator,
        onDeviceModelSettingsRepository = onDeviceModelSettingsRepository,
        onDeviceAiClient = onDeviceAiClient,
        applicationScope = applicationScope,
    )

    val reminderScheduler = ReminderScheduler(
        context = context.applicationContext,
        reminderSettingsRepository = reminderSettingsRepository,
        applicationScope = applicationScope,
    ).also { it.syncInBackground() }
}
