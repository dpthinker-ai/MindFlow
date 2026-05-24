package com.mindflow.app.data.ai

import java.util.UUID

data class CloudUsageNotificationBatch(
    val batchId: String,
    val message: String,
    val eventIds: List<String>,
    val notifiedAt: Long,
)

class CloudUsageNotificationAggregator(
    private val minDelayMs: Long = 5 * 60_000L,
    private val maxWindowMs: Long = 30 * 60_000L,
    private val dailyNotificationLimit: Int = 3,
) {
    private val pending = mutableListOf<AiUsageEvent>()
    private var firstPendingAt: Long = 0L
    private val sentByDay = mutableMapOf<Long, Int>()

    fun record(event: AiUsageEvent, now: Long = System.currentTimeMillis()): CloudUsageNotificationBatch? {
        if (event.triggerMode == AiTriggerMode.FOREGROUND_USER_ACTION) return null
        if (pending.isEmpty()) {
            firstPendingAt = now
        }
        pending += event
        return null
    }

    fun flushIfDue(now: Long = System.currentTimeMillis()): CloudUsageNotificationBatch? {
        if (pending.isEmpty()) return null
        val elapsed = now - firstPendingAt
        if (elapsed < minDelayMs && elapsed < maxWindowMs) return null
        val dayKey = now.floorDayKey()
        val sentCount = sentByDay[dayKey] ?: 0
        if (sentCount >= dailyNotificationLimit) {
            pending.clear()
            firstPendingAt = 0L
            return null
        }

        val events = pending.toList()
        pending.clear()
        firstPendingAt = 0L
        sentByDay[dayKey] = sentCount + 1
        val batchId = UUID.randomUUID().toString()
        return CloudUsageNotificationBatch(
            batchId = batchId,
            message = buildMessage(events),
            eventIds = events.map { it.eventId },
            notifiedAt = now,
        )
    }

    private fun buildMessage(events: List<AiUsageEvent>): String {
        val providerLabels = events.map { it.providerLabel }.distinct().joinToString("、")
        val taskLabels = events.map { it.taskType.notificationLabel() }.distinct().take(3).joinToString("、")
        return "MindFlow 已用 $providerLabels 完成 ${events.size} 项后台整理：$taskLabels。"
    }

    private fun AiTaskType.notificationLabel(): String = when (this) {
        AiTaskType.TEST_CONNECTION -> "连接测试"
        AiTaskType.EXTRACT_TOPIC -> "主题判断"
        AiTaskType.EXTRACT_TAGS -> "标签提取"
        AiTaskType.CLASSIFY_CATEGORY -> "分类判断"
        AiTaskType.SUMMARIZE_NOTE -> "洞察摘要"
        AiTaskType.GRAPH_EXTRACT_CONCEPTS,
        AiTaskType.GRAPH_CANONICALIZE_CONCEPTS,
        AiTaskType.GRAPH_GENERATE_RELATIONS,
        AiTaskType.GRAPH_GENERATE_SNAPSHOT -> "图谱整理"
        AiTaskType.POLISH_CONTENT,
        AiTaskType.POLISH_TITLE -> "编辑润色"
        AiTaskType.TRANSCRIBE_AUDIO,
        AiTaskType.TRANSLATE_AUDIO,
        AiTaskType.UNDERSTAND_IMAGE -> "本地媒体处理"
        AiTaskType.DAILY_BRIEF -> "今日线索"
        AiTaskType.NEXT_ACTION -> "下一步"
        AiTaskType.WEEKLY_REVIEW -> "周回看"
        AiTaskType.FUSION_SUGGESTION -> "碰撞建议"
        AiTaskType.FLOW_MAINLINE,
        AiTaskType.FLOW_SETTLED_KNOWLEDGE,
        AiTaskType.FLOW_BREAKTHROUGH_GAP -> "今天/回看整理"
        AiTaskType.THREAD_WORKSPACE,
        AiTaskType.THREAD_EXECUTION -> "方向执行"
        AiTaskType.RESEARCH_BRIEF,
        AiTaskType.RESEARCH_ACTION_SUMMARY,
        AiTaskType.EXTERNAL_RESEARCH -> "外部线索"
        AiTaskType.STALE_RECONNECT -> "旧记录重连"
        AiTaskType.REVIEW_CHAT_REPLY,
        AiTaskType.REVIEW_CHAT_QUERY_PLAN -> "回看问答"
    }

    private fun Long.floorDayKey(): Long = this / DAY_MS

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
