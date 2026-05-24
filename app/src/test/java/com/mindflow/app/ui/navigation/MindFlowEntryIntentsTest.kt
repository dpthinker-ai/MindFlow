package com.mindflow.app.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MindFlowEntryIntentsTest {
    @Test
    fun defaultImageCaptureSeed_createsImageSeedWithEditableImageContent() {
        val seed = MindFlowEntryIntents.defaultImageCaptureSeed()

        assertThat(seed.mode).isEqualTo(CaptureMode.IMAGE)
        assertThat(seed.initialTopic).isEqualTo("图片记录")
        assertThat(seed.initialContent).isEqualTo("图片：\n补充说明：")
        assertThat(seed.initialTags).containsExactly("图片")
    }

    @Test
    fun defaultVoiceCaptureSeed_waitsForManualRecordingStart() {
        val seed = MindFlowEntryIntents.defaultVoiceCaptureSeed()

        assertThat(seed.mode).isEqualTo(CaptureMode.VOICE)
        assertThat(seed.autoStartVoiceInput).isFalse()
        assertThat(seed.initialTags).containsExactly("语音")
    }
}
