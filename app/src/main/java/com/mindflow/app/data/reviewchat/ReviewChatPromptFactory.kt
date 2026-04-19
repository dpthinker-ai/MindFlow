package com.mindflow.app.data.reviewchat

object ReviewChatPromptFactory {
    fun cloud(packet: ReviewChatContextPacket): String = buildPrompt(packet)

    fun onDevice(packet: ReviewChatContextPacket): String = buildPrompt(packet)

    private fun buildPrompt(packet: ReviewChatContextPacket): String = buildString {
        appendLine("问题类型：${packet.intent.name}")
        appendLine("当前问题：${packet.question}")
        packet.sessionSummary.takeIf { it.isNotBlank() }?.let {
            appendLine("近期会话摘要：$it")
        }
        appendLine("LM Knowledge Base：")
        packet.knowledgeBaseSnippets.forEach(::appendLine)
        appendLine("LLM Wiki：")
        packet.wikiSnippets.forEach(::appendLine)
        appendLine("原始记录：")
        packet.rawNoteSnippets.forEach(::appendLine)
        appendLine("回答要求：优先基于上面的本地知识压缩和结构化沉淀回答，再用原始记录补充细节。不要假装看过不存在的材料。")
    }
}
