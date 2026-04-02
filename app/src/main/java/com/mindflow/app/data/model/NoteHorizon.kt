package com.mindflow.app.data.model

enum class NoteHorizon(
    val label: String,
    val windowLabel: String,
    val priority: Int,
) {
    SHORT(
        label = "短期",
        windowLabel = "一周内",
        priority = 3,
    ),
    MEDIUM(
        label = "中期",
        windowLabel = "两三周",
        priority = 2,
    ),
    LONG(
        label = "长期",
        windowLabel = "一个月以上",
        priority = 1,
    );

    companion object {
        fun inferFrom(
            content: String,
            topic: String = "",
        ): NoteHorizon {
            val sample = "$topic\n$content".lowercase()
            return when {
                sample.containsAny("今天", "这周", "本周", "一周", "尽快", "马上", "立刻", "明天") -> SHORT
                sample.containsAny("下周", "两周", "三周", "近期", "这个月", "本月", "过几周") -> MEDIUM
                sample.containsAny("长期", "一个月", "几个月", "今年", "慢慢", "以后") -> LONG
                else -> MEDIUM
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean =
            keywords.any { keyword -> contains(keyword) }
    }
}
