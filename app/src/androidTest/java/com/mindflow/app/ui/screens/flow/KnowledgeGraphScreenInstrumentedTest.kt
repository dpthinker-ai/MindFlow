package com.mindflow.app.ui.screens.flow

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiGraphMaturity
import com.mindflow.app.data.wiki.DirectionWikiGraphOverview
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationEdge
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationNode
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationSnapshot
import com.mindflow.app.data.wiki.DirectionWikiGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.theme.MindFlowTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeGraphScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun graphNodesExposeStableSemantics() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = graphSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(graphNodeTestTag("folder:work"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "中心",
                ),
            )
        assertThat(composeRule.onAllNodesWithTag(KnowledgeGraphInfoPanelTag).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun selectingNodeShowsRelationLabelAndInfoPanel() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = graphSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("folder:work")).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("folder:work")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("方法共享")
        assertThat(composeRule.onAllNodesWithText("方法共享").fetchSemanticsNodes().isNotEmpty()).isTrue()
    }

    @Test
    fun selectingHeatmapDateDoesNotChangeGraphSelection() {
        val today = LocalDate.now()
        val learningDate = today.minusDays(2)
        val workDate = today.minusDays(1)

        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = graphSnapshot(),
                    notes = listOf(
                        note(
                            id = 1,
                            date = learningDate,
                            topic = "学习笔记",
                            content = "补一条学习记录。",
                            tags = listOf("learning"),
                        ),
                        note(
                            id = 2,
                            date = workDate,
                            topic = "工作记录",
                            content = "补一条工作记录。",
                            folderKey = "work",
                        ),
                    ),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("tag:learning")).performClick()
        composeRule.onNodeWithTag(graphNodeTestTag("tag:learning")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("学习")

        composeRule.onNodeWithTag(heatmapDayTestTag(workDate)).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("tag:learning")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("学习")
    }

    private fun graphSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            directions = mapOf(
                "folder:work" to directionSummary("folder:work", "工作", "工作主线更清楚了。"),
                "tag:learning" to directionSummary("tag:learning", "学习", "学习方法在成形。"),
            ),
            graph = DirectionWikiGraphSnapshot(
                overview = DirectionWikiGraphOverview(
                    hubThreadKeys = listOf("folder:work"),
                    isolatedThreadKeys = emptyList(),
                ),
                presentation = DirectionWikiGraphPresentationSnapshot(
                    headline = "2 个主题 · 1 条关系",
                    nodes = listOf(
                        DirectionWikiGraphPresentationNode(
                            threadKey = "folder:work",
                            label = "工作",
                            summaryLine = "工作主线更清楚了。",
                            densityScore = 0.9,
                            maturity = DirectionWikiGraphMaturity.STABLE,
                            noteCount = 6,
                        ),
                        DirectionWikiGraphPresentationNode(
                            threadKey = "tag:learning",
                            label = "学习",
                            summaryLine = "学习方法在成形。",
                            densityScore = 0.55,
                            maturity = DirectionWikiGraphMaturity.STRENGTHENING,
                            noteCount = 3,
                        ),
                    ),
                    edges = listOf(
                        DirectionWikiGraphPresentationEdge(
                            fromThreadKey = "folder:work",
                            toThreadKey = "tag:learning",
                            strength = 4,
                            reasonLine = "方法共享。",
                        ),
                    ),
                ),
            ),
        )

    private fun directionSummary(
        threadKey: String,
        title: String,
        conclusionLine: String,
    ) = DirectionWikiDirectionSummary(
        threadKey = threadKey,
        slug = threadKey,
        title = title,
        stage = DirectionStage.FORMING,
        conclusionLine = conclusionLine,
        updatedAt = 1_000L,
    )

    private fun note(
        id: Long,
        date: LocalDate,
        topic: String,
        content: String,
        folderKey: String? = null,
        tags: List<String> = emptyList(),
    ) = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.RULE,
        folderKey = folderKey,
        folderSource = FolderSource.RULE,
        tags = tags,
        tagSource = TagSource.RULE,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = date.toEpochMillis(),
        updatedAt = date.toEpochMillis(),
    )

    private fun LocalDate.toEpochMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
