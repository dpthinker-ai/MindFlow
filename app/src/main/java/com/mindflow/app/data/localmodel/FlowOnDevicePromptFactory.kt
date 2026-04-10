package com.mindflow.app.data.localmodel

internal object FlowOnDevicePromptFactory {
    fun mainline(contextSummary: String): String = buildPrompt(
        instruction = "你是 MindFlow 的本地 llm-wiki 维护员。你现在不是在临时回答问题，而是在读取 raw sources、wiki 页面、index 和 log 后，给出一个已经被维护好的当前综合判断。",
        task = "只输出两行中文。第一行：一个当前综合判断，必须像会改变优先级的判断，而不是摘要、待办或口号。第二行：为什么现在该先围绕它继续推进。如果材料里有不同文件夹或项目，用户点“换一个”时必须换到不同项目，而不是同题改写。不要编号，不要解释流程，不要复述标题。",
        contextSummary = contextSummary,
    )

    fun settled(contextSummary: String): String = buildPrompt(
        instruction = "你在挑选最近刚被吸收进本地知识层的一条结果。它必须像已经写进 wiki 的结论、方法或判断，而不能像聊天式总结。",
        task = "只输出两行中文。第一行：最近吸收进知识层的一条结果。第二行：一句最硬的可信基础。优先选择不同文件夹或不同项目里真正成立的结果，不要继续围着当前主线改写。不要编号，不要口号，不要复述当前综合判断。",
        contextSummary = contextSummary,
    )

    fun gap(contextSummary: String): String = buildPrompt(
        instruction = "你在寻找本地知识层今天刚长出来的一条新连接，或者一处最该厘清的张力。重点不是喊口号，而是指出 wiki 里哪些旧点值得重新连起来。",
        task = "只输出两行中文。第一行：当前最值得试的一条新连接，或者最该厘清的一处张力。优先连接不同文件夹、不同项目、不同经验的旧积累。第二行：下一次应该摄入什么材料，或者补哪类来源。不要编号，不要维护黑话，不要复述当前综合判断和最近吸收。",
        contextSummary = contextSummary,
    )

    fun knowledgeShape(contextSummary: String): String = buildPrompt(
        instruction = "你在读取一套已经持续维护的本地知识层。你的任务不是总结几条笔记，而是描述当前知识到底长成什么样了。",
        task = "只输出两行中文。第一行：一句当前知识版图判断，说明它现在主要围绕什么母题或方向展开。第二行：一句说明它现在包含哪些关键点、对象或脉络。不要编号，不要口号，不要泛泛夸奖。",
        contextSummary = contextSummary,
    )

    fun openQuestion(contextSummary: String): String = buildPrompt(
        instruction = "你在挑选一个最值得继续追问的问题。这个问题应该来自知识层里的真实张力，而不是普通的待办事项。",
        task = "只输出两行中文。第一行：当前最值得继续追的问题。第二行：为什么它现在最值钱，或者补上它会打开什么。不要编号，不要喊口号。",
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
