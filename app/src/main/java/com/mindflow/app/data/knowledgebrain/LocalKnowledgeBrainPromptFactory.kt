package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.entity.NoteEntity

object LocalKnowledgeBrainPromptFactory {
    fun fragment(note: NoteEntity): String = buildString {
        appendLine("你在维护一个端侧本地知识层。")
        appendLine("任务：把下面这条记录压成一个 MemoryFragment。")
        appendLine("输出格式：")
        appendLine("fragmentSummary=<一句摘要>")
        appendLine("topicKey=<topic/...>")
        appendLine("questionKey=<question/...或空>")
        appendLine("salience=<0.0-1.0>")
        appendLine("记录标题：${note.topic}")
        appendLine("记录正文：${note.content}")
    }
}
