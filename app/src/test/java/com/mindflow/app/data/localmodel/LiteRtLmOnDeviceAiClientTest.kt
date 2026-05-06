package com.mindflow.app.data.localmodel

import com.google.ai.edge.litertlm.Backend
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Test

class LiteRtLmOnDeviceAiClientTest {
    @Test
    fun buildTextEngineConfig_doesNotUseDiskCacheByDefault() {
        val config = buildLiteRtLmTextEngineConfig(
            modelPath = "/models/gemma-4-E4B-it.litertlm",
            backend = Backend.CPU(),
            maxNumTokens = 2048,
        )

        assertThat(config.modelPath).isEqualTo("/models/gemma-4-E4B-it.litertlm")
        assertThat(config.maxNumTokens).isEqualTo(2048)
        assertThat(config.audioBackend).isNull()
        assertThat(config.cacheDir).isNull()
    }

    @Test
    fun buildTextEngineConfig_enablesCpuAudioBackendForGemmaAudioInput() {
        val config = buildLiteRtLmTextEngineConfig(
            modelPath = "/models/gemma-4-E4B-it.litertlm",
            backend = Backend.GPU(),
            audioEnabled = true,
            maxNumTokens = 2048,
        )

        assertThat(config.audioBackend).isInstanceOf(Backend.CPU::class.java)
    }

    @Test
    fun buildTextEngineConfig_enablesVisionBackendForImageInput() {
        val config = buildLiteRtLmTextEngineConfig(
            modelPath = "/models/gemma-4-E4B-it.litertlm",
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            maxNumTokens = 2048,
        )

        assertThat(config.visionBackend).isInstanceOf(Backend.GPU::class.java)
    }

    @Test
    fun textBackendCandidates_useOnlyCpuOnEmulator() {
        val candidates = liteRtLmTextBackendCandidates(
            deviceProfile = LocalInferenceDeviceProfile(
                fingerprint = "google/sdk_gphone64_arm64/emu64a:16/BAK9.250118.001/13251064:userdebug/dev-keys",
                model = "sdk_gphone64_arm64",
                manufacturer = "Google",
                brand = "google",
                device = "emu64a",
                product = "sdk_gphone64_arm64",
                hardware = "ranchu",
            )
        )

        assertThat(candidates.map { it.first }).containsExactly("cpu").inOrder()
        assertThat((candidates.single().second as Backend.CPU).numOfThreads).isEqualTo(1)
    }

    @Test
    fun textBackendCandidates_useHardwareAccelerationBeforeCpuFallbackOnPhysicalDevice() {
        val candidates = liteRtLmTextBackendCandidates(
            deviceProfile = LocalInferenceDeviceProfile(
                fingerprint = "HONOR/MEP-AN00/HNMEP:16/HONORMEP/6.0.0.1:user/release-keys",
                model = "MEP-AN00",
                manufacturer = "HONOR",
                brand = "HONOR",
                device = "HNMEP",
                product = "MEP-AN00",
                hardware = "qcom",
            )
        )

        assertThat(candidates.map { it.first }).containsExactly("gpu", "npu", "cpu").inOrder()
    }

    @Test
    fun imageBackendCandidates_useGalleryStyleVisionPathOnPhysicalDevice() {
        val candidates = liteRtLmImageBackendCandidates(
            deviceProfile = LocalInferenceDeviceProfile(
                fingerprint = "HONOR/MEP-AN00/HNMEP:16/HONORMEP/6.0.0.1:user/release-keys",
                model = "MEP-AN00",
                manufacturer = "HONOR",
                brand = "HONOR",
                device = "HNMEP",
                product = "MEP-AN00",
                hardware = "qcom",
            )
        )

        assertThat(candidates.map { it.name }).containsExactly("gpu", "cpu").inOrder()
        assertThat(candidates.map { it.visionBackend::class.java })
            .containsExactly(Backend.GPU::class.java, Backend.CPU::class.java)
            .inOrder()
    }

    @Test
    fun imageSampleSizeMatchesEdgeGalleryAskImageDecodePolicy() {
        assertThat(calculateLiteRtLmImageSampleSize(width = 4000, height = 3000)).isEqualTo(4)
        assertThat(calculateLiteRtLmImageSampleSize(width = 3000, height = 4000)).isEqualTo(4)
        assertThat(calculateLiteRtLmImageSampleSize(width = 1600, height = 900)).isEqualTo(2)
        assertThat(calculateLiteRtLmImageSampleSize(width = 1024, height = 768)).isEqualTo(1)
    }

    @Test
    fun runtimeMaxTokens_usesSmallerBudgetOnEmulator() {
        val emulatorProfile = LocalInferenceDeviceProfile(
            fingerprint = "google/sdk_gphone64_arm64/emu64a:16/BAK9.250118.001/13251064:userdebug/dev-keys",
            model = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "google",
            device = "emu64a",
            product = "sdk_gphone64_arm64",
            hardware = "ranchu",
        )
        val physicalProfile = LocalInferenceDeviceProfile(
            fingerprint = "HONOR/MEP-AN00/HNMEP:16/HONORMEP/6.0.0.1:user/release-keys",
            model = "MEP-AN00",
            manufacturer = "HONOR",
            brand = "HONOR",
            device = "HNMEP",
            product = "MEP-AN00",
            hardware = "qcom",
        )

        assertThat(liteRtLmRuntimeMaxTokens(emulatorProfile)).isEqualTo(LITERT_LM_EMULATOR_RUNTIME_MAX_TOKENS)
        assertThat(liteRtLmRuntimeMaxTokens(physicalProfile)).isEqualTo(LITERT_LM_DEFAULT_RUNTIME_MAX_TOKENS)
    }

    @Test
    fun runtimeSafety_rejectsLargeGemmaModelOnEmulatorBeforeNativeInitialization() {
        val modelFile = File.createTempFile("gemma", ".litertlm")
        modelFile.deleteOnExit()
        RandomAccessFile(modelFile, "rw").use { file ->
            file.setLength(LITERT_LM_EMULATOR_UNSAFE_MODEL_BYTES)
        }
        val emulatorProfile = LocalInferenceDeviceProfile(
            fingerprint = "google/sdk_gphone64_arm64/emu64a:16/BAK9.250118.001/13251064:userdebug/dev-keys",
            model = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "google",
            device = "emu64a",
            product = "sdk_gphone64_arm64",
            hardware = "ranchu",
        )

        val message = liteRtLmRuntimeSafetyFailureMessage(modelFile, emulatorProfile)

        assertThat(message).contains("模拟器 CPU 内存不足")
    }

    @Test
    fun deleteLegacyLiteRtLmDiskCache_removesNestedCacheFiles() {
        val cacheDir = Files.createTempDirectory("litert-lm-test").toFile()
        cacheDir.resolve("nested").mkdirs()
        cacheDir.resolve("nested/gemma.xnnpack_cache").writeText("legacy-cache")

        val deleted = deleteLegacyLiteRtLmDiskCache(cacheDir)

        assertThat(deleted).isTrue()
        assertThat(cacheDir.exists()).isFalse()
    }
}
