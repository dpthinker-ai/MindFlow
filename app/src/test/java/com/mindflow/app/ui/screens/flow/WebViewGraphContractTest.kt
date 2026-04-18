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
        assertThat(payload.edges.single().source).isEqualTo("center")
        assertThat(payload.edges.single().target).isEqualTo("neighbor")
    }


    @Test
    fun `viewport payload assigns centered stable positions across both sides`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "center",
                label = "中心知识点",
                summary = "测试中心点。",
            ),
            neighbors = listOf(
                "n1" to "知识点1",
                "n2" to "知识点2",
                "n3" to "知识点3",
                "n4" to "知识点4",
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

        assertThat(center.xFraction).isWithin(0.001).of(0.5)
        assertThat(center.yFraction).isWithin(0.001).of(0.52)
        assertThat(neighbors).isNotEmpty()
        assertThat(neighbors.any { it.xFraction < center.xFraction }).isTrue()
        assertThat(neighbors.any { it.xFraction > center.xFraction }).isTrue()
        neighbors.forEach { neighbor ->
            assertThat(neighbor.xFraction).isAtLeast(0.12)
            assertThat(neighbor.xFraction).isAtMost(0.88)
            assertThat(neighbor.yFraction).isAtLeast(0.12)
            assertThat(neighbor.yFraction).isAtMost(0.88)
        }
    }

    @Test
    fun `bridge parser accepts nodeClick and rejects malformed events`() {
        val click = parseGraphBridgeEvent("""{"type":"nodeClick","conceptId":"neighbor"}""")
        val malformed = parseGraphBridgeEvent("""{}""")

        assertThat(click).isEqualTo(GraphBridgeEvent.NodeClick("neighbor"))
        assertThat(malformed).isEqualTo(GraphBridgeEvent.Invalid("missing_or_invalid_type"))
    }
}
