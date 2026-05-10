package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import org.junit.Test

class WebViewGraphContractTest {
    @Test
    fun `viewport payload marks center node and keeps only visible neighbors`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "center",
                label = "概念A",
                summary = "测试中心点。",
            ),
            neighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "neighbor",
                        label = "概念B",
                        summary = "测试邻接点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "center",
                        toConceptId = "neighbor",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.8,
                    ),
                    relationWord = "支持",
                ),
            ),
        )

        val payload = viewport.toWebPayload()

        assertThat(payload.centerNodeId).isEqualTo("center")
        assertThat(payload.nodes.map { it.id }).containsExactly("center", "neighbor")
        assertThat(payload.nodes.first { it.id == "center" }.isCenter).isTrue()
        assertThat(payload.nodes.first { it.id == "center" }.displayLabel).isEqualTo("概念A")
        assertThat(payload.edges.single().source).isEqualTo("center")
        assertThat(payload.edges.single().target).isEqualTo("neighbor")
    }


    @Test
    fun `viewport payload assigns center cluster positions and label priority`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "center",
                label = "中心知识点",
                summary = "测试中心点。",
            ),
            neighbors = listOf(
                "n1" to "知识点1",
                "n2" to "知识点2",
                "n3" to "长期知识点3",
                "n4" to "长期知识点4",
                "n5" to "知识点5",
                "n6" to "知识点6",
            ).map { (id, label) ->
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = id,
                        label = label,
                        summary = "测试邻接点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "center",
                        toConceptId = id,
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.7,
                    ),
                    relationWord = "关联",
                )
            },
        )

        val payload = viewport.toWebPayload()
        val center = payload.nodes.first { it.id == "center" }
        val neighbors = payload.nodes.filterNot { it.id == "center" }
        val emphasisById = neighbors.associateBy({ it.id }, { it.emphasis })
        val labelById = neighbors.associateBy({ it.id }, { it.displayLabel })
        val positionById = neighbors.associateBy({ it.id }, { it.xFraction to it.yFraction })
        val colorById = neighbors.associateBy({ it.id }, { it.accentColor })

        assertThat(center.xFraction).isWithin(0.001).of(0.5)
        assertThat(center.yFraction).isWithin(0.001).of(0.5)
        assertThat(neighbors).isNotEmpty()
        assertThat(emphasisById["n1"]).isEqualTo(2)
        assertThat(emphasisById["n2"]).isEqualTo(2)
        assertThat(emphasisById["n3"]).isEqualTo(1)
        assertThat(emphasisById["n4"]).isEqualTo(0)
        assertThat(emphasisById["n6"]).isEqualTo(0)
        assertThat(labelById["n1"]).isEqualTo("知识点1")
        assertThat(labelById["n2"]).isEqualTo("知识点2")
        assertThat(labelById["n3"]).isEqualTo("长期知识")
        assertThat(labelById["n4"]).isEmpty()
        assertThat(labelById["n5"]).isEmpty()
        assertThat(labelById["n6"]).isEmpty()
        assertThat(positionById["n1"]).isEqualTo(0.51 to 0.2)
        assertThat(positionById["n2"]).isEqualTo(0.24 to 0.38)
        assertThat(positionById["n3"]).isEqualTo(0.76 to 0.37)
        assertThat(positionById["n4"]).isEqualTo(0.34 to 0.7)
        assertThat(positionById["n5"]).isEqualTo(0.67 to 0.76)
        assertThat(positionById["n6"]).isEqualTo(0.18 to 0.56)
        assertThat(colorById["n1"]).isNotEqualTo(colorById["n2"])
        assertThat(colorById["n2"]).isNotEqualTo(colorById["n3"])
        assertThat(colorById["n3"]).isNotEqualTo(colorById["n4"])
        assertThat(center.accentColor).isNotEqualTo(colorById["n1"])
        neighbors.forEach { neighbor ->
            assertThat(neighbor.xFraction).isAtLeast(0.12)
            assertThat(neighbor.xFraction).isAtMost(0.88)
            assertThat(neighbor.yFraction).isAtLeast(0.12)
            assertThat(neighbor.yFraction).isAtMost(0.92)
        }
    }

    @Test
    fun `isolated viewport uses switchable nodes as suggested graph targets`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "center",
                label = "中心知识点",
                summary = "测试中心点。",
            ),
            switchableNodes = listOf(
                ConceptGraphNode(
                    conceptId = "switch-1",
                    label = "切换知识点1",
                    summary = "测试切换点。",
                ),
                ConceptGraphNode(
                    conceptId = "switch-2",
                    label = "切换知识点2",
                    summary = "测试切换点。",
                ),
            ),
        )

        val payload = viewport.toWebPayload()

        assertThat(payload.nodes.map { it.id }).containsExactly("center", "switch-1", "switch-2")
        assertThat(payload.nodes.first { it.id == "switch-1" }.isSuggested).isTrue()
        assertThat(payload.nodes.first { it.id == "switch-2" }.isSuggested).isTrue()
        assertThat(payload.edges).hasSize(2)
        assertThat(payload.edges.all { it.isSuggested }).isTrue()
        assertThat(payload.edges.map { it.target }).containsExactly("switch-1", "switch-2")
    }

    @Test
    fun `connected viewport includes suggested preview neighbors as dashed graph targets`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "center",
                label = "中心知识点",
                summary = "测试中心点。",
            ),
            neighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "near",
                        label = "直接邻居",
                        summary = "测试邻接点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "center",
                        toConceptId = "near",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.8,
                    ),
                    relationWord = "支持",
                ),
            ),
            suggestedNeighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "suggested",
                        label = "下一层邻居",
                        summary = "测试下一层点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "center",
                        toConceptId = "suggested",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        confidence = 0.92,
                    ),
                    relationWord = "推进",
                ),
            ),
        )

        val payload = viewport.toWebPayload()

        assertThat(payload.nodes.map { it.id }).containsExactly("center", "near", "suggested")
        assertThat(payload.nodes.first { it.id == "near" }.isSuggested).isFalse()
        assertThat(payload.nodes.first { it.id == "suggested" }.isSuggested).isTrue()
        assertThat(payload.edges).hasSize(2)
        assertThat(payload.edges.first { it.target == "near" }.isSuggested).isFalse()
        assertThat(payload.edges.first { it.target == "suggested" }.isSuggested).isTrue()
    }

    @Test
    fun `previous center is promoted into an explicit return node`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "target",
                label = "目标节点",
                summary = "测试中心点。",
            ),
            neighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "near",
                        label = "直接邻居",
                        summary = "测试邻接点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "target",
                        toConceptId = "near",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.8,
                    ),
                    relationWord = "支持",
                ),
            ),
            suggestedNeighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "origin",
                        label = "原中心节点",
                        summary = "测试返回点。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "origin",
                        toConceptId = "target",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.32,
                    ),
                    relationWord = "支持",
                ),
            ),
            returnNodeId = "origin",
        )

        val payload = viewport.toWebPayload()
        val returnNode = payload.nodes.first { it.id == "origin" }

        assertThat(returnNode.isSuggested).isTrue()
        assertThat(returnNode.isReturnNode).isTrue()
        assertThat(returnNode.emphasis).isEqualTo(2)
        assertThat(returnNode.displayLabel).isEqualTo("原中心节点")
        assertThat(payload.nodes.map { it.id })
            .containsExactly("target", "near", "origin")
            .inOrder()
    }

    @Test
    fun `bridge parser accepts nodeClick and rejects malformed events`() {
        val click = parseGraphBridgeEvent("""{"type":"nodeClick","conceptId":"neighbor"}""")
        val malformed = parseGraphBridgeEvent("""{}""")

        assertThat(click).isEqualTo(GraphBridgeEvent.NodeClick("neighbor"))
        assertThat(malformed).isEqualTo(GraphBridgeEvent.Invalid("missing_or_invalid_type"))
    }

    @Test
    fun `native graph hit test reaches the top neighbor`() {
        val payload = WebGraphPayload(
            centerNodeId = "center",
            nodes = listOf(
                WebGraphNode(
                    id = "center",
                    label = "数据采集",
                    displayLabel = "数据采集",
                    accentColor = "#158CFF",
                    isCenter = true,
                    isSuggested = false,
                    isReturnNode = false,
                    emphasis = 3,
                    xFraction = 0.5,
                    yFraction = 0.5,
                ),
                WebGraphNode(
                    id = "douyin",
                    label = "抖音",
                    displayLabel = "抖音",
                    accentColor = "#45B7FF",
                    isCenter = false,
                    isSuggested = false,
                    isReturnNode = false,
                    emphasis = 2,
                    xFraction = 0.51,
                    yFraction = 0.2,
                ),
            ),
            edges = listOf(
                WebGraphEdge(
                    id = "center->douyin",
                    source = "center",
                    target = "douyin",
                    relationType = "parallel",
                    confidence = 0.8,
                    isSuggested = false,
                ),
            ),
        )

        val hitNodeId = resolveWebGraphTapNodeId(
            payload = payload,
            tapX = 453f,
            tapY = 78f,
            viewportWidth = 890f,
            viewportHeight = 389f,
            density = 2.7083333f,
        )

        assertThat(hitNodeId).isEqualTo("douyin")
    }
}
