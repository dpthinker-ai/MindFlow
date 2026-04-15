package com.mindflow.app.ui.screens.flow

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import com.mindflow.app.data.wiki.ConceptGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeGraphScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultCenterNodeUsesStableSemantics() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = connectedSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithText("知识图谱").assertIsDisplayed()
        composeRule.onNodeWithTag(graphNodeTestTag("work")).assertIsSelected()
        composeRule.onNodeWithTag(graphNodeTestTag("work")).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "中心节点",
            ),
        )
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("工作系统")
    }

    @Test
    fun clickingNeighborRecentersGraphAndShowsRelationExplanation() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = connectedSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("learning")).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("learning")).assertIsSelected()
        composeRule.onNodeWithTag(graphNodeTestTag("learning")).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "中心节点",
            ),
        )
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("支持")
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("把工作拆回可执行练习。")
        composeRule.onNodeWithTag(graphNodeTestTag("work")).assertIsDisplayed()
    }

    @Test
    fun isolatedCenterShowsNotConnectedYetEmptyState() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = isolatedCenterSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("solo")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("独立概念")
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("这个知识点还没有连接起来。")
        composeRule.onNodeWithText("这个知识点还没有连接起来。").assertIsDisplayed()
    }

    private fun connectedSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "work",
                nodes = listOf(
                    conceptNode("work", "工作系统", "把任务和知识组织成稳定节奏。", 0.95, 1_000),
                    conceptNode("learning", "学习循环", "把反馈变成可重复练习。", 0.8, 900),
                    conceptNode("writing", "写作表达", "把理解压缩成可复用输出。", 0.7, 800),
                ),
                edges = listOf(
                    conceptEdge(
                        fromConceptId = "work",
                        toConceptId = "learning",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "把工作拆回可执行练习。",
                        confidence = 0.92,
                    ),
                    conceptEdge(
                        fromConceptId = "work",
                        toConceptId = "writing",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "写下来能推进工作复盘。",
                        confidence = 0.78,
                    ),
                ),
            ),
        )

    private fun isolatedCenterSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "solo",
                nodes = listOf(
                    conceptNode("solo", "独立概念", "还没有任何稳定关系。", 0.9, 1_000),
                    conceptNode("other", "旁支概念", "暂时没有被连接。", 0.4, 800),
                ),
                edges = emptyList(),
            ),
        )

    private fun conceptNode(
        conceptId: String,
        label: String,
        summary: String,
        hotnessScore: Double,
        updatedAt: Long,
    ) = ConceptGraphNode(
        conceptId = conceptId,
        label = label,
        summary = summary,
        hotnessScore = hotnessScore,
        updatedAt = updatedAt,
    )

    private fun conceptEdge(
        fromConceptId: String,
        toConceptId: String,
        relationType: ConceptGraphRelationType,
        reasonLine: String,
        confidence: Double,
    ) = ConceptGraphEdge(
        fromConceptId = fromConceptId,
        toConceptId = toConceptId,
        relationType = relationType,
        reasonLine = reasonLine,
        confidence = confidence,
    )
}
