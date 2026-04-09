package com.mindflow.app.data.localmodel

internal object FlowOnDevicePromptFactory {
    fun mainline(contextSummary: String): String = buildPrompt(
        instruction = "你是 MindFlow 的本地知识维护员。你要把最近新进的材料和已有积累压成一个当前最值得看的综合判断，而不是摘要、待办或口号。",
        task = "只输出两行中文。第一行：一个当前综合判断。第二行：为什么现在值得先围绕它继续看。不要编号，不要解释流程，不要复述标题。",
        contextSummary = contextSummary,
    )

    fun settled(contextSummary: String): String = buildPrompt(
        instruction = "你在选择最近刚被吸收进个人知识层的一条结果。它必须像已经被写进知识层的结论、方法或判断，而不能像聊天式总结。",
        task = "只输出两行中文。第一行：最近吸收进知识层的一条结果。第二行：一句最硬的可信基础。不要编号，不要口号，不要复述当前综合判断。",
        contextSummary = contextSummary,
    )

    fun gap(contextSummary: String): String = buildPrompt(
        instruction = "你在寻找当前最值得厘清的一处张力。重点不是喊口号，而是指出知识层里哪里最该补、最该查、最该重新连接。",
        task = "只输出两行中文。第一行：当前最该厘清的一处张力。第二行：下一次应该摄入什么材料，或者补哪类来源。不要编号，不要维护黑话，不要复述当前综合判断和最近吸收。",
        contextSummary = contextSummary,
    )

    fun editorRecall(contextSummary: String): String = buildPrompt(
        instruction = "你在做端侧旧知识召回。目标不是总结草稿，而是指出这条新记录和哪条旧积累最应该接上。",
        task = "只输出两行中文。第一行：这条新记录最值得接上的旧知识或主线。第二行：一句很短的理由。不要编号，不要泛泛鼓励。",
        contextSummary = contextSummary,
    )

    private fun buildPrompt(
        instruction: String,
        task: String,
        contextSummary: String,
    ): String = buildString {
        appendLine("### 角色")
        appendLine(instruction)
        appendLine()
        appendLine("### 材料")
        appendLine(contextSummary.trim())
        appendLine()
        appendLine("### 输出要求")
        appendLine(task)
    }
}
