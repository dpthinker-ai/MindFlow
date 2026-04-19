package com.mindflow.app.ui.screens.reviewchat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatPlanner
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.SavedReviewChatSession
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.navigation.ReviewChatSeed
import com.mindflow.app.ui.theme.MindFlowTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ReviewChatScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun route_showsSeedQuestionAndAssistantReply() {
        composeRule.setContent {
            MindFlowTheme {
                ReviewChatRoute(
                    seed = ReviewChatSeed(initialQuestion = "把最近两周的矛盾串一下"),
                    planner = samplePlanner(),
                    savedConversationRepository = FakeSavedConversationRepository(),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("把最近两周的矛盾串一下").assertIsDisplayed()
        composeRule.onNodeWithText("你最近在增长和定位之间反复摇摆。").assertIsDisplayed()
        composeRule.onNodeWithText("本次由云侧完成").assertIsDisplayed()
    }

    private fun samplePlanner() = ReviewChatPlanner(
        loadNotes = { listOf(sampleNote()) },
        loadWeeklyReview = { WeeklyReviewState(lines = listOf("主线")) },
        loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
        loadWikiSnapshot = { DirectionWikiSnapshot() },
        resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
        isCloudConfigured = { true },
        isOnDeviceReady = { true },
        runCloud = { AiChatResult.Success("你最近在增长和定位之间反复摇摆。") },
        runOnDevice = { AiChatResult.Success("端侧回答") },
    )

    private fun sampleNote() = NoteEntity(
        id = 1L,
        content = "最近总在增长和定位之间来回摇摆。",
        topic = "产品方向",
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("产品"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    private class FakeSavedConversationRepository : ReviewChatSavedConversationRepository {
        private val latestSummary = MutableStateFlow<SavedReviewChatSessionSummary?>(null)

        override suspend fun saveSession(title: String, messages: List<ReviewChatMessage>): Long = 1L

        override suspend fun getSession(sessionId: Long): SavedReviewChatSession? = null

        override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> = latestSummary
    }
}
