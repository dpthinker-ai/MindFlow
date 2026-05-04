package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class ReleaseCaptureFlowScriptContractTest {
    @Test
    fun releaseCaptureFlowScript_preservesDataAndCoversCaptureSurfaces() {
        val script = locateScript().readText()

        assertThat(script).contains("app/build/outputs/apk/release/app-release.apk")
        assertThat(script).contains("adb install -r")
        assertThat(script).contains("com.mindflow.app/.SplashActivity")
        assertThat(script).contains("gemma-4-E4B-it.litertlm")
        assertThat(script).contains("files/notes | wc -l")

        assertThat(script).contains("纯文本输入")
        assertThat(script).contains("完成记录")
        assertThat(script).contains("文本记录")
        assertThat(script).contains("重新生成标题")
        assertThat(script).contains("润色正文")
        assertThat(script).contains("插入今天")
        assertThat(script).contains("链接任务")
        assertThat(script).contains("导入项目")

        assertThat(script).contains("语音输入")
        assertThat(script).contains("原始内容信息")
        assertThat(script).contains("AI 洞察")
        assertThat(script).contains("完成解析")

        assertThat(script).contains("图片输入")
        assertThat(script).contains("图像理解结果")
        assertThat(script).contains("OCR 文本(可选)")
        assertThat(script).contains("继续解析")

        assertThat(script).doesNotContain("adb uninstall")
        assertThat(script).doesNotContain("pm clear")
        assertThat(script).doesNotContain("wipe-data")
        assertThat(script).doesNotContain("app-debug.apk")
    }

    private fun locateScript(): File {
        val candidates = listOf(
            File("scripts/verify_release_capture_flows.sh"),
            File("../scripts/verify_release_capture_flows.sh"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Could not locate release capture flow script from ${File(".").absolutePath}")
    }
}
