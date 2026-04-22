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
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.followup.StaleReconnectPlanner
import com.mindflow.app.data.flow.FlowKnowledgeCompressionPlanner
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlanner
import com.mindflow.app.data.localmodel.LiteRtLmOnDeviceAiClient
import com.mindflow.app.data.knowledgebrain.MemoryLayerRepository
import com.mindflow.app.data.knowledgebrain.RoomMemoryLayerRepository
import com.mindflow.app.data.localmodel.EditorKnowledgeRecallPlanner
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.OnDeviceAiClient
import com.mindflow.app.data.localmodel.OnDeviceModelManager
import com.mindflow.app.data.local.reviewchat.ReviewChatDatabase
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.reminder.ReminderScheduler
import com.mindflow.app.data.review.WeeklyReviewPlanner
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.repository.MarkdownNoteRepository
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatOnDeviceRequest
import com.mindflow.app.data.reviewchat.RoomReviewChatSavedConversationRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.PreferencesAiSettingsRepository
import com.mindflow.app.data.settings.PreferencesCloudBackupSettingsRepository
import com.mindflow.app.data.settings.PreferencesOnDeviceModelSettingsRepository
import com.mindflow.app.data.settings.PreferencesReminderSettingsRepository
import com.mindflow.app.data.topic.ContentPolishPlanner
import com.mindflow.app.data.settings.PreferencesTimeBankSettingsRepository
import com.mindflow.app.data.settings.PreferencesThreadPreferencesRepository
import com.mindflow.app.data.settings.ReminderSettingsRepository
import com.mindflow.app.data.settings.TimeBankSettingsRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import com.mindflow.app.data.topic.AiServiceClient
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskTraceRecorder
import com.mindflow.app.data.ai.CloudAiTaskProvider
import com.mindflow.app.data.ai.OnDeviceAiTaskProvider
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
import java.io.File
import com.mindflow.app.data.wiki.ConceptGraphPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloudNoteDocumentCodec = CloudNoteDocumentCodec()
    private val database = Room.databaseBuilder(
        context.applicationContext,
        com.mindflow.app.data.local.MindFlowDatabase::class.java,
        "mindflow.db",
    ).addMigrations(
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_1_2,
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_2_3,
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_3_4,
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_4_5,
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_5_6,
        com.mindflow.app.data.local.MindFlowDatabase.MIGRATION_6_7,
    ).build()

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

    private val aiTaskTraceRecorder = AiTaskTraceRecorder(
        File(context.applicationContext.filesDir, "ai-traces"),
    )

    val aiTaskRouter = AiTaskRouter(
        resolveMode = { onDeviceModelSettingsRepository.getCurrent().executionMode },
        onDeviceProvider = OnDeviceAiTaskProvider(
            settingsRepository = onDeviceModelSettingsRepository,
            client = onDeviceAiClient,
        ),
        cloudProvider = CloudAiTaskProvider(
            settingsRepository = aiSettingsRepository,
            client = aiServiceClient,
        ),
        traceRecorder = aiTaskTraceRecorder,
    )

    val contentPolishPlanner = ContentPolishPlanner(
        aiTaskRouter = aiTaskRouter,
    )

    private val topicExtractor = CombinedTopicExtractor(
        aiTopicExtractor = AiTopicExtractor(
            aiTaskRouter = aiTaskRouter,
        ),
        ruleBasedTopicExtractor = RuleBasedTopicExtractor(),
    )

    private val tagExtractor = CombinedTagExtractor(
        aiTagExtractor = AiTagExtractor(
            aiTaskRouter = aiTaskRouter,
        ),
        ruleBasedTagExtractor = RuleBasedTagExtractor(),
    )

    private val folderClassifier = CombinedFolderClassifier(
        aiFolderClassifier = AiFolderClassifier(
            aiTaskRouter = aiTaskRouter,
        ),
        ruleBasedFolderClassifier = RuleBasedFolderClassifier(),
    )

    val localKnowledgeBrainPlanner: LocalKnowledgeBrainPlanner by lazy {
        LocalKnowledgeBrainPlanner(
            memoryLayerRepository = memoryLayerRepository,
            loadNoteById = { noteId -> noteRepository.getNote(noteId) },
            loadAllNotes = { noteRepository.observeAllNotes().first().filter { !it.isArchived } },
            runOnDevice = { prompt ->
                val settings = onDeviceModelSettingsRepository.getCurrent()
                onDeviceAiClient.generateReviewChatReply(
                    settings = settings,
                    request = ReviewChatOnDeviceRequest(
                        sessionId = "local-knowledge-brain",
                        prompt = prompt,
                        resetConversation = true,
                    ),
                )
            },
            applicationScope = applicationScope,
        )
    }

    val noteRepository: NoteRepository = MarkdownNoteRepository(
        appContext = context.applicationContext,
        topicExtractor = topicExtractor,
        folderClassifier = folderClassifier,
        tagExtractor = tagExtractor,
        markdownExporter = MarkdownExporter(),
        markdownImportParser = MarkdownImportParser(),
        cloudNoteDocumentCodec = cloudNoteDocumentCodec,
        applicationScope = applicationScope,
        onNoteMutated = { noteId -> localKnowledgeBrainPlanner.enqueueNoteIngestion(noteId) },
    )

    val memoryLayerRepository: MemoryLayerRepository = RoomMemoryLayerRepository(
        dao = database.memoryLayerDao(),
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

    val conceptGraphPlanner = ConceptGraphPlanner(
        aiTaskRouter = aiTaskRouter,
    )

    val directionWikiCoordinator = DirectionWikiCoordinator(
        context = context.applicationContext,
        noteRepository = noteRepository,
        threadPreferencesRepository = threadPreferencesRepository,
        threadExecutionPlanner = threadExecutionPlanner,
        externalResearchPlanner = externalResearchPlanner,
        conceptGraphPlanner = conceptGraphPlanner,
        memoryLayerRepository = memoryLayerRepository,
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

    private val reviewChatDatabase: ReviewChatDatabase = Room.databaseBuilder(
        context.applicationContext,
        ReviewChatDatabase::class.java,
        "review-chat.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository =
        RoomReviewChatSavedConversationRepository(
            dao = reviewChatDatabase.reviewChatDao(),
        )

    val reviewChatPlanner = ReviewChatPlanner(
        loadNotes = {
            noteRepository.observeAllNotes().first()
        },
        loadWeeklyReview = {
            weeklyReviewPlanner.state.first()
        },
        loadMaintenanceSnapshot = {
            localKnowledgeMaintenancePlanner.snapshot.value
        },
        loadWikiSnapshot = {
            directionWikiCoordinator.snapshot.value
        },
        resolveExecutionMode = {
            onDeviceModelSettingsRepository.getCurrent().executionMode
        },
        isCloudConfigured = {
            aiSettingsRepository.getCurrent().let { it.aiEnabled && it.isConfigured }
        },
        isOnDeviceReady = {
            onDeviceModelSettingsRepository.getCurrent().isReady
        },
        planQueryWithCloud = { prompt ->
            aiServiceClient.planReviewChatQuery(
                settings = aiSettingsRepository.getCurrent(),
                prompt = prompt,
            )
        },
        runCloud = { prompt ->
            aiServiceClient.generateReviewChatReply(
                settings = aiSettingsRepository.getCurrent(),
                prompt = prompt,
            )
        },
        runOnDevice = { request ->
            onDeviceAiClient.generateReviewChatReply(
                settings = onDeviceModelSettingsRepository.getCurrent(),
                request = request,
            )
        },
        streamOnDevice = { request ->
            onDeviceAiClient.streamReviewChatReply(
                settings = onDeviceModelSettingsRepository.getCurrent(),
                request = request,
            )
        },
    )

    val reminderScheduler = ReminderScheduler(
        context = context.applicationContext,
        reminderSettingsRepository = reminderSettingsRepository,
        applicationScope = applicationScope,
    ).also { it.syncInBackground() }
}
