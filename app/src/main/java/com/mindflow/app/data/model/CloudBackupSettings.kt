package com.mindflow.app.data.model

data class CloudBackupSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val username: String = "",
    val password: String = "",
    val remoteDir: String = DEFAULT_REMOTE_DIR,
    val autoBackupEnabled: Boolean = false,
    val lastBackupAt: Long = 0L,
    val lastBackupError: String = "",
) {
    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && normalizedBaseUrl.isNotBlank()

    val normalizedBaseUrl: String
        get() = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }

    val normalizedRemoteDir: String
        get() = remoteDir.trim().trim('/').ifBlank { DEFAULT_REMOTE_DIR }

    companion object {
        const val DEFAULT_BASE_URL = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_REMOTE_DIR = "MindFlow"
        const val DEFAULT_FILE_NAME = "mindflow-latest.md"
    }
}
