package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatReferencedNote
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatSkillWebView
import com.mindflow.app.data.reviewchat.ReviewChatStructuredAnswer
import com.mindflow.app.data.reviewchat.ReviewChatStructuredSection
import com.mindflow.app.data.reviewchat.ReviewChatTurnEvent
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnResult
import com.mindflow.app.data.reviewchat.SavedReviewChatSession
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.navigation.ReviewChatSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
class ReviewChatViewModelTest {
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
    fun seedQuestion_sendsImmediatelyAndAppendsAssistantReply() = runTest(dispatcher) {
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "把最近的矛盾串一下"),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                    answer = "你在增长和定位之间反复摇摆。",
                    provider = ReviewChatProvider.CLOUD,
                    fallbackOccurred = false,
                    providerLine = "本次由云侧完成",
                    sessionSummary = "矛盾串联摘要",
                    titleSuggestion = "产品方向矛盾",
                        )
                    )
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.messages).hasSize(2)
        assertThat(state.messages.first().role).isEqualTo(ReviewChatMessageRole.USER)
        assertThat(state.messages.last().role).isEqualTo(ReviewChatMessageRole.ASSISTANT)
        assertThat(state.providerLine).isEqualTo("本次由云侧完成")
        assertThat(state.title).isEqualTo("产品方向矛盾")
    }

    @Test
    fun saveConversation_persistsMessagesAndTurnsReadOnly() = runTest(dispatcher) {
        val repository = FakeSavedConversationRepository()
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "我最近的主线是什么"),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                    answer = "你最近围绕发布节奏在收口。",
                    provider = ReviewChatProvider.ON_DEVICE,
                    fallbackOccurred = false,
                    providerLine = "本次由端侧完成",
                    sessionSummary = "发布节奏",
                    titleSuggestion = "发布节奏回看",
                        )
                    )
                )
            },
            savedConversationRepository = repository,
        )

        advanceUntilIdle()
        viewModel.saveConversation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isReadOnly).isTrue()
        assertThat(state.savedSessionId).isEqualTo(1L)
        assertThat(repository.savedSessions.single().title).isEqualTo("发布节奏回看")
    }

    @Test
    fun savedSeed_loadsSessionReadOnlyWithoutCallingPlanner() = runTest(dispatcher) {
        val repository = FakeSavedConversationRepository().apply {
            seedSession(
                SavedReviewChatSession(
                    sessionId = 7L,
                    title = "上次回看",
                    createdAt = 1_000L,
                    updatedAt = 2_000L,
                    messages = listOf(
                        ReviewChatMessage(
                            role = ReviewChatMessageRole.USER,
                            content = "之前的判断是什么",
                            createdAt = 1_000L,
                        ),
                        ReviewChatMessage(
                            role = ReviewChatMessageRole.ASSISTANT,
                            content = "核心判断是先收口再扩张。",
                            provider = ReviewChatProvider.CLOUD,
                            createdAt = 1_100L,
                        ),
                    ),
                ),
            )
        }
        var called = false
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(savedSessionId = 7L),
            answerTurnStream = {
                called = true
                error("should not run")
            },
            savedConversationRepository = repository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(called).isFalse()
        assertThat(state.isReadOnly).isTrue()
        assertThat(state.title).isEqualTo("上次回看")
        assertThat(state.messages).hasSize(2)
    }

    @Test
    fun emptySeed_restoresLatestWorkingSessionEditable() = runTest(dispatcher) {
        val repository = FakeSavedConversationRepository().apply {
            seedSession(
                SavedReviewChatSession(
                    sessionId = 9L,
                    title = "未完成聊天",
                    createdAt = 1_000L,
                    updatedAt = 2_000L,
                    messages = listOf(
                        ReviewChatMessage(
                            role = ReviewChatMessageRole.USER,
                            content = "继续刚才的问题",
                            createdAt = 1_000L,
                        ),
                    ),
                    draft = "还没发出去的一句",
                    isArchived = false,
                ),
            )
        }

        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(),
            answerTurnStream = { error("should not run") },
            savedConversationRepository = repository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isReadOnly).isFalse()
        assertThat(state.savedSessionId).isEqualTo(9L)
        assertThat(state.title).isEqualTo("继续刚才的问题")
        assertThat(state.messages.single().content).isEqualTo("继续刚才的问题")
        assertThat(state.draft).isEqualTo("还没发出去的一句")
    }

    @Test
    fun sendDraft_replacesStaleWorkingTitleWithCurrentQuestion() = runTest(dispatcher) {
        val repository = FakeSavedConversationRepository().apply {
            seedSession(
                SavedReviewChatSession(
                    sessionId = 11L,
                    title = "纸临时标题",
                    createdAt = 1_000L,
                    updatedAt = 2_000L,
                    messages = listOf(
                        ReviewChatMessage(
                            role = ReviewChatMessageRole.USER,
                            content = "旧的问题",
                            createdAt = 1_000L,
                        ),
                    ),
                    isArchived = false,
                ),
            )
        }
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                            answer = "新的回答",
                            provider = ReviewChatProvider.CLOUD,
                            fallbackOccurred = false,
                            providerLine = "本次由云侧完成",
                            sessionSummary = "新的回答",
                            titleSuggestion = "新的问题",
                        )
                    )
                )
            },
            savedConversationRepository = repository,
        )

        advanceUntilIdle()
        viewModel.onDraftChange("新的问题是什么")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title).isEqualTo("新的问题")
        assertThat(repository.workingSessions.single().title).isEqualTo("新的问题")
    }

    @Test
    fun draftChange_cachesWorkingSession() = runTest(dispatcher) {
        val repository = FakeSavedConversationRepository()
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(),
            answerTurnStream = { error("should not run") },
            savedConversationRepository = repository,
        )

        viewModel.onDraftChange("先写一半")
        advanceUntilIdle()

        val working = repository.workingSessions.single()
        assertThat(working.draft).isEqualTo("先写一半")
        assertThat(working.isArchived).isFalse()
        assertThat(viewModel.uiState.value.savedSessionId).isEqualTo(working.sessionId)
    }

    @Test
    fun answerWithReferencedRecord_preservesNoteIdForOpenRecordAction() = runTest(dispatcher) {
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "把 4 月 10 号那条完整记录给我"),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                            answer = "这是那条记录的完整内容。",
                            provider = ReviewChatProvider.CLOUD,
                            fallbackOccurred = false,
                            providerLine = "本次由云侧完成",
                            sessionSummary = "完整记录",
                            titleSuggestion = "4 月 10 号记录",
                            referencedNoteId = 42L,
                            referencedNotes = listOf(
                                ReviewChatReferencedNote(
                                    noteId = 42L,
                                    title = "4 月 10 号记录",
                                    dateLabel = "2026-04-10",
                                )
                            ),
                        )
                    )
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertThat(assistant.role).isEqualTo(ReviewChatMessageRole.ASSISTANT)
        assertThat(assistant.referencedNoteId).isEqualTo(42L)
        assertThat(assistant.referencedNotes.single().noteId).isEqualTo(42L)
    }

    @Test
    fun sendDraft_passesStableSessionIdToPlanner() = runTest(dispatcher) {
        val requests = mutableListOf<ReviewChatTurnRequest>()
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(requestId = 12345L),
            answerTurnStream = { request ->
                requests += request
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                            answer = "收到",
                            provider = ReviewChatProvider.ON_DEVICE,
                            fallbackOccurred = false,
                            providerLine = "本次由端侧完成",
                            sessionSummary = "收到",
                            titleSuggestion = "对话",
                        )
                    )
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        viewModel.onDraftChange("先问一个问题")
        viewModel.sendDraft()
        advanceUntilIdle()
        viewModel.onDraftChange("再追问一次")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(requests).hasSize(2)
        assertThat(requests[0].sessionId).isEqualTo("12345")
        assertThat(requests[1].sessionId).isEqualTo("12345")
    }

    @Test
    fun sendDraft_streamingAnswerShowsPartialThenCommitsFinalAssistantMessage() = runTest(dispatcher) {
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(),
            answerTurnStream = {
                flow {
                    emit(
                        ReviewChatTurnEvent.Partial(
                            content = "第一段",
                            provider = ReviewChatProvider.ON_DEVICE,
                            providerLine = "本次由端侧完成",
                        )
                    )
                    emit(
                        ReviewChatTurnEvent.Partial(
                            content = "第一段第二段",
                            provider = ReviewChatProvider.ON_DEVICE,
                            providerLine = "本次由端侧完成",
                        )
                    )
                    emit(
                        ReviewChatTurnEvent.Complete(
                            ReviewChatTurnResult(
                                answer = "第一段第二段",
                                provider = ReviewChatProvider.ON_DEVICE,
                                fallbackOccurred = false,
                                providerLine = "本次由端侧完成",
                                sessionSummary = "流式结果",
                                titleSuggestion = "流式对话",
                            )
                        )
                    )
                }
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        viewModel.onDraftChange("先来一条流式问题")
        viewModel.sendDraft()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.streamingAnswer).isEmpty()
        assertThat(state.messages).hasSize(2)
        assertThat(state.messages.last().content).isEqualTo("第一段第二段")
        assertThat(state.providerLine).isEqualTo("本次由端侧完成")
    }

    @Test
    fun sendDraft_statusEventShowsGenerationStatusUntilFinalAnswer() = runTest(dispatcher) {
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(),
            answerTurnStream = {
                flow {
                    emit(
                        ReviewChatTurnEvent.Status(
                            message = "正在加载端侧模型并准备推理…",
                            provider = ReviewChatProvider.ON_DEVICE,
                            providerLine = "本次由端侧完成",
                        )
                    )
                    emit(
                        ReviewChatTurnEvent.Complete(
                            ReviewChatTurnResult(
                                answer = "端侧回答",
                                provider = ReviewChatProvider.ON_DEVICE,
                                fallbackOccurred = false,
                                providerLine = "本次由端侧完成",
                                sessionSummary = "端侧回答",
                                titleSuggestion = "端侧",
                            )
                        )
                    )
                }
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        viewModel.onDraftChange("端侧问题")
        viewModel.sendDraft()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.generationStatus).isEmpty()
        assertThat(state.messages.last().content).isEqualTo("端侧回答")
        assertThat(state.providerLine).isEqualTo("本次由端侧完成")
    }

    @Test
    fun completeTurn_preservesStructuredAnswerOnAssistantMessage() = runTest(dispatcher) {
        val structured = ReviewChatStructuredAnswer(
            sections = listOf(
                ReviewChatStructuredSection(
                    title = "答复",
                    body = listOf("本周末主要记录了 3 类信息。"),
                    items = emptyList(),
                ),
                ReviewChatStructuredSection(
                    title = "类别",
                    body = emptyList(),
                    items = listOf("产品设计：启动页、图标、名称"),
                ),
            ),
        )
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "看一下本周末记录了哪些信息，都有哪些类别"),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                            answer = "【答复】本周末主要记录了 3 类信息。【类别】- 产品设计：启动页、图标、名称",
                            structuredAnswer = structured,
                            provider = ReviewChatProvider.CLOUD,
                            fallbackOccurred = false,
                            providerLine = "本次由云侧完成",
                            sessionSummary = "分类结果",
                            titleSuggestion = "周末分类",
                        )
                    )
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertThat(assistant.structuredAnswer).isEqualTo(structured)
    }

    @Test
    fun completeTurn_preservesSkillWebViewOnAssistantMessage() = runTest(dispatcher) {
        val skillWebView = ReviewChatSkillWebView(
            url = "file:///android_asset/skills/history-query/assets/result-card.html?matched=3",
            iframe = false,
            aspectRatio = 1.333f,
        )
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "今天记录了什么"),
            answerTurnStream = {
                flowOf(
                    ReviewChatTurnEvent.Complete(
                        ReviewChatTurnResult(
                            answer = "今天命中 3 条记录。",
                            provider = ReviewChatProvider.CLOUD,
                            fallbackOccurred = false,
                            providerLine = "本次由云侧完成",
                            sessionSummary = "今天记录",
                            titleSuggestion = "今天记录",
                            skillWebView = skillWebView,
                        )
                    )
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertThat(assistant.skillWebView).isEqualTo(skillWebView)
    }

    private class FakeSavedConversationRepository : ReviewChatSavedConversationRepository {
        val savedSessions = mutableListOf<SavedReviewChatSession>()
        private val latestSummary = MutableStateFlow<SavedReviewChatSessionSummary?>(null)
        private val savedSummaries = MutableStateFlow<List<SavedReviewChatSessionSummary>>(emptyList())

        val workingSessions: List<SavedReviewChatSession>
            get() = savedSessions.filter { !it.isArchived }

        override suspend fun saveSession(
            sessionId: Long?,
            title: String,
            messages: List<ReviewChatMessage>,
        ): Long {
            val resolvedSessionId = sessionId ?: (savedSessions.size + 1).toLong()
            val session = SavedReviewChatSession(
                sessionId = resolvedSessionId,
                title = title,
                createdAt = messages.firstOrNull()?.createdAt ?: 0L,
                updatedAt = messages.lastOrNull()?.createdAt ?: 0L,
                messages = messages,
                isArchived = true,
            )
            savedSessions.removeAll { it.sessionId == resolvedSessionId }
            savedSessions += session
            refreshSavedSummaries()
            return resolvedSessionId
        }

        override suspend fun cacheWorkingSession(
            sessionId: Long?,
            title: String,
            messages: List<ReviewChatMessage>,
            draft: String,
        ): Long {
            val resolvedSessionId = sessionId ?: (savedSessions.size + 1).toLong()
            val session = SavedReviewChatSession(
                sessionId = resolvedSessionId,
                title = title,
                createdAt = messages.firstOrNull()?.createdAt ?: 0L,
                updatedAt = System.currentTimeMillis(),
                messages = messages,
                draft = draft,
                isArchived = false,
            )
            savedSessions.removeAll { it.sessionId == resolvedSessionId }
            savedSessions += session
            return resolvedSessionId
        }

        override suspend fun getLatestWorkingSession(): SavedReviewChatSession? =
            savedSessions.filter { !it.isArchived }.maxByOrNull { it.updatedAt }

        override suspend fun getSession(sessionId: Long): SavedReviewChatSession? =
            savedSessions.firstOrNull { it.sessionId == sessionId }

        override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> = latestSummary

        override fun observeSavedSessionSummaries(): Flow<List<SavedReviewChatSessionSummary>> = savedSummaries

        override suspend fun deleteSessions(sessionIds: List<Long>) {
            savedSessions.removeAll { it.sessionId in sessionIds }
            refreshSavedSummaries()
        }

        fun seedSession(session: SavedReviewChatSession) {
            savedSessions += session
            refreshSavedSummaries()
        }

        private fun refreshSavedSummaries() {
            val summaries = savedSessions
                .filter { it.isArchived }
                .sortedWith(compareByDescending<SavedReviewChatSession> { it.updatedAt }.thenByDescending { it.sessionId })
                .map { session ->
                    SavedReviewChatSessionSummary(
                        sessionId = session.sessionId,
                        title = session.title,
                        updatedAt = session.updatedAt,
                        messageCount = session.messages.size,
                        latestExcerpt = session.messages.lastOrNull()?.content.orEmpty(),
                        isArchived = true,
                    )
                }
            savedSummaries.value = summaries
            latestSummary.value = summaries.firstOrNull()
        }
    }
}
