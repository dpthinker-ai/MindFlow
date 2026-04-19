package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.localmodel.LocalMaintainerCard
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatPlannerTest {
    @Test
    fun classifyReviewChatIntent_prioritizesSynthesisLanguage() {
        assertThat(classifyReviewChatIntent("把工作和副项目里的共同主题串一下"))
            .isEqualTo(ReviewChatIntent.SYNTHESIZE)
        assertThat(classifyReviewChatIntent("为什么这个方向现在更值得继续推"))
            .isEqualTo(ReviewChatIntent.DISCUSS)
        assertThat(classifyReviewChatIntent("我之前什么时候提过这个问题"))
            .isEqualTo(ReviewChatIntent.RECALL)
    }

    @Test
    fun buildReviewChatContextPacket_includesRawAndStructuredInputsButCapsLength() {
        val packet = buildReviewChatContextPacket(
            question = "把最近两周的矛盾串一下",
            intent = ReviewChatIntent.SYNTHESIZE,
            notes = List(12) { index -> sampleNote(index.toLong() + 1L, "主题$index", "正文$index") },
            weeklyReview = WeeklyReviewState(lines = listOf("主线", "推进", "重启", "串联")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收", support = "吸收依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向",
                        conclusionLine = "一个关键结论",
                    ),
                ),
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "关键结论",
                        summary = "已经被沉淀下来的判断",
                    ),
                ),
            ),
            sessionSummary = "上一轮在讨论方向冲突",
        )

        assertThat(packet.rawNoteSnippets).hasSize(6)
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("当前判断")
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("关键结论")
    }

    @Test
    fun answer_automaticModePrefersCloudThenFallsBackToOnDevice() = runTest {
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "主题", "一条正文")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("主线")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { AiChatResult.Failure(AiFailureReason.NETWORK, "网络失败") },
            runOnDevice = { AiChatResult.Success("端侧补位回答") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把最近的问题串起来",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.ON_DEVICE)
        assertThat(result.fallbackOccurred).isTrue()
        assertThat(result.answer).contains("端侧补位回答")
    }

    private fun sampleNote(id: Long, topic: String, content: String): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("产品"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
