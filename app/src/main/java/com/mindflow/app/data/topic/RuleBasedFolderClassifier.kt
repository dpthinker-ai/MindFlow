package com.mindflow.app.data.topic

import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.FolderSuggestion

class RuleBasedFolderClassifier {
    private val workKeywords = setOf(
        "工作", "会议", "需求", "客户", "团队", "汇报", "录音机", "信道", "ai 内存", "内存管理", "产品",
    )
    private val lifeKeywords = setOf(
        "生活", "家庭", "旅行", "读书", "学习", "教育", "朋友", "日常", "计划",
    )
    private val projectKeywords = setOf(
        "app", "应用", "项目", "开发", "版本", "功能", "上线", "分享图", "release", "bug", "仓库",
    )
    private val healthKeywords = setOf(
        "健康", "饮食", "睡眠", "体重", "体检", "营养", "血压", "情绪", "恢复",
        "跑步", "晨跑", "深蹲", "俯卧撑", "健身", "训练", "高抬腿", "拉伸", "弓步蹲", "慢跑",
    )

    fun classify(content: String): String? {
        val normalized = content.lowercase()
        return when {
            workKeywords.any { normalized.contains(it) } -> "work"
            healthKeywords.any { normalized.contains(it) } -> "health"
            projectKeywords.any { normalized.contains(it) } -> "project"
            lifeKeywords.any { normalized.contains(it) } -> "life"
            else -> null
        }
    }

    fun toSuggestion(content: String): FolderSuggestion =
        FolderSuggestion(
            folderKey = classify(content),
            source = FolderSource.RULE,
        )
}
