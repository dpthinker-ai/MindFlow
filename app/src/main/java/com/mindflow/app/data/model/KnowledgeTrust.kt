package com.mindflow.app.data.model

enum class KnowledgeTrust(
    val label: String,
) {
    NONE("未标记"),
    SIGNAL("外部线索"),
    HYPOTHESIS("待验证"),
    VERIFIED("已查证"),
    VALIDATED("已验证");
}
