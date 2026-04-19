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
        appendLine("原始记录：")
        packet.rawNoteSnippets.forEach(::appendLine)
        appendLine("已沉淀结构：")
        packet.structuredSnippets.forEach(::appendLine)
    }
}
