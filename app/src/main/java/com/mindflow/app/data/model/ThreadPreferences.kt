package com.mindflow.app.data.model

data class ThreadPreferences(
    val followedThreadKeys: Set<String> = emptySet(),
) {
    fun isFollowed(threadKey: String): Boolean = threadKey in followedThreadKeys
}
