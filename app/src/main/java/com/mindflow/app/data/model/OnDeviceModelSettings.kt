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
    val lastDownloadedAt: Long = 0L,
    val lastMessage: String = "",
    val status: OnDeviceModelStatus = OnDeviceModelStatus.NOT_DOWNLOADED,
) {
    val isReady: Boolean
        get() = status == OnDeviceModelStatus.READY && localModelPath.isNotBlank()

    companion object {
        const val DEFAULT_MODEL_LABEL = "Gemma 4 E4B"
        const val DEFAULT_MODEL_DOWNLOAD_URL =
            "https://huggingface.co/huggingworld/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"
        const val DEFAULT_MODEL_SOURCE_URL = "https://huggingface.co/huggingworld/gemma-4-E4B-it-litert-lm"
    }
}
