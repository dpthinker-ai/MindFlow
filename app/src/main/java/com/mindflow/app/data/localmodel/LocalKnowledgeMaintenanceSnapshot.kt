package com.mindflow.app.data.localmodel

data class LocalMaintainerCard(
    val line: String = "",
    val support: String = "",
    val anchorLabel: String = "",
    val threadKey: String = "",
    val noteId: Long? = null,
) {
    val hasContent: Boolean
        get() = line.isNotBlank()
}

data class LocalKnowledgeGraphPulse(
    val newSourceCount: Int = 0,
    val newNodeCount: Int = 0,
    val newEdgeCount: Int = 0,
    val activeHubLabel: String = "",
    val missingLinkLabel: String = "",
)

data class LocalKnowledgeMaintenanceSnapshot(
    val rootPath: String = "",
    val generatedAt: Long = 0L,
    val date: String = "",
    val recentAbsorption: LocalMaintainerCard = LocalMaintainerCard(),
    val currentJudgement: LocalMaintainerCard = LocalMaintainerCard(),
    val newConnection: LocalMaintainerCard = LocalMaintainerCard(),
    val openQuestion: LocalMaintainerCard = LocalMaintainerCard(),
    val graphPulse: LocalKnowledgeGraphPulse = LocalKnowledgeGraphPulse(),
    val activeDirectionCount: Int = 0,
    val updatedDirectionTitle: String = "",
) {
    val hasContent: Boolean
        get() = recentAbsorption.hasContent || currentJudgement.hasContent || newConnection.hasContent
}
