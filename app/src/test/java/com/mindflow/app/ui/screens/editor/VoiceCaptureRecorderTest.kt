package com.mindflow.app.ui.screens.editor

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class VoiceCaptureRecorderTest {
    @Test
    fun wavHeader_usesGemmaFriendlyMono16kPcmFormat() {
        val header = wavHeader(pcmDataBytes = 32_000L)
        val littleEndian = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(String(header, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(littleEndian.getInt(4)).isEqualTo(36 + 32_000)
        assertThat(String(header, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(String(header, 12, 4, Charsets.US_ASCII)).isEqualTo("fmt ")
        assertThat(littleEndian.getShort(20).toInt()).isEqualTo(1)
        assertThat(littleEndian.getShort(22).toInt()).isEqualTo(VOICE_CAPTURE_CHANNEL_COUNT)
        assertThat(littleEndian.getInt(24)).isEqualTo(VOICE_CAPTURE_SAMPLE_RATE_HZ)
        assertThat(littleEndian.getShort(34).toInt()).isEqualTo(VOICE_CAPTURE_BITS_PER_SAMPLE)
        assertThat(String(header, 36, 4, Charsets.US_ASCII)).isEqualTo("data")
        assertThat(littleEndian.getInt(40)).isEqualTo(32_000)
    }
}
