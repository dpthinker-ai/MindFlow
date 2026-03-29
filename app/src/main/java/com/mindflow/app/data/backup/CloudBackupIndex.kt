package com.mindflow.app.data.backup

data class CloudBackupIndex(
    val targetKey: String = "",
    val noteHashes: Map<String, String> = emptyMap(),
)
