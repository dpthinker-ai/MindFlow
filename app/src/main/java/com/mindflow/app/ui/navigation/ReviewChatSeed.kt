package com.mindflow.app.ui.navigation

data class ReviewChatSeed(
    val requestId: Long = System.currentTimeMillis(),
    val initialQuestion: String = "",
    val savedSessionId: Long? = null,
)
