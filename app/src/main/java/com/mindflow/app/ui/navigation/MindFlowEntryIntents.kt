package com.mindflow.app.ui.navigation

import android.content.Intent

data class CaptureSeed(
    val requestId: Long = System.currentTimeMillis(),
    val initialContent: String = "",
    val initialTopic: String = "",
)

sealed interface MindFlowLaunchRequest {
    val requestId: Long

    data class OpenCapture(val seed: CaptureSeed) : MindFlowLaunchRequest {
        override val requestId: Long = seed.requestId
    }

    data class OpenSearch(override val requestId: Long = System.currentTimeMillis()) : MindFlowLaunchRequest

    data class OpenFlow(override val requestId: Long = System.currentTimeMillis()) : MindFlowLaunchRequest
}

object MindFlowEntryIntents {
    const val ACTION_OPEN_CAPTURE = "com.mindflow.app.action.OPEN_CAPTURE"
    const val ACTION_OPEN_SEARCH = "com.mindflow.app.action.OPEN_SEARCH"
    const val ACTION_OPEN_FLOW = "com.mindflow.app.action.OPEN_FLOW"

    fun fromIntent(intent: Intent?): MindFlowLaunchRequest? {
        val safeIntent = intent ?: return null
        return when (safeIntent.action) {
            Intent.ACTION_SEND -> parseSharedText(safeIntent)
            Intent.ACTION_PROCESS_TEXT -> parseProcessText(safeIntent)
            ACTION_OPEN_CAPTURE -> MindFlowLaunchRequest.OpenCapture(CaptureSeed())
            ACTION_OPEN_SEARCH -> MindFlowLaunchRequest.OpenSearch()
            ACTION_OPEN_FLOW -> MindFlowLaunchRequest.OpenFlow()
            else -> null
        }
    }

    private fun parseSharedText(intent: Intent): MindFlowLaunchRequest? {
        if (intent.type?.startsWith("text/") != true) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (sharedText.isBlank() && sharedSubject.isBlank()) return null
        val topic = sharedSubject.takeIf { it.isNotBlank() && it != sharedText }
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                initialContent = sharedText,
                initialTopic = topic.orEmpty(),
            ),
        )
    }

    private fun parseProcessText(intent: Intent): MindFlowLaunchRequest? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                initialContent = text,
            ),
        )
    }
}
