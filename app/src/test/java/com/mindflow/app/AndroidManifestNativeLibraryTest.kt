package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class AndroidManifestNativeLibraryTest {
    @Test
    fun manifest_declaresVendorLibrariesNeededForAcceleratedInference() {
        val manifest = locateManifest().readText()

        assertThat(manifest).contains("""android:name="libvndksupport.so"""")
        assertThat(manifest).contains("""android:name="libOpenCL.so"""")
        assertThat(manifest).contains("""android:name="libcdsprpc.so"""")
    }

    private fun locateManifest(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Could not locate AndroidManifest.xml from ${File(".").absolutePath}")
    }
}
