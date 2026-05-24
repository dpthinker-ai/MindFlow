package com.mindflow.app.ui.screens.editor

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.CloudBackupSnapshot
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.FolderRefreshResult
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TagRefreshResult
import com.mindflow.app.data.model.TopicExtractionResult
import com.mindflow.app.data.model.TopicRefreshResult
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.model.TopicSuggestion
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.topic.ArticleContentExtractor
import com.mindflow.app.data.topic.ArticleHtmlFetcher
import com.mindflow.app.data.topic.ContentPolishPlanner
import com.mindflow.app.data.topic.ImageUnderstandingPlanner
import com.mindflow.app.data.topic.NoteInsightPlanner
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.data.topic.VoiceTranscriptionPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_ignoresDuplicateNewNoteSaveWhileFirstSaveIsPending() = runTest(dispatcher) {
        val repository = RecordingNoteRepository(createDelayMs = 10)
        val viewModel = noteEditorViewModel(repository)

        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertThat(repository.createdContents).containsExactly("语音转写（可编辑）：一次录音")
    }

    @Test
    fun ensureArticleExtraction_skipsFetcherWhenManualBodyExists() = runTest(dispatcher) {
        val fetcher = RecordingArticleHtmlFetcher()
        val viewModel = noteEditorViewModel(
            repository = RecordingNoteRepository(),
            initialContent = "链接：https://example.com/post\n正文：我已经手动贴了正文",
            articleContentExtractor = ArticleContentExtractor(fetcher = fetcher),
        )

        viewModel.ensureArticleExtraction("https://example.com/post")
        advanceUntilIdle()

        assertThat(fetcher.fetchCount).isEqualTo(0)
        assertThat(viewModel.uiState.value.isExtractingArticle).isFalse()
        assertThat(articleBodyFromContent(viewModel.uiState.value.content)).isEqualTo("我已经手动贴了正文")
        assertThat(articleStatusFromContent(viewModel.uiState.value.content)).isEqualTo("正文已有内容，未重新提取")
    }

    private fun noteEditorViewModel(
        repository: RecordingNoteRepository,
        initialContent: String = "语音转写（可编辑）：一次录音",
        articleContentExtractor: ArticleContentExtractor = ArticleContentExtractor(fetcher = { "" }),
    ): NoteEditorViewModel {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.ON_DEVICE_ONLY },
            onDeviceProvider = EmptyAiTaskProvider,
            cloudProvider = EmptyAiTaskProvider,
        )
        return NoteEditorViewModel(
            noteRepository = repository,
            contentPolishPlanner = ContentPolishPlanner(router),
            topicExtractor = FakeTopicExtractor,
            noteInsightPlanner = NoteInsightPlanner(router),
            voiceTranscriptionPlanner = VoiceTranscriptionPlanner(router),
            articleContentExtractor = articleContentExtractor,
            imageUnderstandingPlanner = ImageUnderstandingPlanner(router),
            noteId = null,
            initialContent = initialContent,
            initialTopic = "",
            initialFolderKey = null,
            initialTags = listOf("语音"),
            initialKnowledgeTrust = KnowledgeTrust.NONE,
        )
    }
}

private object EmptyAiTaskProvider : AiTaskProvider {
    override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = null
}

private object FakeTopicExtractor : TopicExtractor {
    override suspend fun extract(
        content: String,
        automaticPreference: com.mindflow.app.data.ai.AiAutomaticPreference,
    ): TopicExtractionResult = TopicExtractionResult(extractRule(content))

    override fun extractRule(content: String): TopicSuggestion =
        TopicSuggestion(topic = "语音记录", source = TopicSource.RULE)
}

private class RecordingArticleHtmlFetcher : ArticleHtmlFetcher {
    var fetchCount = 0
        private set

    override suspend fun fetch(url: String): String {
        fetchCount += 1
        return """
            <html>
              <head><title>自动标题</title></head>
              <body><article>自动解析正文</article></body>
            </html>
        """.trimIndent()
    }
}

private class RecordingNoteRepository(
    private val createDelayMs: Long = 0L,
) : NoteRepository {
    val createdContents = mutableListOf<String>()

    override fun observeFeed(): Flow<List<NoteEntity>> = emptyFlow()
    override fun observeAllNotes(): Flow<List<NoteEntity>> = emptyFlow()
    override fun observeSearchResults(filters: SearchFilters): Flow<List<NoteEntity>> = emptyFlow()
    override fun observeStatusHistory(noteId: Long): Flow<List<NoteStatusHistoryEntity>> = flowOf(emptyList())
    override fun observeNoteStats(): Flow<NoteStats> = flowOf(NoteStats())
    override fun observeSystemNotices(): Flow<String> = emptyFlow()
    override suspend fun getNote(noteId: Long): NoteEntity? = null

    override suspend fun createNote(
        content: String,
        topic: String,
        folderKey: String?,
        tags: List<String>,
        status: NoteStatus,
        horizon: NoteHorizon,
        knowledgeTrust: KnowledgeTrust,
        isArchived: Boolean,
        folderManuallyEdited: Boolean,
        topicManuallyEdited: Boolean,
        tagsManuallyEdited: Boolean,
        aiSummary: String,
        aiKeyPoints: List<String>,
        aiInsightContentHash: String,
        aiInsightUpdatedAt: Long,
    ): Long {
        if (createDelayMs > 0L) delay(createDelayMs)
        createdContents += content
        return createdContents.size.toLong()
    }

    override suspend fun updateNote(
        noteId: Long,
        content: String,
        topic: String,
        folderKey: String?,
        tags: List<String>,
        status: NoteStatus,
        horizon: NoteHorizon,
        knowledgeTrust: KnowledgeTrust,
        isArchived: Boolean,
        folderManuallyEdited: Boolean,
        topicManuallyEdited: Boolean,
        tagsManuallyEdited: Boolean,
    ) = Unit

    override suspend fun setArchived(noteId: Long, archived: Boolean) = Unit
    override suspend fun deleteNote(noteId: Long) = Unit
    override suspend fun ensureAiInsight(noteId: Long): Boolean = false
    override suspend fun classifyPendingFolders(): Int = 0
    override suspend fun retriggerFolderClassification(noteId: Long): FolderRefreshResult = FolderRefreshResult()
    override suspend fun retriggerTopicExtraction(noteId: Long): TopicRefreshResult = TopicRefreshResult()
    override suspend fun retriggerTagExtraction(noteId: Long): TagRefreshResult = TagRefreshResult()
    override suspend fun exportAllNotes(): ExportPayload = ExportPayload("", "")
    override suspend fun exportCloudBackupSnapshot(): CloudBackupSnapshot = CloudBackupSnapshot(emptyList())
    override suspend fun replaceAllFromCloudBackup(snapshot: CloudBackupSnapshot) =
        com.mindflow.app.data.model.ImportResult(0, 0)
    override suspend fun importNotes(markdown: String) =
        com.mindflow.app.data.model.ImportResult(0, 0)
    override suspend fun replaceAllNotes(markdown: String) =
        com.mindflow.app.data.model.ImportResult(0, 0)
}
