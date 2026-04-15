package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage

data class DirectionWikiDirectionSummary(
    val threadKey: String,
    val slug: String,
    val title: String,
    val stage: DirectionStage = DirectionStage.FORMING,
    val assetSummary: String = "",
    val conclusionLine: String = "",
    val nextShiftLine: String = "",
    val groundingLine: String = "",
    val trustLine: String = "",
    val knowledgeObjectLine: String = "",
    val healthLine: String = "",
    val maintenanceLine: String = "",
    val maintenanceTargetLine: String = "",
    val maintenanceSourceLine: String = "",
    val maintenanceDimensionLine: String = "",
    val maintenanceFocusLine: String = "",
    val signalPoints: List<String> = emptyList(),
    val hypothesisPoints: List<String> = emptyList(),
    val verifiedPoints: List<String> = emptyList(),
    val validatedPoints: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val lintIssues: List<String> = emptyList(),
    val continuityLine: String = "",
    val trajectoryLine: String = "",
    val stageHistorySummary: String = "",
    val snapshotStageLine: String = "",
    val snapshotCadenceLine: String = "",
    val updatedAt: Long = 0L,
)

enum class ConceptGraphRelationType(
    val wireName: String,
) {
    SUPPORTS("supports"),
    ADVANCES("advances"),
    PARALLEL("parallel"),
    REFERENCES("references"),
    CONTRASTS("contrasts"),
    ;

    companion object {
        fun fromWireName(raw: String): ConceptGraphRelationType? {
            val normalized = raw.trim().lowercase()
            if (normalized.isBlank()) return null
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}

data class ConceptGraphCandidate(
    val conceptId: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val summary: String = "",
    val hotnessScore: Double = 0.0,
    val updatedAt: Long = 0L,
    val sourceIds: List<String> = emptyList(),
)

data class ConceptGraphNode(
    val conceptId: String,
    val label: String,
    val aliases: List<String> = emptyList(),
    val summary: String = "",
    val hotnessScore: Double = 0.0,
    val updatedAt: Long = 0L,
    val sourceIds: List<String> = emptyList(),
)

data class ConceptGraphEdge(
    val fromConceptId: String,
    val toConceptId: String,
    val relationType: ConceptGraphRelationType = ConceptGraphRelationType.REFERENCES,
    val reasonLine: String = "",
    val supportIds: List<String> = emptyList(),
    val confidence: Double = 0.0,
)

data class ConceptGraphSnapshot(
    val version: Int = 1,
    val defaultCenterNodeId: String = "",
    val nodes: List<ConceptGraphNode> = emptyList(),
    val edges: List<ConceptGraphEdge> = emptyList(),
    val source: String = "rule",
    val generatedAt: Long = 0L,
)

enum class KnowledgeLayerSearchType(
    val label: String,
) {
    DIRECTION("方向"),
    CONCEPT("概念"),
    QUESTION("问题"),
    METHOD("方法"),
    EXPERIMENT("实验"),
    CONCLUSION("结论"),
    EVIDENCE("证据"),
}

data class KnowledgeLayerSearchItem(
    val id: String,
    val type: KnowledgeLayerSearchType,
    val title: String,
    val summary: String = "",
    val supportLine: String = "",
    val trustLabel: String = "",
    val threadKey: String = "",
    val noteId: Long? = null,
    val updatedAt: Long = 0L,
)

data class DirectionWikiSnapshot(
    val rootPath: String = "",
    val lastGeneratedAt: Long = 0L,
    val directions: Map<String, DirectionWikiDirectionSummary> = emptyMap(),
    val knowledgeItems: List<KnowledgeLayerSearchItem> = emptyList(),
    val conceptGraph: ConceptGraphSnapshot = ConceptGraphSnapshot(),
)

data class DirectionWikiRefreshResult(
    val generatedDirectionCount: Int,
    val generatedAt: Long,
)
