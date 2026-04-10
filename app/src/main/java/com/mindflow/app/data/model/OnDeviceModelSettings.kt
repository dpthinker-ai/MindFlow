package com.mindflow.app.data.model

enum class OnDeviceModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR,
}

data class OnDeviceModelSettings(
    val modelLabel: String = DEFAULT_MODEL_LABEL,
    val modelDownloadUrl: String = DEFAULT_MODEL_DOWNLOAD_URL,
    val preferOnDevice: Boolean = false,
    val localModelPath: String = "",
    val downloadedBytes: Long = 0L,
    val downloadTargetBytes: Long = DEFAULT_MODEL_SIZE_BYTES,
    val lastDownloadedAt: Long = 0L,
    val lastMessage: String = "",
    val status: OnDeviceModelStatus = OnDeviceModelStatus.NOT_DOWNLOADED,
) {
    val isReady: Boolean
        get() = status == OnDeviceModelStatus.READY && localModelPath.isNotBlank()

    val hasPartialDownload: Boolean
        get() = !isReady && downloadedBytes > 0L

    companion object {
        const val DEFAULT_MODEL_LABEL = "Gemma 4 E4B"
        private const val DEFAULT_MODEL_ID = "litert-community/gemma-4-E4B-it-litert-lm"
        private const val DEFAULT_MODEL_FILE = "gemma-4-E4B-it.litertlm"
        private const val DEFAULT_MODEL_COMMIT = "9695417f248178c63a9f318c6e0c56cb917cb837"
        const val DEFAULT_MODEL_SIZE_BYTES = 3_654_467_584L
        const val DEFAULT_MODEL_DOWNLOAD_URL =
            "https://huggingface.co/$DEFAULT_MODEL_ID/resolve/$DEFAULT_MODEL_COMMIT/$DEFAULT_MODEL_FILE?download=true"
        const val DEFAULT_MODEL_SOURCE_URL = "https://huggingface.co/$DEFAULT_MODEL_ID"

        private val gemma4E4bAliases = setOf(
            "google/gemma-4-E4B-it",
            "google/gemma-4-e4b-it",
            DEFAULT_MODEL_ID,
            DEFAULT_MODEL_ID.lowercase(),
            "gemma-4-e4b",
            "gemma4-e4b",
            "gemma4-e4b-it",
            DEFAULT_MODEL_SOURCE_URL,
            DEFAULT_MODEL_SOURCE_URL.removePrefix("https://"),
            DEFAULT_MODEL_SOURCE_URL.removePrefix("http://"),
        )

        fun normalizeDownloadUrl(raw: String): String {
            val candidate = raw.trim()
            if (candidate.isBlank()) return DEFAULT_MODEL_DOWNLOAD_URL

            val normalized = candidate.removeSuffix("/").trim()
            val lower = normalized.lowercase()
            if (normalized in gemma4E4bAliases || lower in gemma4E4bAliases.map { it.lowercase() }) {
                return DEFAULT_MODEL_DOWNLOAD_URL
            }

            if (lower.contains("gemma-4-e4b") && !lower.startsWith("http://") && !lower.startsWith("https://")) {
                return DEFAULT_MODEL_DOWNLOAD_URL
            }

            if (lower.startsWith("huggingface.co/")) {
                return normalizeDownloadUrl("https://$normalized")
            }

            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                if (lower == DEFAULT_MODEL_SOURCE_URL.lowercase()) {
                    return DEFAULT_MODEL_DOWNLOAD_URL
                }
                if (lower.contains("huggingface.co/$DEFAULT_MODEL_ID".lowercase()) &&
                    !lower.contains("/resolve/")
                ) {
                    return DEFAULT_MODEL_DOWNLOAD_URL
                }
                return normalized
            }

            return normalized
        }
    }
}
