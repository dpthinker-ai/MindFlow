package com.mindflow.app.ui.navigation

import android.content.Intent
import com.mindflow.app.data.model.KnowledgeTrust

enum class FlowFocus {
    TODAY,
    RECONNECT,
    REVIEW,
    DIRECTION,
    MAINLINE,
}

enum class CaptureMode {
    TEXT,
    VOICE,
    IMAGE,
    ARTICLE,
}

data class CaptureSeed(
    val requestId: Long = System.currentTimeMillis(),
    val mode: CaptureMode = CaptureMode.TEXT,
    val initialContent: String = "",
    val initialTopic: String = "",
    val initialFolderKey: String? = null,
    val initialTags: List<String> = emptyList(),
    val initialKnowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
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

    data class OpenFlow(
        val focus: FlowFocus? = null,
        override val requestId: Long = System.currentTimeMillis(),
    ) : MindFlowLaunchRequest

    data class OpenThread(
        val threadKey: String,
        override val requestId: Long = System.currentTimeMillis(),
    ) : MindFlowLaunchRequest
}

object MindFlowEntryIntents {
    const val ACTION_OPEN_CAPTURE = "com.mindflow.app.action.OPEN_CAPTURE"
    const val ACTION_OPEN_CAPTURE_VOICE = "com.mindflow.app.action.OPEN_CAPTURE_VOICE"
    const val ACTION_OPEN_CAPTURE_IMAGE = "com.mindflow.app.action.OPEN_CAPTURE_IMAGE"
    const val ACTION_OPEN_SEARCH = "com.mindflow.app.action.OPEN_SEARCH"
    const val ACTION_OPEN_FLOW = "com.mindflow.app.action.OPEN_FLOW"
    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_THREAD_KEY = "extra_thread_key"
    const val EXTRA_CAPTURE_CONTENT = "extra_capture_content"
    const val EXTRA_CAPTURE_TOPIC = "extra_capture_topic"
    const val EXTRA_CAPTURE_FOLDER = "extra_capture_folder"
    const val EXTRA_CAPTURE_TAGS = "extra_capture_tags"
    const val EXTRA_FLOW_FOCUS = "extra_flow_focus"

    fun fromIntent(intent: Intent?): MindFlowLaunchRequest? {
        val safeIntent = intent ?: return null
        return when (safeIntent.action) {
            Intent.ACTION_SEND -> parseSharedText(safeIntent)
            Intent.ACTION_PROCESS_TEXT -> parseProcessText(safeIntent)
            ACTION_OPEN_CAPTURE -> parseCaptureIntent(safeIntent, autoStartVoiceInput = false)
            ACTION_OPEN_CAPTURE_VOICE -> parseVoiceCaptureIntent(safeIntent)
            ACTION_OPEN_CAPTURE_IMAGE -> parseImageCaptureIntent(safeIntent)
            ACTION_OPEN_SEARCH -> MindFlowLaunchRequest.OpenSearch()
            ACTION_OPEN_FLOW -> parseFlowIntent(safeIntent)
            else -> null
        }
    }

    private fun parseFlowIntent(intent: Intent): MindFlowLaunchRequest {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val threadKey = intent.getStringExtra(EXTRA_THREAD_KEY).orEmpty()
        val focus = intent.getStringExtra(EXTRA_FLOW_FOCUS)
            ?.let { raw -> FlowFocus.entries.firstOrNull { it.name == raw } }
        return if (noteId > 0L) {
            MindFlowLaunchRequest.OpenNote(noteId = noteId)
        } else if (threadKey.isNotBlank()) {
            MindFlowLaunchRequest.OpenThread(threadKey = threadKey)
        } else {
            MindFlowLaunchRequest.OpenFlow(focus = focus)
        }
    }

    private fun parseCaptureIntent(
        intent: Intent,
        autoStartVoiceInput: Boolean,
    ): MindFlowLaunchRequest {
        val initialContent = intent.getStringExtra(EXTRA_CAPTURE_CONTENT).orEmpty()
        val initialTopic = intent.getStringExtra(EXTRA_CAPTURE_TOPIC).orEmpty()
        val initialTags = intent.getStringArrayListExtra(EXTRA_CAPTURE_TAGS)?.toList().orEmpty()
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                mode = inferCaptureMode(initialContent, initialTags, autoStartVoiceInput),
                initialContent = initialContent,
                initialTopic = initialTopic,
                initialFolderKey = intent.getStringExtra(EXTRA_CAPTURE_FOLDER)?.takeIf { it.isNotBlank() },
                initialTags = initialTags,
                autoStartVoiceInput = autoStartVoiceInput,
            ),
        )
    }

    private fun parseVoiceCaptureIntent(intent: Intent): MindFlowLaunchRequest {
        val initialContent = intent.getStringExtra(EXTRA_CAPTURE_CONTENT).orEmpty()
        val initialTopic = intent.getStringExtra(EXTRA_CAPTURE_TOPIC).orEmpty()
        val initialTags = intent.getStringArrayListExtra(EXTRA_CAPTURE_TAGS)?.toList().orEmpty()
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                mode = CaptureMode.VOICE,
                initialContent = initialContent,
                initialTopic = initialTopic,
                initialFolderKey = intent.getStringExtra(EXTRA_CAPTURE_FOLDER)?.takeIf { it.isNotBlank() },
                initialTags = initialTags,
                autoStartVoiceInput = false,
            ),
        )
    }

    private fun parseImageCaptureIntent(intent: Intent): MindFlowLaunchRequest {
        val initialContent = intent.getStringExtra(EXTRA_CAPTURE_CONTENT)
            ?.takeIf { it.isNotBlank() }
            ?: "图片：\n补充说明："
        val initialTopic = intent.getStringExtra(EXTRA_CAPTURE_TOPIC)
            ?.takeIf { it.isNotBlank() }
            ?: "图片记录"
        val initialTags = intent.getStringArrayListExtra(EXTRA_CAPTURE_TAGS)?.toList().orEmpty()
        return MindFlowLaunchRequest.OpenCapture(
            defaultImageCaptureSeed(
                initialContent = initialContent,
                initialTopic = initialTopic,
                initialFolderKey = intent.getStringExtra(EXTRA_CAPTURE_FOLDER)?.takeIf { it.isNotBlank() },
                initialTags = (initialTags + "图片").distinct(),
            ),
        )
    }

    internal fun defaultImageCaptureSeed(
        initialContent: String = "图片：\n补充说明：",
        initialTopic: String = "图片记录",
        initialFolderKey: String? = null,
        initialTags: List<String> = listOf("图片"),
    ): CaptureSeed = CaptureSeed(
        mode = CaptureMode.IMAGE,
        initialContent = initialContent,
        initialTopic = initialTopic,
        initialFolderKey = initialFolderKey,
        initialTags = initialTags,
        autoStartVoiceInput = false,
    )

    private fun parseSharedText(intent: Intent): MindFlowLaunchRequest? {
        if (intent.type?.startsWith("text/") != true) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (sharedText.isBlank() && sharedSubject.isBlank()) return null
        val topic = sharedSubject.takeIf { it.isNotBlank() && it != sharedText }
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                mode = inferCaptureMode(sharedText, emptyList(), autoStartVoiceInput = false),
                initialContent = sharedText,
                initialTopic = topic.orEmpty(),
                initialTags = if (containsUrl(sharedText)) listOf("文章") else emptyList(),
            ),
        )
    }

    private fun parseProcessText(intent: Intent): MindFlowLaunchRequest? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null
        return MindFlowLaunchRequest.OpenCapture(
            CaptureSeed(
                mode = inferCaptureMode(text, emptyList(), autoStartVoiceInput = false),
                initialContent = text,
                initialTags = if (containsUrl(text)) listOf("文章") else emptyList(),
            ),
        )
    }

    private fun inferCaptureMode(
        content: String,
        tags: List<String>,
        autoStartVoiceInput: Boolean,
    ): CaptureMode = when {
        autoStartVoiceInput -> CaptureMode.VOICE
        containsUrl(content) || tags.any { it == "文章" || it == "链接" } -> CaptureMode.ARTICLE
        else -> CaptureMode.TEXT
    }

    private fun containsUrl(text: String): Boolean =
        Regex("""https?://[^\s，。)）]+""").containsMatchIn(text)
}
