package com.mindflow.app.ui.navigation

data class KnowledgeMaintenanceSeedContext(
    val title: String,
    val threadKey: String,
    val stageLabel: String,
    val horizonLabel: String = "",
    val whyNow: String = "",
    val nextStep: String = "",
    val validationStep: String = "",
    val conclusionLine: String = "",
    val nextShiftLine: String = "",
    val groundingLine: String = "",
    val maintenanceLine: String = "",
    val maintenanceTargetLine: String = "",
    val maintenanceSourceLine: String = "",
    val maintenanceDimensionLine: String = "",
    val maintenanceFocusLine: String = "",
)

fun buildKnowledgeMaintenanceCaptureSeed(
    context: KnowledgeMaintenanceSeedContext,
): CaptureSeed {
    val cleanTitle = context.title.removePrefix("#").trim()
    val initialFolderKey = context.threadKey
        .takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.ifBlank { null }
    val initialTags = context.threadKey
        .takeIf { it.startsWith("tag:") }
        ?.removePrefix("tag:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()

    val maintenanceTopic = when {
        context.maintenanceTargetLine.contains("证据") -> "$cleanTitle · 补证据"
        context.maintenanceTargetLine.contains("问题") -> "$cleanTitle · 核心问题"
        context.maintenanceTargetLine.contains("方法") || context.maintenanceFocusLine.contains("方法对象") -> "$cleanTitle · 沉淀方法"
        context.maintenanceTargetLine.contains("实验") || context.maintenanceFocusLine.contains("实验对象") -> "$cleanTitle · 设计实验"
        context.maintenanceTargetLine.contains("结论") -> "$cleanTitle · 刷新结论"
        context.maintenanceTargetLine.contains("方向") -> "$cleanTitle · 最近进展"
        else -> "$cleanTitle · 补材料"
    }

    return CaptureSeed(
        initialTopic = maintenanceTopic,
        initialContent = buildString {
            appendLine("围绕「$cleanTitle」补一条知识层维护记录：")
            appendLine("- 当前阶段：${context.stageLabel}${context.horizonLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}")
            context.maintenanceLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 建议先补：$it")
            }
            context.maintenanceTargetLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 先维护：$it")
            }
            context.maintenanceSourceLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 先补来源：$it")
            }
            context.maintenanceDimensionLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 最薄弱：$it")
            }
            context.maintenanceFocusLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 优先对象：$it")
            }
            context.whyNow.takeIf { it.isNotBlank() }?.let {
                appendLine("- 为什么现在：$it")
            }
            context.conclusionLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前结论：$it")
            }
            context.groundingLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 证据基础：$it")
            }
            context.nextStep.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前最小动作：$it")
            }
            context.validationStep.takeIf { it.isNotBlank() }?.let {
                appendLine("- 当前验证动作：$it")
            }
            context.nextShiftLine.takeIf { it.isNotBlank() }?.let {
                appendLine("- 下一步承接：$it")
            }
            appendLine("- 这次补充的材料：")
            appendLine("- 它对当前判断的影响：")
            appendLine("- 补完之后，下一步怎么推进：")
        },
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
    )
}
