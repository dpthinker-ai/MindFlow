package com.mindflow.app.ui.navigation

import android.content.Intent

data class CaptureSeed(
    val requestId: Long = System.currentTimeMillis(),
    val initialContent: String = "",
    val initialTopic: String = "",
    val autoStartVoiceInput: Boolean = false,
)

sealed interface MindFlowLaunchRequest {
    val requestId: Long

    data class OpenCapture(val seed: CaptureSeed) : MindFlowLaunchRequest {
        override val requestId: Long = seed.requestId
    }

    data class OpenNote(
        val noteId: Long,
        override val requestId: Long = System.currentTimeMillis(),
    ) : MindFlowLaunchRequest

    data class OpenSearch(override val requestId: Long = System.currentTimeMillis()) : MindFlowLaunchRequest

    data class OpenFlow(override val requestId: Long = System.currentTimeMillis()) : MindFlowLaunchRequest

    data class OpenThread(
        val threadKey: String,
        override val requestId: Long = System.currentTimeMillis(),
    ) : MindFlowLaunchRequest
}

object MindFlowEntryIntents {
    const val ACTION_OPEN_CAPTURE = "com.mindflow.app.action.OPEN_CAPTURE"
    const val ACTION_OPEN_CAPTURE_VOICE = "com.mindflow.app.action.OPEN_CAPTURE_VOICE"
    const val ACTION_OPEN_SEARCH = "com.mindflow.app.action.OPEN_SEARCH"
    const val ACTION_OPEN_FLOW = "com.mindflow.app.action.OPEN_FLOW"
    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_THREAD_KEY = "extra_thread_key"
    const val EXTRA_CAPTURE_CONTENT = "extra_capture_content"
    const val EXTRA_CAPTURE_TOPIC = "extra_capture_topic"

    fun fromIntent(intent: Intent?): MindFlowLaunchRequest? {
        val safeIntent = intent ?: return null
        return when (safeIntent.action) {
            Intent.ACTION_SEND -> parseSharedText(safeIntent)
            Intent.ACTION_PROCESS_TEXT -> parseProcessText(safeIntent)
            ACTION_OPEN_CAPTURE -> parseCaptureIntent(safeIntent, autoStartVoiceInput = false)
            ACTION_OPEN_CAPTURE_VOICE -> parseCaptureIntent(safeIntent, autoStartVoiceInput = true)
            ACTION_OPEN_SEARCH -> MindFlowLaunchRequest.OpenSearch()
            ACTION_OPEN_FLOW -> parseFlowIntent(safeIntent)
            else -> null
        }
    }

    private fun parseFlowIntent(intent: Intent): MindFlowLaunchRequest {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val threadKey = intent.getStringExtra(EXTRA_THREAD_KEY).orEmpty()
        return if (noteId > 0L) {
            MindFlowLaunchRequest.OpenNote(noteId = noteId)
        } else if (threadKey.isNotBlank()) {
            MindFlowLaunchRequest.OpenThread(threadKey = threadKey)
        } else {
            MindFlowLaunchRequest.OpenFlow()
        }
    }

    private fun parseCaptureIntent(
        intent: Intent,
        autoStartVoiceInput: Boolean,
    ): MindFlowLaunchRequest =
        MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                initialContent = intent.getStringExtra(EXTRA_CAPTURE_CONTENT).orEmpty(),
                initialTopic = intent.getStringExtra(EXTRA_CAPTURE_TOPIC).orEmpty(),
                autoStartVoiceInput = autoStartVoiceInput,
            ),
        )

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
