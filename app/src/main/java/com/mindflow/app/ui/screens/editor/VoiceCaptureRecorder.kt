package com.mindflow.app.ui.screens.editor

import android.media.AudioFormat
import android.media.AudioRecord
import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

internal class VoiceCaptureRecorder(
    private val context: Context,
) {
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    @Volatile private var recording = false
    @Volatile private var paused = false
    @Volatile private var amplitudeLevel = 0f

    @Suppress("MissingPermission")
    fun start(): File {
        release()
        val directory = File(context.filesDir, "captures/voice").apply { mkdirs() }
        val file = File(directory, "voice-${System.currentTimeMillis()}.wav")
        val minBufferSize = AudioRecord.getMinBufferSize(
            VOICE_CAPTURE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSize > 0) { "当前设备无法初始化语音录音缓冲区" }
        val bufferSize = max(minBufferSize, VOICE_CAPTURE_SAMPLE_RATE_HZ / 4)
        val nextRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            VOICE_CAPTURE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(nextRecorder.state == AudioRecord.STATE_INITIALIZED) { "当前设备无法初始化语音录音器" }

        recorder = nextRecorder
        outputFile = file
        recording = true
        paused = false
        amplitudeLevel = 0f
        nextRecorder.startRecording()
        recordingThread = Thread({
            writeWavRecording(nextRecorder, file, bufferSize)
        }, "MindFlowVoiceRecorder").apply {
            isDaemon = true
            start()
        }
        return file
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun maxAmplitudeLevel(): Float =
        amplitudeLevel

    fun stop(): File? {
        val file = outputFile
        val current = recorder ?: return file
        recording = false
        runCatching { current.stop() }
        runCatching { recordingThread?.join(1_500L) }
        release()
        return file?.takeIf { it.exists() && it.length() > WAV_HEADER_BYTES }
    }

    fun release() {
        recording = false
        recorder?.let { current ->
            runCatching { current.stop() }
        }
        runCatching { recordingThread?.join(1_000L) }
        recorder?.let { current ->
            runCatching { current.release() }
        }
        recorder = null
        recordingThread = null
        outputFile = null
        paused = false
        amplitudeLevel = 0f
    }

    private fun writeWavRecording(audioRecord: AudioRecord, file: File, bufferSize: Int) {
        var pcmBytesWritten = 0L
        runCatching {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0L)
                output.write(wavHeader(pcmDataBytes = 0L))
                val samples = ShortArray(bufferSize / 2)
                while (recording) {
                    val read = audioRecord.read(samples, 0, samples.size)
                    if (read <= 0) continue
                    amplitudeLevel = samples.maxAmplitudeLevel(read)
                    if (paused) continue
                    val bytes = samples.toLittleEndianPcm(read)
                    output.write(bytes)
                    pcmBytesWritten += bytes.size
                }
                output.seek(0L)
                output.write(wavHeader(pcmDataBytes = pcmBytesWritten))
            }
        }.onFailure {
            file.delete()
        }
    }
}

internal const val VOICE_CAPTURE_SAMPLE_RATE_HZ = 16_000
internal const val VOICE_CAPTURE_CHANNEL_COUNT = 1
internal const val VOICE_CAPTURE_BITS_PER_SAMPLE = 16
internal const val WAV_HEADER_BYTES = 44

internal fun wavHeader(
    pcmDataBytes: Long,
    sampleRateHz: Int = VOICE_CAPTURE_SAMPLE_RATE_HZ,
    channelCount: Int = VOICE_CAPTURE_CHANNEL_COUNT,
    bitsPerSample: Int = VOICE_CAPTURE_BITS_PER_SAMPLE,
): ByteArray {
    val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8
    val riffChunkSize = 36L + pcmDataBytes
    return ByteBuffer.allocate(WAV_HEADER_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray(Charsets.US_ASCII))
        .putInt(riffChunkSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        .put("WAVE".toByteArray(Charsets.US_ASCII))
        .put("fmt ".toByteArray(Charsets.US_ASCII))
        .putInt(16)
        .putShort(1)
        .putShort(channelCount.toShort())
        .putInt(sampleRateHz)
        .putInt(byteRate)
        .putShort(blockAlign.toShort())
        .putShort(bitsPerSample.toShort())
        .put("data".toByteArray(Charsets.US_ASCII))
        .putInt(pcmDataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        .array()
}

private fun ShortArray.maxAmplitudeLevel(readSamples: Int): Float {
    var maxAmplitude = 0
    for (index in 0 until readSamples) {
        val value = this[index].toInt()
        val amplitude = if (value == Short.MIN_VALUE.toInt()) Short.MAX_VALUE.toInt() else abs(value)
        if (amplitude > maxAmplitude) maxAmplitude = amplitude
    }
    return (maxAmplitude.toFloat() / Short.MAX_VALUE.toFloat()).coerceIn(0f, 1f)
}

private fun ShortArray.toLittleEndianPcm(readSamples: Int): ByteArray {
    val buffer = ByteBuffer.allocate(readSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (index in 0 until readSamples) {
        buffer.putShort(this[index])
    }
    return buffer.array()
}
