package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnResult
import com.mindflow.app.data.reviewchat.SavedReviewChatSession
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.navigation.ReviewChatSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            answerTurn = {
                ReviewChatTurnResult(
                    answer = "你在增长和定位之间反复摇摆。",
                    provider = ReviewChatProvider.CLOUD,
                    fallbackOccurred = false,
                    providerLine = "本次由云侧完成",
                    sessionSummary = "矛盾串联摘要",
                    titleSuggestion = "产品方向矛盾",
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
            answerTurn = {
                ReviewChatTurnResult(
                    answer = "你最近围绕发布节奏在收口。",
                    provider = ReviewChatProvider.ON_DEVICE,
                    fallbackOccurred = false,
                    providerLine = "本次由端侧完成",
                    sessionSummary = "发布节奏",
                    titleSuggestion = "发布节奏回看",
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
            answerTurn = {
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
    fun answerWithReferencedRecord_preservesNoteIdForOpenRecordAction() = runTest(dispatcher) {
        val viewModel = ReviewChatViewModel(
            seed = ReviewChatSeed(initialQuestion = "把 4 月 10 号那条完整记录给我"),
            answerTurn = {
                ReviewChatTurnResult(
                    answer = "这是那条记录的完整内容。",
                    provider = ReviewChatProvider.LOCAL_MEMORY,
                    fallbackOccurred = false,
                    providerLine = "本次由本地知识层完成",
                    sessionSummary = "完整记录",
                    titleSuggestion = "4 月 10 号记录",
                    referencedNoteId = 42L,
                )
            },
            savedConversationRepository = FakeSavedConversationRepository(),
        )

        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertThat(assistant.role).isEqualTo(ReviewChatMessageRole.ASSISTANT)
        assertThat(assistant.referencedNoteId).isEqualTo(42L)
    }

    private class FakeSavedConversationRepository : ReviewChatSavedConversationRepository {
        val savedSessions = mutableListOf<SavedReviewChatSession>()
        private val latestSummary = MutableStateFlow<SavedReviewChatSessionSummary?>(null)

        override suspend fun saveSession(
            title: String,
            messages: List<ReviewChatMessage>,
        ): Long {
            val sessionId = (savedSessions.size + 1).toLong()
            val session = SavedReviewChatSession(
                sessionId = sessionId,
                title = title,
                createdAt = messages.firstOrNull()?.createdAt ?: 0L,
                updatedAt = messages.lastOrNull()?.createdAt ?: 0L,
                messages = messages,
            )
            savedSessions += session
            latestSummary.value = SavedReviewChatSessionSummary(
                sessionId = sessionId,
                title = title,
                updatedAt = session.updatedAt,
                messageCount = messages.size,
                latestExcerpt = messages.lastOrNull()?.content.orEmpty(),
            )
            return sessionId
        }

        override suspend fun getSession(sessionId: Long): SavedReviewChatSession? =
            savedSessions.firstOrNull { it.sessionId == sessionId }

        override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> = latestSummary

        fun seedSession(session: SavedReviewChatSession) {
            savedSessions += session
            latestSummary.value = SavedReviewChatSessionSummary(
                sessionId = session.sessionId,
                title = session.title,
                updatedAt = session.updatedAt,
                messageCount = session.messages.size,
                latestExcerpt = session.messages.lastOrNull()?.content.orEmpty(),
            )
        }
    }
}
