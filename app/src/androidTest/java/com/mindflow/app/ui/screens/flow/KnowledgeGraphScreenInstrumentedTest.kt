package com.mindflow.app.ui.screens.flow

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import com.mindflow.app.data.wiki.ConceptGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.theme.MindFlowTheme
import com.mindflow.app.util.TimeFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

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
        composeRule.onNodeWithTag(graphNodeTestTag("work")).assert(hasNoClickAction())
        composeRule.onNodeWithTag(graphNodeTestTag("work")).assert(hasNoButtonRole())
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
    fun clickingBackAcrossDirectedRelationUsesReverseRelationCopy() {
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
        composeRule.onNodeWithText("被支持").assertIsDisplayed()

        composeRule.onNodeWithTag(graphNodeTestTag("work")).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("work")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("被支持")
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("把工作拆回可执行练习。")
    }

    @Test
    fun bidirectionalPairsPreferCurrentCenterDirectionForDisplayedCopy() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = bidirectionalPairSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithText("支持").assertIsDisplayed()
        composeRule.onAllNodesWithText("被推进").assertCountEquals(0)

        composeRule.onNodeWithTag(graphNodeTestTag("learning")).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("learning")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("支持")
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("从工作切到学习时应该展示这条关系。")

        composeRule.onNodeWithTag(graphNodeTestTag("work")).performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("work")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("推进")
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("从学习切回工作时应该展示这条关系。")
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
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("直接点图里的相关想法继续往下看。")
        composeRule.onNodeWithTag(graphNodeTestTag("other")).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(graphNodeTestTag("other")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("旁支概念")
        composeRule.onNodeWithTag(graphNodeTestTag("bridge")).assertIsDisplayed()
    }

    @Test
    fun isolatedCenterRevealsSwitchTargetsOneBatchAtATime() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = isolatedSparseSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("solo")).assertIsSelected()
        composeRule.onNodeWithTag(graphNodeTestTag("node-6")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("node-7")).assertCountEquals(0)

        composeRule.onNodeWithText("再展开 8 个相关知识点").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("node-12")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("node-13")).assertCountEquals(0)
        composeRule.onNodeWithText("再展开 2 个相关知识点").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("node-13")).assertIsDisplayed()
        composeRule.onNodeWithTag(graphNodeTestTag("node-14")).assertIsDisplayed()
        composeRule.onAllNodesWithText("再展开 2 个相关知识点").assertCountEquals(0)
    }

    @Test
    fun explicitExpansionRevealsNeighborsOneBatchAtATime() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = expansionSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("node-6")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("node-7")).assertCountEquals(0)

        composeRule.onNodeWithText("展开其余 8 个关联知识点").performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("node-12")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("node-13")).assertCountEquals(0)
        composeRule.onNodeWithText("展开其余 2 个关联知识点").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("node-13")).assertIsDisplayed()
        composeRule.onNodeWithTag(graphNodeTestTag("node-14")).assertIsDisplayed()
        composeRule.onAllNodesWithText("展开其余 2 个关联知识点").assertCountEquals(0)
    }

    @Test
    fun connectedCenterDoesNotRenderDetachedExpandButtonForHiddenNeighbors() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = expansionSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("center")).assertIsSelected()
        composeRule.onAllNodesWithText("还有 8 个关联知识点").assertCountEquals(0)
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag)
            .assertTextContains("更淡的点表示下一层关联")
    }

    @Test
    fun connectedCenterDoesNotRenderDetachedSwitchList() {
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = disconnectedComponentsSnapshot(),
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("core")).assertIsSelected()
        composeRule.onNodeWithTag(graphNodeTestTag("neighbor")).assertIsDisplayed()
        composeRule.onAllNodesWithText("换个中心点继续看").assertCountEquals(0)
        composeRule.onAllNodesWithText("从这些知识点继续看").assertCountEquals(0)
        composeRule.onAllNodesWithText("切到别的点继续看").assertCountEquals(0)
        composeRule.onAllNodesWithTag(graphNodeTestTag("island-hub")).assertCountEquals(0)
    }

    @Test
    fun refreshingSnapshotPreservesCurrentCenterButResetsExpansionState() {
        var snapshot by mutableStateOf(refreshScopedExpansionSnapshot(generatedAt = 1L))
        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = snapshot,
                    notes = emptyList(),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag(graphNodeTestTag("branch")).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(graphNodeTestTag("branch")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("刷新前用于验证展开状态。")
        composeRule.onNodeWithTag(graphNodeTestTag("leaf-6")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("leaf-7")).assertCountEquals(0)

        composeRule.onNodeWithText("展开其余 8 个关联知识点").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(graphNodeTestTag("leaf-12")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("leaf-13")).assertCountEquals(0)

        composeRule.runOnUiThread {
            snapshot = refreshScopedExpansionSnapshot(
                generatedAt = 2L,
                branchSummary = "刷新后应该保留当前中心，但回到首批关联知识点。",
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(graphNodeTestTag("branch")).assertIsSelected()
        composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains(
            "刷新后应该保留当前中心，但回到首批关联知识点。",
        )
        composeRule.onNodeWithTag(graphNodeTestTag("leaf-6")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(graphNodeTestTag("leaf-7")).assertCountEquals(0)
        composeRule.onAllNodesWithTag(graphNodeTestTag("leaf-12")).assertCountEquals(0)
        composeRule.onNodeWithText("展开其余 8 个关联知识点").assertIsDisplayed()
    }

    @Test
    fun heatmapNoteCardKeepsTimestampReadableForLongTitle() {
        val updatedAt = Instant.parse("2026-04-18T08:51:00Z").toEpochMilli()
        val note = heatmapNote(
            id = 42L,
            topic = "快速记录里边这个很多乱七八糟的东西，你描述请描述给他优化一下那个。",
            updatedAt = updatedAt,
        )

        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = connectedSnapshot(),
                    notes = listOf(note),
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithText("记录热度").assertIsDisplayed()
        composeRule.onNodeWithText(note.topic, substring = true).assertIsDisplayed()
        composeRule
            .onNodeWithTag(graphActivityTimestampTestTag(note.id))
            .assertIsDisplayed()
            .assertTextContains(TimeFormatter.compact(updatedAt))
    }

    @Test
    fun heatmapNoteCardPlacesTimestampOnIndependentMetaRowInsteadOfRightAlignedTail() {
        val updatedAt = Instant.parse("2026-04-18T08:51:00Z").toEpochMilli()
        val note = heatmapNote(
            id = 88L,
            topic = "这是一个会把标题撑到两行的记录热度卡片标题，用来验证时间戳不再被挤到右侧尾巴。",
            updatedAt = updatedAt,
        )

        composeRule.setContent {
            MindFlowTheme {
                KnowledgeGraphScreen(
                    snapshot = connectedSnapshot(),
                    notes = listOf(note),
                    onOpenNote = {},
                )
            }
        }

        val titleBounds = composeRule
            .onNodeWithText(note.topic, substring = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val timestampBounds = composeRule
            .onNodeWithTag(graphActivityTimestampTestTag(note.id))
            .fetchSemanticsNode()
            .boundsInRoot

        assertThat(timestampBounds.top).isGreaterThan(titleBounds.bottom)
        assertThat(timestampBounds.left).isWithin(12f).of(titleBounds.left)
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

    private fun bidirectionalPairSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "work",
                nodes = listOf(
                    conceptNode("work", "工作系统", "把任务和知识组织成稳定节奏。", 0.95, 1_000),
                    conceptNode("learning", "学习循环", "把反馈变成可重复练习。", 0.8, 900),
                ),
                edges = listOf(
                    conceptEdge(
                        fromConceptId = "work",
                        toConceptId = "learning",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "从工作切到学习时应该展示这条关系。",
                        confidence = 0.58,
                    ),
                    conceptEdge(
                        fromConceptId = "learning",
                        toConceptId = "work",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "从学习切回工作时应该展示这条关系。",
                        confidence = 0.97,
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
                    conceptNode("other", "旁支概念", "通向另一个知识簇。", 0.8, 950),
                    conceptNode("bridge", "桥接概念", "把这个知识簇连起来。", 0.6, 850),
                ),
                edges = listOf(
                    conceptEdge(
                        fromConceptId = "other",
                        toConceptId = "bridge",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "旁支概念会继续推进到桥接概念。",
                        confidence = 0.82,
                    ),
                ),
            ),
        )

    private fun isolatedSparseSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "solo",
                nodes = buildList {
                    add(conceptNode("solo", "独立概念", "用于验证逐批切换。", 0.2, 1_000))
                    repeat(14) { index ->
                        add(
                            conceptNode(
                                conceptId = "node-${index + 1}",
                                label = "候选${index + 1}",
                                summary = "第 ${index + 1} 个可切换知识点。",
                                hotnessScore = 0.98 - (index * 0.02),
                                updatedAt = 980L - index,
                            ),
                        )
                    }
                },
                edges = listOf(
                    conceptEdge(
                        fromConceptId = "node-1",
                        toConceptId = "node-2",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "候选1和候选2形成第一条稀疏关系。",
                        confidence = 0.9,
                    ),
                    conceptEdge(
                        fromConceptId = "node-1",
                        toConceptId = "node-3",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "候选1继续推进到候选3。",
                        confidence = 0.85,
                    ),
                    conceptEdge(
                        fromConceptId = "node-4",
                        toConceptId = "node-5",
                        relationType = ConceptGraphRelationType.REFERENCES,
                        reasonLine = "候选4和候选5之间只有一条轻量引用。",
                        confidence = 0.7,
                    ),
                ),
            ),
        )

    private fun expansionSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "center",
                nodes = buildList {
                    add(conceptNode("center", "中心知识点", "用于验证逐批展开。", 1.0, 1_000))
                    repeat(14) { index ->
                        add(
                            conceptNode(
                                conceptId = "node-${index + 1}",
                                label = "关联${index + 1}",
                                summary = "第 ${index + 1} 个关联知识点。",
                                hotnessScore = 0.95 - (index * 0.03),
                                updatedAt = 950L - index,
                            ),
                        )
                    }
                },
                edges = List(14) { index ->
                    conceptEdge(
                        fromConceptId = "center",
                        toConceptId = "node-${index + 1}",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "中心知识点连接到关联${index + 1}。",
                        confidence = 0.99 - (index * 0.01),
                    )
                },
            ),
        )

    private fun disconnectedComponentsSnapshot(): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "core",
                nodes = listOf(
                    conceptNode("core", "中心概念", "当前中心仍然有一跳邻居。", 0.95, 1_000),
                    conceptNode("neighbor", "邻居概念", "和中心概念直接相连。", 0.9, 990),
                    conceptNode("island-hub", "孤岛入口", "另一个知识簇的入口。", 0.88, 980),
                    conceptNode("island-leaf", "孤岛叶子", "只有切换过去后才能继续展开。", 0.8, 970),
                ),
                edges = listOf(
                    conceptEdge(
                        fromConceptId = "core",
                        toConceptId = "neighbor",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "中心概念会先展示自己的直接邻居。",
                        confidence = 0.94,
                    ),
                    conceptEdge(
                        fromConceptId = "island-hub",
                        toConceptId = "island-leaf",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "切换过去后能继续浏览另一个知识簇。",
                        confidence = 0.91,
                    ),
                ),
            ),
        )

    private fun refreshScopedExpansionSnapshot(
        generatedAt: Long,
        branchSummary: String = "刷新前用于验证展开状态。",
    ): DirectionWikiSnapshot =
        DirectionWikiSnapshot(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "entry",
                generatedAt = generatedAt,
                nodes = buildList {
                    add(conceptNode("entry", "入口概念", "用于切换到展开中心。", 0.3, 1_000))
                    add(conceptNode("branch", "刷新中心", branchSummary, 0.95, 990))
                    repeat(14) { index ->
                        add(
                            conceptNode(
                                conceptId = "leaf-${index + 1}",
                                label = "分支${index + 1}",
                                summary = "刷新中心的第 ${index + 1} 个关联知识点。",
                                hotnessScore = 0.9 - (index * 0.02),
                                updatedAt = 980L - index,
                            ),
                        )
                    }
                },
                edges = List(14) { index ->
                    conceptEdge(
                        fromConceptId = "branch",
                        toConceptId = "leaf-${index + 1}",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "刷新中心连接到分支${index + 1}。",
                        confidence = 0.99 - (index * 0.01),
                    )
                },
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

    private fun heatmapNote(
        id: Long,
        topic: String,
        updatedAt: Long,
    ) = NoteEntity(
        id = id,
        content = "$topic\n继续描述一下具体内容。",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = null,
        folderSource = FolderSource.RULE,
        tags = emptyList(),
        tagSource = TagSource.RULE,
        status = NoteStatus.DONE,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = updatedAt - 60_000,
        updatedAt = updatedAt,
    )

    private fun hasNoClickAction(): SemanticsMatcher =
        SemanticsMatcher("has no click action") { semanticsNode ->
            !semanticsNode.config.contains(SemanticsActions.OnClick)
        }

    private fun hasNoButtonRole(): SemanticsMatcher =
        SemanticsMatcher("does not expose button role") { semanticsNode ->
            !semanticsNode.config.contains(SemanticsProperties.Role) ||
                semanticsNode.config[SemanticsProperties.Role] != Role.Button
        }
}
